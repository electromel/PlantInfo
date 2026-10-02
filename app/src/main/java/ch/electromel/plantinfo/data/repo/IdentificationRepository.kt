package ch.electromel.plantinfo.data.repo

import android.content.Context
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.remote.ai.AiAnalysisInput
import ch.electromel.plantinfo.data.remote.ai.AiFailureReason
import ch.electromel.plantinfo.data.remote.ai.AiImage
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.ai.AiOutcome
import ch.electromel.plantinfo.data.remote.ai.summary
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetError
import ch.electromel.plantinfo.domain.ConfidenceEngine
import ch.electromel.plantinfo.domain.SafetyFlags
import ch.electromel.plantinfo.domain.model.FailureKind
import ch.electromel.plantinfo.domain.model.GpsLocation
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.util.AppLocales
import ch.electromel.plantinfo.util.StringProvider
import ch.electromel.plantinfo.util.ImageStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestration du pipeline hybride Pl@ntNet + IA (§2.3) et persistance du résultat.
 *
 * Décision :
 *  1. Pl@ntNet (si clé) → candidats taxonomiques.
 *  2. IA générative (repli entre fournisseurs) → validation/correction, diagnostic, enrichissement,
 *     identification des champignons.
 *  3. Fusion des scores (ConfidenceEngine) → score final /100.
 *  4. Enregistrement en historique avec le fournisseur IA effectif.
 *
 * Cas dégradés : aucune clé IA → résultat Pl@ntNet brut ; toutes les IA en échec → Pl@ntNet brut si
 * disponible, sinon échec typé (clé invalide / quota / réseau) avec mise en file éventuelle (Phase 3).
 */
@Singleton
class IdentificationRepository @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val plantNetClient: PlantNetClient,
    private val aiOrchestrator: AiOrchestrator,
    private val imageStorage: ImageStorage,
    private val dao: IdentificationDao,
    private val strings: StringProvider,
    @ApplicationContext private val context: Context,
) {
    suspend fun identifyAndSave(request: IdentificationRequest): IdentificationOutcome =
        when (val pipeline = runPipeline(request)) {
            is Pipeline.Ok -> {
                val id = persist(pipeline.result, request)
                IdentificationOutcome.Success(id, pipeline.result, pipeline.infoMessage)
            }
            is Pipeline.Ko -> pipeline.failure
        }

    /**
     * Relance le pipeline complet sur une fiche déjà enregistrée (ex. IA inaccessible lors de la
     * première tentative, ou clé IA ajoutée après coup) et met à jour la fiche en place.
     * Conserve id, date, photos, position, favori et notes ; une validation manuelle antérieure est
     * remplacée par la nouvelle analyse. Pas de mise en file hors-ligne : la relance est manuelle.
     */
    suspend fun reanalyzeAndUpdate(entity: IdentificationEntity): IdentificationOutcome =
        reanalyze(entity, defaultOrgans(entity.photoPaths.size))

    /**
     * Ajoute à la fiche la photo complémentaire proposée quand l'identification est incertaine,
     * puis relance l'analyse avec toutes les photos. La photo est enregistrée **avant** l'analyse :
     * si celle-ci échoue (réseau, quota), elle reste sur la fiche et une simple relance suffit.
     * La position de la fiche n'est pas touchée : c'est le même sujet, au même endroit.
     */
    suspend fun addPhotoAndReanalyze(
        entity: IdentificationEntity,
        photoPath: String,
        organ: PhotoOrgan,
    ): IdentificationOutcome {
        // Repartir de la ligne telle qu'elle est en base, pas de l'instantané de l'écran : une note
        // ou un favori modifiés entre-temps ne doivent pas être écrasés par cette mise à jour.
        val latest = dao.getById(entity.id) ?: entity
        val withPhoto = latest.copy(photoPaths = latest.photoPaths + photoPath)
        dao.update(withPhoto)
        // L'organe de la nouvelle photo est connu (c'est celui qui a été demandé) : Pl@ntNet en tient
        // compte. Les organes des photos précédentes ne sont pas persistés, d'où l'heuristique.
        return reanalyze(withPhoto, defaultOrgans(entity.photoPaths.size) + organ)
    }

    // Les organes choisis à la capture ne sont pas persistés : on reprend l'heuristique de la
    // capture (1re photo = port général, suivantes = feuille).
    private fun defaultOrgans(count: Int): List<PhotoOrgan> =
        List(count) { i -> if (i == 0) PhotoOrgan.HABIT else PhotoOrgan.LEAF }

    private suspend fun reanalyze(entity: IdentificationEntity, organs: List<PhotoOrgan>): IdentificationOutcome {
        val gps = if (entity.latitude != null && entity.longitude != null) {
            GpsLocation(entity.latitude, entity.longitude, entity.altitude, entity.gpsAccuracy)
        } else {
            null
        }
        val request = IdentificationRequest(
            photoPaths = entity.photoPaths,
            organs = organs,
            gps = gps,
        )
        return when (val pipeline = runPipeline(request)) {
            is Pipeline.Ok -> {
                val flagged = SafetyFlags.reinforce(pipeline.result)
                // L'analyse dure de quelques secondes à plus d'une minute : pendant ce temps
                // l'utilisateur a pu modifier la note ou le favori, ou supprimer la fiche. On relit
                // donc la ligne **après** l'analyse plutôt que de réécrire l'instantané de départ.
                val current = dao.getById(entity.id)
                if (current != null) {
                    val updated = flagged
                        .toEntity(gps, entity.photoPaths, flagged.aiProvider, entity.dateTime)
                        .copy(id = entity.id, isFavorite = current.isFavorite, notes = current.notes)
                    dao.update(updated)
                }
                IdentificationOutcome.Success(entity.id, flagged, pipeline.infoMessage)
            }
            is Pipeline.Ko -> {
                val f = pipeline.failure
                if (f.queuedForRetry) {
                    f.copy(
                        message = strings.get(R.string.id_reanalyze_offline),
                        queuedForRetry = false,
                    )
                } else {
                    f
                }
            }
        }
    }

    /** Issue interne du pipeline, avant persistance (insertion ou mise à jour). */
    private sealed interface Pipeline {
        data class Ok(val result: IdentificationResult, val infoMessage: String?) : Pipeline
        data class Ko(val failure: IdentificationOutcome.Failure) : Pipeline
    }

    private suspend fun runPipeline(request: IdentificationRequest): Pipeline {
        // Lecture disque hors du thread principal : l'appelant est le plus souvent un ViewModel.
        val images = withContext(Dispatchers.IO) {
            request.photoPaths.map { AiImage(imageStorage.readBytes(it), "image/jpeg") }
        }
        // Langue de l'application au moment de l'identification : celle des noms Pl@ntNet comme de
        // la fiche rédigée par l'IA.
        val language = AppLocales.current(context)

        // --- Étape 1 : Pl@ntNet ---
        val plantNetKey = keyStore.getKey(ApiProvider.PLANTNET)
        val hasAnyAi = keyStore.availableAiProvidersInOrder().isNotEmpty()
        if (plantNetKey == null && !hasAnyAi) {
            return Pipeline.Ko(
                IdentificationOutcome.Failure(
                    FailureKind.NO_KEYS,
                    strings.get(R.string.id_no_keys),
                ),
            )
        }
        val plantNetResult = if (plantNetKey != null) {
            plantNetClient.identify(images, request.organs.map { it.plantnetValue }, plantNetKey, language.tag)
        } else {
            null
        }
        val candidates: List<SpeciesCandidate> = plantNetResult?.candidates ?: emptyList()

        // --- Étape 2 : IA générative (avec repli) ---
        val aiOutcome = aiOrchestrator.analyze(
            AiAnalysisInput(
                images = images,
                plantNetCandidates = candidates,
                gps = request.gps,
                language = language,
            ),
        )

        return when (aiOutcome) {
            is AiOutcome.Success -> {
                val result = ConfidenceEngine.combineWithAi(aiOutcome.analysis, candidates)
                    .copy(aiProvider = aiOutcome.provider)
                Pipeline.Ok(result, infoMessage = null)
            }

            AiOutcome.NoProvidersConfigured -> {
                if (candidates.isNotEmpty()) {
                    Pipeline.Ok(
                        ConfidenceEngine.plantNetOnly(candidates),
                        infoMessage = strings.get(R.string.id_no_ai_key),
                    )
                } else {
                    Pipeline.Ko(plantNetOnlyFailure(plantNetResult?.error))
                }
            }

            is AiOutcome.AllFailed -> {
                if (candidates.isNotEmpty()) {
                    Pipeline.Ok(
                        ConfidenceEngine.plantNetOnly(candidates),
                        infoMessage = strings.get(
                            R.string.id_ai_unavailable,
                            aiOutcome.failures.summary(strings),
                        ),
                    )
                } else {
                    Pipeline.Ko(aiFailure(aiOutcome))
                }
            }
        }
    }

    private suspend fun persist(result: IdentificationResult, request: IdentificationRequest): Long {
        val flagged = SafetyFlags.reinforce(result)
        val entity = flagged.toEntity(
            gps = request.gps,
            photoPaths = request.photoPaths,
            provider = flagged.aiProvider,
            dateTime = System.currentTimeMillis(),
        )
        return dao.insert(entity)
    }

    private fun plantNetOnlyFailure(error: PlantNetError?): IdentificationOutcome.Failure = when (error) {
        PlantNetError.INVALID_KEY -> IdentificationOutcome.Failure(
            FailureKind.INVALID_KEY, strings.get(R.string.id_plantnet_invalid_key),
        )
        PlantNetError.QUOTA -> IdentificationOutcome.Failure(
            FailureKind.QUOTA, strings.get(R.string.id_plantnet_quota),
        )
        PlantNetError.NETWORK -> IdentificationOutcome.Failure(
            FailureKind.NETWORK, strings.get(R.string.id_offline_retry),
            queuedForRetry = true,
        )
        PlantNetError.SERVER -> IdentificationOutcome.Failure(
            FailureKind.SERVER, strings.get(R.string.id_plantnet_server),
        )
        else -> IdentificationOutcome.Failure(
            FailureKind.NO_RESULT,
            strings.get(R.string.id_no_result),
        )
    }

    private fun aiFailure(outcome: AiOutcome.AllFailed): IdentificationOutcome.Failure {
        val detail = outcome.failures.summary(strings)
        return when (outcome.lastReason) {
            AiFailureReason.INVALID_KEY -> IdentificationOutcome.Failure(
                FailureKind.INVALID_KEY, strings.get(R.string.id_ai_invalid_key, detail),
            )
            AiFailureReason.QUOTA, AiFailureReason.BILLING -> IdentificationOutcome.Failure(
                FailureKind.QUOTA,
                strings.get(R.string.id_ai_quota, detail),
            )
            AiFailureReason.NETWORK -> IdentificationOutcome.Failure(
                FailureKind.NETWORK,
                strings.get(R.string.id_offline_retry_network),
                queuedForRetry = true,
            )
            else -> IdentificationOutcome.Failure(
                if (outcome.allNetwork) FailureKind.NETWORK else FailureKind.SERVER,
                strings.get(R.string.id_ai_failed, detail),
                queuedForRetry = outcome.allNetwork,
            )
        }
    }

}

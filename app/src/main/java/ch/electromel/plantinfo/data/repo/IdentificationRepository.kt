package ch.electromel.plantinfo.data.repo

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
import ch.electromel.plantinfo.domain.ProtectedSpeciesChecker
import ch.electromel.plantinfo.domain.model.FailureKind
import ch.electromel.plantinfo.domain.model.GpsLocation
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.iucnStatus
import ch.electromel.plantinfo.util.ImageStorage
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
    suspend fun reanalyzeAndUpdate(entity: IdentificationEntity): IdentificationOutcome {
        val gps = if (entity.latitude != null && entity.longitude != null) {
            GpsLocation(entity.latitude, entity.longitude, entity.altitude, entity.gpsAccuracy)
        } else {
            null
        }
        val request = IdentificationRequest(
            photoPaths = entity.photoPaths,
            // Les organes choisis à la capture ne sont pas persistés : on reprend l'heuristique de
            // la capture (1re photo = port général, suivantes = feuille).
            organs = entity.photoPaths.mapIndexed { i, _ ->
                if (i == 0) PhotoOrgan.HABIT else PhotoOrgan.LEAF
            },
            gps = gps,
        )
        return when (val pipeline = runPipeline(request)) {
            is Pipeline.Ok -> {
                val flagged = withProtectedFlag(pipeline.result)
                val updated = flagged
                    .toEntity(gps, entity.photoPaths, flagged.aiProvider, entity.dateTime)
                    .copy(id = entity.id, isFavorite = entity.isFavorite, notes = entity.notes)
                dao.update(updated)
                IdentificationOutcome.Success(entity.id, flagged, pipeline.infoMessage)
            }
            is Pipeline.Ko -> {
                val f = pipeline.failure
                if (f.queuedForRetry) {
                    f.copy(
                        message = "Pas de connexion : impossible de relancer l'analyse. Réessayez plus tard.",
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
        val images = request.photoPaths.map {
            AiImage(imageStorage.readBytes(it), "image/jpeg")
        }

        // --- Étape 1 : Pl@ntNet ---
        val plantNetKey = keyStore.getKey(ApiProvider.PLANTNET)
        val hasAnyAi = keyStore.availableAiProvidersInOrder().isNotEmpty()
        if (plantNetKey == null && !hasAnyAi) {
            return Pipeline.Ko(
                IdentificationOutcome.Failure(
                    FailureKind.NO_KEYS,
                    "Aucune clé API configurée. Renseignez au minimum la clé Pl@ntNet dans les paramètres.",
                ),
            )
        }
        val plantNetResult = if (plantNetKey != null) {
            plantNetClient.identify(images, request.organs.map { it.plantnetValue }, plantNetKey)
        } else {
            null
        }
        val candidates: List<SpeciesCandidate> = plantNetResult?.candidates ?: emptyList()

        // --- Étape 2 : IA générative (avec repli) ---
        val aiOutcome = aiOrchestrator.analyze(
            AiAnalysisInput(images = images, plantNetCandidates = candidates, gps = request.gps),
        )

        return when (aiOutcome) {
            is AiOutcome.Success -> {
                val result = ConfidenceEngine.combineWithAi(aiOutcome.analysis, candidates)
                    .copy(aiProvider = aiOutcome.provider)
                Pipeline.Ok(result, infoMessage = null)
            }

            AiOutcome.NoProvidersConfigured -> {
                if (candidates.isNotEmpty()) {
                    Pipeline.Ok(ConfidenceEngine.plantNetOnly(candidates), infoMessage = NO_AI_MESSAGE)
                } else {
                    Pipeline.Ko(plantNetOnlyFailure(plantNetResult?.error))
                }
            }

            is AiOutcome.AllFailed -> {
                if (candidates.isNotEmpty()) {
                    Pipeline.Ok(
                        ConfidenceEngine.plantNetOnly(candidates),
                        infoMessage = "Analyse IA indisponible (${aiOutcome.failures.summary()}). " +
                            "Résultat Pl@ntNet brut : sans description, diagnostic de santé ni score IA.",
                    )
                } else {
                    Pipeline.Ko(aiFailure(aiOutcome))
                }
            }
        }
    }

    /**
     * Croisement avec la liste de référence Suisse **et** le statut UICN rapporté par Pl@ntNet :
     * renforce le drapeau « espèce protégée » renvoyé par l'IA (§2.4) sans jamais le désactiver.
     *
     * Une espèce menacée au sens UICN n'est pas juridiquement protégée pour autant (le statut est
     * mondial, la protection est cantonale/fédérale) ; on l'assimile ici volontairement, l'objectif
     * du drapeau étant de déconseiller la cueillette. Le statut UICN reste par ailleurs affiché tel
     * quel, pour ne pas travestir les deux notions.
     */
    private fun withProtectedFlag(result: IdentificationResult): IdentificationResult {
        if (result.isProtected) return result
        val listed = ProtectedSpeciesChecker.isProtected(result.scientificName)
        val threatened = result.iucnStatus?.threatened == true
        return if (listed || threatened) result.copy(isProtected = true) else result
    }

    private suspend fun persist(result: IdentificationResult, request: IdentificationRequest): Long {
        val flagged = withProtectedFlag(result)
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
            FailureKind.INVALID_KEY, "Clé Pl@ntNet invalide. Vérifiez-la dans les paramètres.",
        )
        PlantNetError.QUOTA -> IdentificationOutcome.Failure(
            FailureKind.QUOTA, "Quota Pl@ntNet atteint. Réessayez plus tard.",
        )
        PlantNetError.NETWORK -> IdentificationOutcome.Failure(
            FailureKind.NETWORK, "Pas de connexion. L'identification sera réessayée automatiquement.",
            queuedForRetry = true,
        )
        PlantNetError.SERVER -> IdentificationOutcome.Failure(
            FailureKind.SERVER, "Service Pl@ntNet momentanément indisponible.",
        )
        else -> IdentificationOutcome.Failure(
            FailureKind.NO_RESULT,
            "Aucune espèce n'a pu être identifiée. Ajoutez une photo plus nette ou complémentaire.",
        )
    }

    private fun aiFailure(outcome: AiOutcome.AllFailed): IdentificationOutcome.Failure {
        val detail = outcome.failures.summary()
        return when (outcome.lastReason) {
            AiFailureReason.INVALID_KEY -> IdentificationOutcome.Failure(
                FailureKind.INVALID_KEY, "Clé(s) IA invalide(s) ($detail). Vérifiez-les dans les paramètres.",
            )
            AiFailureReason.QUOTA, AiFailureReason.BILLING -> IdentificationOutcome.Failure(
                FailureKind.QUOTA,
                "Analyse IA impossible : $detail. Vérifiez le crédit/quota de vos comptes fournisseurs.",
            )
            AiFailureReason.NETWORK -> IdentificationOutcome.Failure(
                FailureKind.NETWORK,
                "Pas de connexion. L'identification sera réessayée automatiquement dès le retour du réseau.",
                queuedForRetry = true,
            )
            else -> IdentificationOutcome.Failure(
                if (outcome.allNetwork) FailureKind.NETWORK else FailureKind.SERVER,
                "L'analyse a échoué ($detail).",
                queuedForRetry = outcome.allNetwork,
            )
        }
    }

    private companion object {
        const val NO_AI_MESSAGE =
            "Aucune clé IA configurée : seul le résultat brut de Pl@ntNet est affiché (sans diagnostic " +
                "de santé ni informations complémentaires). Ajoutez une clé dans les paramètres."
    }
}

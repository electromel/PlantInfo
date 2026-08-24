package ch.electromel.plantinfo.data.repo

import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.domain.FungusChecker
import ch.electromel.plantinfo.domain.ProtectedSpeciesChecker
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.isToxic
import ch.electromel.plantinfo.util.ImageStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Gestion de l'historique local : lecture filtrée, suppression, favoris, notes (§2.5). */
@Singleton
class HistoryRepository @Inject constructor(
    private val dao: IdentificationDao,
    private val imageStorage: ImageStorage,
) {
    fun observeFiltered(
        query: String,
        favoritesOnly: Boolean,
        withLocationOnly: Boolean,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<IdentificationEntity>> =
        dao.observeFiltered(query, favoritesOnly, withLocationOnly, fromMillis, toMillis)

    fun observeById(id: Long): Flow<IdentificationEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): IdentificationEntity? = dao.getById(id)

    /** Supprime une entrée et ses photos associées du stockage interne. */
    suspend fun delete(entity: IdentificationEntity) {
        entity.photoPaths.forEach { imageStorage.delete(it) }
        dao.delete(entity)
    }

    /** Supprime tout l'historique (avec confirmation côté UI) et les photos correspondantes. */
    suspend fun deleteAll(entities: List<IdentificationEntity>) {
        entities.forEach { e -> e.photoPaths.forEach { imageStorage.delete(it) } }
        dao.deleteAll()
    }

    suspend fun setFavorite(entity: IdentificationEntity, favorite: Boolean) {
        dao.update(entity.copy(isFavorite = favorite))
    }

    suspend fun setNotes(entity: IdentificationEntity, notes: String?) {
        dao.update(entity.copy(notes = notes))
    }

    /**
     * Valide manuellement une hypothèse alternative : la promeut comme identification principale.
     * L'ancienne espèce principale rejoint la liste des alternatives. Les scores Pl@ntNet/IA (qui
     * portaient sur l'ancienne espèce) sont effacés et le score final devient celui de l'hypothèse
     * choisie ; la carte se met à jour automatiquement sur la nouvelle espèce.
     *
     * **Tout ce qui décrit l'espèce et non la photo est effacé** : comestibilité, habitat,
     * description, dimensions à maturité, calendrier, usages, symbolique, diagnostic de santé. Ces
     * informations ont été produites par l'IA pour l'espèce précédente ; les conserver sous un
     * nouveau nom fabriquerait une fiche fausse — au pire mortelle (« Comestible » de l'ail des ours
     * sous le nom du colchique). La fiche ne garde donc que ce qui reste vrai : photo, lieu, carte,
     * nom choisi, hypothèses.
     *
     * Les deux drapeaux de sécurité ne sont jamais simplement remis à false, ce qui *supprimerait*
     * un avertissement : `isProtected` est recalculé sur la nouvelle espèce ([ProtectedSpeciesChecker]),
     * `isFungus` est conservé et peut être levé par [FungusChecker] (bascule plante → champignon).
     * `toxic` est recalculé de la même façon prudente à partir de [SpeciesCandidate.isToxic].
     */
    suspend fun selectSpecies(entity: IdentificationEntity, chosen: SpeciesCandidate) {
        val previousMain = SpeciesCandidate(
            scientificName = entity.scientificName,
            commonName = entity.commonName,
            score = entity.scoreFinal,
            gbifKey = entity.gbifKey,
            iucnCategory = entity.iucnCategory,
            // La toxicité connue de l'espèce rétrogradée la suit dans les alternatives : c'est ce qui
            // permet d'avertir d'une confusion toxique après une validation manuelle.
            toxic = entity.toxic,
        )
        val currentAlternatives = entity.toResult().alternatives
        val newAlternatives = (listOf(previousMain) + currentAlternatives)
            .distinctBy { it.scientificName }
            .filter { it.scientificName != chosen.scientificName }
            .take(3)

        // Toxicité de la nouvelle espèce : le drapeau de l'hypothèse (IA) ou la liste locale
        // suffisent. false n'est jamais écrit — « non toxique » serait une affirmation que personne
        // n'a faite sur cette espèce ; null = inconnu, et la section reste muette sur le verdict.
        val toxic = if (chosen.isToxic) true else null

        dao.update(
            entity.copy(
                commonName = chosen.commonName ?: chosen.scientificName,
                scientificName = chosen.scientificName,
                scoreFinal = chosen.score,
                scorePlantNet = null,
                scoreAi = null,
                // Métadonnées taxonomiques solidaires de l'espèce : elles suivent celle qui est
                // promue, sinon la carte continuerait d'interroger le taxon GBIF de l'ancienne.
                // null est correct ici : la carte retombe alors sur la résolution par nom.
                gbifKey = chosen.gbifKey,
                iucnCategory = chosen.iucnCategory,

                // --- Drapeaux de sécurité : recalculés, jamais désactivés ---
                isProtected = ProtectedSpeciesChecker.isProtected(chosen.scientificName),
                isFungus = entity.isFungus || FungusChecker.isFungus(chosen.scientificName),

                // --- Informations propres à l'espèce : périmées dès que l'espèce change ---
                edible = null,
                toxic = toxic,
                edibilityNote = edibilityNoteAfterSelection(toxic == true),
                habitat = null,
                description = null,
                matureHeight = null,
                matureDiameter = null,
                timeToMaturity = null,
                careCalendarJson = null,
                usesJson = null,
                symbolism = null,
                // Le diagnostic porte bien sur le sujet photographié, mais l'IA l'a formulé en
                // supposant l'ancienne espèce (« feuillage normal pour un… », recommandations
                // d'entretien propres à elle). Rien ne permet d'en isoler la part purement visuelle :
                // il part avec le reste.
                healthStatus = null,
                isHealthy = null,
                recommendations = emptyList(),

                sourcesDisagree = false,
                userConfirmed = true,
                alternativesJson = encodeAlternatives(newAlternatives),
            ),
        )
    }
}

/**
 * Note affichée dans la section « Comestibilité » après une validation manuelle. Elle remplace un
 * verdict devenu faux : sans elle, la section disparaîtrait sans que l'utilisateur comprenne
 * pourquoi le « Comestible » qu'il venait de lire s'est volatilisé.
 */
private fun edibilityNoteAfterSelection(toxic: Boolean): String = if (toxic) {
    "Espèce signalée comme toxique par la liste de référence de l'application. Les informations de " +
        "comestibilité affichées jusqu'ici décrivaient l'espèce précédemment identifiée : elles ont " +
        "été effacées."
} else {
    "Comestibilité non évaluée pour cette espèce : vous l'avez validée manuellement, sans nouvelle " +
        "analyse. Les informations affichées jusqu'ici décrivaient l'espèce précédemment identifiée."
}

package com.plantinfo.data.repo

import com.plantinfo.data.db.IdentificationDao
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.domain.model.SpeciesCandidate
import com.plantinfo.util.ImageStorage
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
     */
    suspend fun selectSpecies(entity: IdentificationEntity, chosen: SpeciesCandidate) {
        val previousMain = SpeciesCandidate(
            scientificName = entity.scientificName,
            commonName = entity.commonName,
            score = entity.scoreFinal,
        )
        val currentAlternatives = entity.toResult().alternatives
        val newAlternatives = (listOf(previousMain) + currentAlternatives)
            .distinctBy { it.scientificName }
            .filter { it.scientificName != chosen.scientificName }
            .take(3)

        dao.update(
            entity.copy(
                commonName = chosen.commonName ?: chosen.scientificName,
                scientificName = chosen.scientificName,
                scoreFinal = chosen.score,
                scorePlantNet = null,
                scoreAi = null,
                sourcesDisagree = false,
                userConfirmed = true,
                alternativesJson = encodeAlternatives(newAlternatives),
            ),
        )
    }
}

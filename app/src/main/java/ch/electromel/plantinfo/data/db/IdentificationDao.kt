package ch.electromel.plantinfo.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Accès aux entrées d'historique (§2.5). */
@Dao
interface IdentificationDao {

    @Insert
    suspend fun insert(entity: IdentificationEntity): Long

    @Update
    suspend fun update(entity: IdentificationEntity)

    @Delete
    suspend fun delete(entity: IdentificationEntity)

    @Query("DELETE FROM identifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM identifications")
    suspend fun deleteAll()

    @Query("SELECT * FROM identifications WHERE id = :id")
    suspend fun getById(id: Long): IdentificationEntity?

    @Query("SELECT * FROM identifications WHERE id = :id")
    fun observeById(id: Long): Flow<IdentificationEntity?>

    /**
     * Liste filtrable (§2.5) : recherche texte sur noms, filtre favoris optionnel, tri antéchronologique.
     * Les bornes de date à 0 / Long.MAX désactivent le filtre temporel.
     */
    @Query(
        """
        SELECT * FROM identifications
        WHERE (:query = '' OR commonName LIKE '%' || :query || '%' OR scientificName LIKE '%' || :query || '%')
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:withLocationOnly = 0 OR latitude IS NOT NULL)
          AND dateTime BETWEEN :fromMillis AND :toMillis
        ORDER BY dateTime DESC
        """
    )
    fun observeFiltered(
        query: String,
        favoritesOnly: Boolean,
        withLocationOnly: Boolean,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<IdentificationEntity>>
}

package com.plantinfo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeciesRangeDao {

    @Query("SELECT * FROM species_range_cache WHERE scientificName = :scientificName")
    suspend fun get(scientificName: String): SpeciesRangeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpeciesRangeCacheEntity)
}

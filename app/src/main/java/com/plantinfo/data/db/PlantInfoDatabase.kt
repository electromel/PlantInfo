package com.plantinfo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de données locale (Room/SQLite).
 * - v1 : historique des identifications.
 * - v2 : ajout du cache de répartition GBIF (par espèce).
 * - v3 : validation manuelle d'une espèce (colonne userConfirmed).
 * - v4 : comestibilité / toxicité (colonnes edible, toxic, edibilityNote).
 */
@Database(
    entities = [IdentificationEntity::class, SpeciesRangeCacheEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PlantInfoDatabase : RoomDatabase() {
    abstract fun identificationDao(): IdentificationDao
    abstract fun speciesRangeDao(): SpeciesRangeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS species_range_cache (
                        scientificName TEXT NOT NULL PRIMARY KEY,
                        pointsJson TEXT NOT NULL,
                        hasData INTEGER NOT NULL,
                        fetchedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE identifications ADD COLUMN userConfirmed INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        // Colonnes nullables (INTEGER pour les Boolean?, TEXT pour la note) : pas de valeur par défaut.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE identifications ADD COLUMN edible INTEGER")
                db.execSQL("ALTER TABLE identifications ADD COLUMN toxic INTEGER")
                db.execSQL("ALTER TABLE identifications ADD COLUMN edibilityNote TEXT")
            }
        }
    }
}

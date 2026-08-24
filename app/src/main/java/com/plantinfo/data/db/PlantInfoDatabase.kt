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
 * - v5 : dimensions à maturité (matureHeight, matureDiameter, timeToMaturity) et métadonnées
 *        taxonomiques Pl@ntNet (gbifKey, iucnCategory).
 * - v6 : calendrier de plantation/entretien (careCalendarJson), usages (usesJson) et signification
 *        symbolique (symbolism).
 * - v7 : consommation de jetons de l'appel IA (usageModel, usageInputTokens, usageOutputTokens),
 *        pour afficher le nombre de jetons et le coût estimé de chaque identification.
 */
@Database(
    entities = [IdentificationEntity::class, SpeciesRangeCacheEntity::class],
    version = 7,
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

        // Toutes nullables : les fiches existantes restent lisibles, sans valeur par défaut à inventer.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE identifications ADD COLUMN matureHeight TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN matureDiameter TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN timeToMaturity TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN gbifKey INTEGER")
                db.execSQL("ALTER TABLE identifications ADD COLUMN iucnCategory TEXT")
            }
        }

        // Colonnes TEXT nullables : les deux premières portent une liste sérialisée, null valant
        // « aucune information » pour les fiches identifiées avant la v6.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE identifications ADD COLUMN careCalendarJson TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN usesJson TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN symbolism TEXT")
            }
        }

        // Nullables sans valeur par défaut : une fiche d'avant la v7 n'a pas « zéro jeton », elle
        // n'a pas de mesure du tout — et 0 s'afficherait comme une analyse gratuite.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE identifications ADD COLUMN usageModel TEXT")
                db.execSQL("ALTER TABLE identifications ADD COLUMN usageInputTokens INTEGER")
                db.execSQL("ALTER TABLE identifications ADD COLUMN usageOutputTokens INTEGER")
            }
        }
    }
}

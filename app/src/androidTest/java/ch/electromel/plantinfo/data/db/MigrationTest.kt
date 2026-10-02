package ch.electromel.plantinfo.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations Room, rejouées sur un appareil avec le vrai SQLite d'Android.
 *
 * CLAUDE.md désigne la migration comme « le point le plus facile à oublier » : sans elle l'app crashe
 * au démarrage chez un utilisateur qui met à jour. Ces tests rendent l'oubli visible avant la
 * publication. [MigrationTestHelper] crée la base à l'ancienne version depuis son schéma exporté
 * (`app/schemas`), applique [PlantInfoDatabase.MIGRATIONS], puis **valide le résultat contre le
 * schéma de la version courante** : une colonne manquante, en trop ou de mauvais type échoue.
 *
 * Les schémas de départ 1 à 8 sont reconstruits par `tools/derive_room_schemas.py` (ils n'avaient
 * jamais été exportés) ; celui de la version courante est généré par Room à chaque construction.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PlantInfoDatabase::class.java,
    )

    /**
     * Version courante : le plus grand schéma exporté. Lue plutôt que figée, pour que l'ajout d'une
     * version n'oblige pas à penser à modifier ce test — et que le test échoue, lui, si la liste
     * des migrations ne suit pas.
     */
    /** Bases créées par ces tests : supprimées à la fin, [MigrationTestHelper] ne le fait pas. */
    private val created = mutableListOf<String>()

    private fun database(name: String, version: Int): SupportSQLiteDatabase {
        created += name
        return helper.createDatabase(name, version)
    }

    @After
    fun deleteCreatedDatabases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        created.forEach { context.deleteDatabase(it) }
    }

    private val latest: Int = InstrumentationRegistry.getInstrumentation().context.assets
        .list(SCHEMA_ASSET_DIR).orEmpty()
        .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
        .max()

    @Test
    fun chaque_version_se_migre_vers_la_derniere() {
        for (from in 1 until latest) {
            val name = "migration-from-$from"
            database(name, from).close()
            helper.runMigrationsAndValidate(name, latest, true, *PlantInfoDatabase.MIGRATIONS)
        }
    }

    @Test
    fun une_fiche_de_la_version_1_survit_a_toute_la_chaine() {
        val name = "migration-data"
        database(name, 1).apply {
            insertRow("identifications")
            execSQL("UPDATE identifications SET commonName = 'Ail des ours', scientificName = 'Allium ursinum'")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, latest, true, *PlantInfoDatabase.MIGRATIONS)

        migrated.query("SELECT commonName, scientificName FROM identifications").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Ail des ours", cursor.getString(0))
            assertEquals("Allium ursinum", cursor.getString(1))
        }
    }

    @Test
    fun la_liste_des_migrations_est_continue_et_va_jusqu_a_la_version_courante() {
        val steps = PlantInfoDatabase.MIGRATIONS.sortedBy { it.startVersion }
        steps.forEachIndexed { index, migration ->
            assertEquals("la migration ${migration.startVersion}→${migration.endVersion} saute une version",
                index + 1, migration.startVersion)
            assertEquals(migration.startVersion + 1, migration.endVersion)
        }
        assertEquals(latest, steps.last().endVersion)
    }

    private companion object {
        const val SCHEMA_ASSET_DIR = "ch.electromel.plantinfo.data.db.PlantInfoDatabase"
    }

    /**
     * Insère une ligne en renseignant toutes les colonnes obligatoires sans valeur par défaut, quelle
     * que soit la version : on lit la définition de la table plutôt que d'en figer la liste ici.
     */
    private fun SupportSQLiteDatabase.insertRow(table: String) {
        val columns = mutableListOf<String>()
        val values = mutableListOf<String>()
        query("PRAGMA table_info($table)").use { info ->
            while (info.moveToNext()) {
                val column = info.getString(info.getColumnIndexOrThrow("name"))
                val type = info.getString(info.getColumnIndexOrThrow("type")).uppercase()
                val notNull = info.getInt(info.getColumnIndexOrThrow("notnull")) == 1
                val hasDefault = !info.isNull(info.getColumnIndexOrThrow("dflt_value"))
                val isPrimaryKey = info.getInt(info.getColumnIndexOrThrow("pk")) > 0
                if (!notNull || hasDefault || isPrimaryKey) continue
                columns += column
                values += if (type.contains("INT") || type.contains("REAL")) "0" else "''"
            }
        }
        execSQL("INSERT INTO $table (${columns.joinToString()}) VALUES (${values.joinToString()})")
    }
}

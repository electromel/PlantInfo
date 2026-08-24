package com.plantinfo.data.repo

import com.plantinfo.data.db.IdentificationDao
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.domain.model.EdibilityVerdict
import com.plantinfo.domain.model.SpeciesCandidate
import com.plantinfo.domain.model.edibilityVerdict
import com.plantinfo.domain.model.toxicConfusionWarningText
import com.plantinfo.util.ImageStorage
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation manuelle d'une hypothèse (`selectSpecies`).
 *
 * L'enjeu de ces tests n'est pas le renommage — c'est que **rien de ce qui décrivait l'ancienne
 * espèce ne survive sous le nouveau nom**. Le scénario de référence est mortel : « Ail des ours »
 * corrigé en « Colchique d'automne » ne doit en aucun cas conserver la mention « Comestible ».
 */
class HistoryRepositoryTest {

    private val dao = mockk<IdentificationDao>()
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val repository = HistoryRepository(dao, imageStorage)

    /** Fiche « riche » : tous les champs d'espèce renseignés par l'IA. */
    private fun entity(
        commonName: String = "Ail des ours",
        scientificName: String = "Allium ursinum",
        isFungus: Boolean = false,
        isProtected: Boolean = false,
        edible: Boolean? = true,
        toxic: Boolean? = false,
        alternatives: List<SpeciesCandidate> = emptyList(),
    ) = IdentificationEntity(
        id = 7,
        dateTime = 1_700_000_000_000,
        photoPaths = listOf("/data/photo1.jpg"),
        latitude = 46.2,
        longitude = 6.15,
        altitude = 400.0,
        gpsAccuracy = 12f,
        commonName = commonName,
        scientificName = scientificName,
        scorePlantNet = 62,
        scoreAi = 71,
        scoreFinal = 68,
        aiProvider = "CLAUDE",
        isProtected = isProtected,
        isFungus = isFungus,
        healthStatus = "Feuillage sain",
        isHealthy = true,
        recommendations = listOf("Récolter avant la floraison"),
        habitat = "Sous-bois frais et humides",
        description = "Plante bulbeuse à odeur d'ail marquée.",
        matureHeight = "20–50 cm",
        matureDiameter = "10–20 cm",
        timeToMaturity = "2–3 ans",
        edible = edible,
        toxic = toxic,
        edibilityNote = "Feuilles consommées crues ou cuites.",
        careCalendarJson = """[{"label":"Récolte","period":"Mars à avril"}]""",
        usesJson = """[{"domain":"FOOD","detail":"Condiment"}]""",
        symbolism = "Symbole du renouveau printanier.",
        gbifKey = 2_857_697,
        iucnCategory = "LC",
        usageModel = "claude-sonnet-5",
        usageInputTokens = 1_200,
        usageOutputTokens = 300,
        alternativesJson = encodeAlternatives(alternatives),
        sourcesDisagree = true,
        isFavorite = true,
        notes = "Cueilli au bord du ruisseau",
        userConfirmed = false,
    )

    /** Exécute selectSpecies et rend l'entité réellement passée au DAO. */
    private suspend fun select(
        entity: IdentificationEntity,
        chosen: SpeciesCandidate,
    ): IdentificationEntity {
        val updated = slot<IdentificationEntity>()
        coEvery { dao.update(capture(updated)) } returns Unit
        repository.selectSpecies(entity, chosen)
        return updated.captured
    }

    private val colchique = SpeciesCandidate("Colchicum autumnale", "Colchique d'automne", 34)

    @Test
    fun `le scenario mortel - la comestibilite de l'ancienne espece ne survit pas`() = runTest {
        val updated = select(entity(), colchique)

        assertEquals("Colchique d'automne", updated.commonName)
        assertEquals("Colchicum autumnale", updated.scientificName)
        // Le point vital : plus aucun « Comestible » hérité de l'ail des ours.
        assertNull(updated.edible)
        assertEquals(EdibilityVerdict.TOXIC, updated.toResult().edibilityVerdict)
        assertTrue(updated.toxic == true)
    }

    @Test
    fun `tous les champs decrivant l'espece sont effaces`() = runTest {
        val updated = select(entity(), SpeciesCandidate("Bellis perennis", "Pâquerette", 40))

        assertNull(updated.habitat)
        assertNull(updated.description)
        assertNull(updated.matureHeight)
        assertNull(updated.matureDiameter)
        assertNull(updated.timeToMaturity)
        assertNull(updated.careCalendarJson)
        assertNull(updated.usesJson)
        assertNull(updated.symbolism)
        assertNull(updated.edible)

        // Rien ne subsiste non plus après reconstruction du modèle de domaine.
        val result = updated.toResult()
        assertTrue(result.careCalendar.isEmpty())
        assertTrue(result.uses.isEmpty())
        assertNull(result.health)
    }

    @Test
    fun `le diagnostic de sante formule pour l'ancienne espece est efface`() = runTest {
        val updated = select(entity(), colchique)

        assertNull(updated.healthStatus)
        assertNull(updated.isHealthy)
        assertTrue(updated.recommendations.isEmpty())
    }

    @Test
    fun `la toxicite est recalculee sur la nouvelle espece par la liste locale`() = runTest {
        // Le candidat vient de Pl@ntNet : toxic == null. Sans recalcul, la fiche resterait muette
        // sur un colchique.
        val updated = select(entity(), SpeciesCandidate("Colchicum autumnale", "Colchique", 34, toxic = null))

        assertEquals(true, updated.toxic)
        assertNotNull(updated.edibilityNote)
        assertTrue(updated.edibilityNote!!.contains("toxique"))
    }

    @Test
    fun `une espece sans toxicite connue reste inconnue et non declaree inoffensive`() = runTest {
        val updated = select(entity(), SpeciesCandidate("Bellis perennis", "Pâquerette", 40))

        // null (inconnu), surtout pas false : personne ne s'est prononcé sur cette espèce.
        assertNull(updated.toxic)
        assertNull(updated.edible)
        assertEquals(EdibilityVerdict.UNKNOWN, updated.toResult().edibilityVerdict)
        assertTrue(updated.edibilityNote!!.contains("non évaluée"))
    }

    @Test
    fun `le drapeau espece protegee est recalcule sur la nouvelle espece`() = runTest {
        // Ancienne espèce protégée, nouvelle qui ne l'est pas : l'avertissement doit disparaître.
        val banal = select(entity(isProtected = true), SpeciesCandidate("Bellis perennis", "Pâquerette", 40))
        assertFalse(banal.isProtected)

        // Cas inverse : l'espèce choisie est protégée alors que l'ancienne ne l'était pas.
        val orchidee = select(entity(), SpeciesCandidate("Orchis militaris", "Orchis militaire", 40))
        assertTrue(orchidee.isProtected)
    }

    @Test
    fun `l'avertissement mycologique n'est jamais desactive et se leve sur une bascule champignon`() = runTest {
        // Champignon → champignon : le drapeau reste, même si l'hypothèse ne dit rien.
        val fungus = select(
            entity(commonName = "Amanite", scientificName = "Amanita rubescens", isFungus = true),
            SpeciesCandidate("Amanita phalloides", "Amanite phalloïde", 30),
        )
        assertTrue(fungus.isFungus)

        // Plante → champignon : le drapeau se lève, sinon l'avertissement systématique disparaîtrait.
        val bascule = select(entity(), SpeciesCandidate("Cantharellus cibarius", "Girolle", 40))
        assertTrue(bascule.isFungus)
    }

    @Test
    fun `l'ancienne espece principale devient une hypothese et emporte sa toxicite`() = runTest {
        val updated = select(
            entity(commonName = "Colchique", scientificName = "Colchicum autumnale", toxic = true),
            SpeciesCandidate("Allium ursinum", "Ail des ours", 45),
        )

        val alternatives = updated.toResult().alternatives
        assertEquals("Colchicum autumnale", alternatives.first().scientificName)
        assertEquals(true, alternatives.first().toxic)
        // Score faible + hypothèse toxique plausible : l'avertissement de confusion reste affiché.
        assertNotNull(updated.toResult().toxicConfusionWarningText())
    }

    @Test
    fun `les donnees de la photo et de l'utilisateur sont preservees`() = runTest {
        val updated = select(entity(), colchique)

        assertEquals(7L, updated.id)
        assertEquals(listOf("/data/photo1.jpg"), updated.photoPaths)
        assertEquals(46.2, updated.latitude!!, 0.0001)
        assertEquals(1_700_000_000_000, updated.dateTime)
        assertTrue(updated.isFavorite)
        assertEquals("Cueilli au bord du ruisseau", updated.notes)
    }

    @Test
    fun `les scores de l'ancienne espece sont effaces et la fiche passe en validee`() = runTest {
        val updated = select(entity(), colchique)

        assertNull(updated.scorePlantNet)
        assertNull(updated.scoreAi)
        assertEquals(34, updated.scoreFinal)
        assertFalse(updated.sourcesDisagree)
        assertTrue(updated.userConfirmed)
        assertNull(updated.gbifKey)      // celui de l'ancienne espèce ne doit pas suivre
        assertNull(updated.iucnCategory)
    }

    @Test
    fun `l'espece choisie disparait des hypotheses et l'ancienne y entre sans doublon`() = runTest {
        val updated = select(
            entity(
                alternatives = listOf(
                    colchique,
                    SpeciesCandidate("Convallaria majalis", "Muguet", 20),
                ),
            ),
            colchique,
        )

        val names = updated.toResult().alternatives.map { it.scientificName }
        assertEquals(listOf("Allium ursinum", "Convallaria majalis"), names)
    }
}

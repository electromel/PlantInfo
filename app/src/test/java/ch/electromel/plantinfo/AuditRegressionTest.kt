package ch.electromel.plantinfo

import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.keys.sanitizeApiKey
import ch.electromel.plantinfo.data.remote.ai.AiAnalysis
import ch.electromel.plantinfo.data.remote.ai.AiPrompt
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.toEntity
import ch.electromel.plantinfo.domain.ConfidenceEngine
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.ui.history.DateRange
import ch.electromel.plantinfo.ui.history.HistoryFilters
import ch.electromel.plantinfo.util.ImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-régression des défauts corrigés à la suite de l'audit du 2026-11-10 qui ne relèvent ni du
 * pipeline ni des clients IA (ceux-là sont dans `IdentificationRepositoryTest`, `AiOrchestratorTest`
 * et `AiClientResponseTest`). Référence : `log/2026.11.10-Audit v2 - rapport.md`.
 */
class AuditRegressionTest {

    private fun fiche(id: Long, photo: String) =
        ConfidenceEngine.plantNetOnly(listOf(SpeciesCandidate("Quercus robur", "Chêne", 70)))
            .toEntity(null, listOf(photo), AiProviderType.NONE, dateTime = 0)
            .copy(id = id)

    // --- F1 : suppression de l'historique ---

    private val dao = mockk<IdentificationDao>(relaxed = true)
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val history = HistoryRepository(dao, imageStorage, TestStrings())

    @Test
    fun `F1 - tout supprimer efface les photos de toutes les fiches de la base`() = runBlocking {
        coEvery { dao.getAll() } returns listOf(fiche(1, "/p/affichee.jpg"), fiche(2, "/p/masquee.jpg"))

        history.deleteAll()

        verify { imageStorage.delete("/p/affichee.jpg") }
        verify { imageStorage.delete("/p/masquee.jpg") }
        coVerify { dao.deleteAll() }
    }

    @Test
    fun `F1 - supprimer les fiches affichees ne touche ni aux autres fiches ni a leurs photos`() = runBlocking {
        val displayed = listOf(fiche(1, "/p/affichee.jpg"))

        history.deleteMany(displayed)

        verify(exactly = 1) { imageStorage.delete(any()) }
        verify { imageStorage.delete("/p/affichee.jpg") }
        coVerify { dao.delete(displayed) }
        coVerify(exactly = 0) { dao.deleteAll() }
    }

    @Test
    fun `F1 - un filtre est actif des qu'un critere masque des fiches`() {
        assertFalse(HistoryFilters().isActive)
        assertFalse(HistoryFilters(query = "   ").isActive)
        assertTrue(HistoryFilters(query = "chêne").isActive)
        assertTrue(HistoryFilters(favoritesOnly = true).isActive)
        assertTrue(HistoryFilters(withLocationOnly = true).isActive)
        assertTrue(HistoryFilters(dateRange = DateRange.LAST_7_DAYS).isActive)
    }

    // --- F9 : favori et notes ---

    @Test
    fun `F9 - favori et notes sont ecrits par mise a jour ciblee, pas en reecrivant la fiche`() = runBlocking {
        val entity = fiche(7, "/p/1.jpg")

        history.setFavorite(entity, true)
        history.setNotes(entity, "près du ruisseau")

        coVerify { dao.setFavorite(7, true) }
        coVerify { dao.setNotes(7, "près du ruisseau") }
        coVerify(exactly = 0) { dao.update(any()) }
    }

    // --- F6 : clé collée avec des caractères parasites ---

    @Test
    fun `F6 - une cle est debarrassee des caracteres qu'un en-tete HTTP refuse`() {
        assertEquals("AIzaCle-123_x", sanitizeApiKey("  AIza Cle-123_x\n"))
        assertEquals("sk-abc", sanitizeApiKey("​sk-abc…"))
        assertEquals("sk-abc", sanitizeApiKey("sk-abc"))
        assertEquals("", sanitizeApiKey(" \t\n"))
    }

    // --- F13 : hybrides ---

    private fun ai(scientific: String) = AiAnalysis(
        commonName = "Menthe",
        scientificName = scientific,
        confidence = 80,
        isFungus = false,
        isProtected = false,
        alternatives = emptyList(),
        healthStatus = "",
        isHealthy = true,
        recommendations = emptyList(),
        habitat = null,
        description = null,
        matureHeight = null,
        matureDiameter = null,
        timeToMaturity = null,
        edible = null,
        toxic = null,
        edibilityNote = null,
        careCalendar = emptyList(),
        uses = emptyList(),
        propagation = emptyList(),
        symbolism = null,
        photoSuggestions = emptyList(),
    )

    @Test
    fun `F13 - deux hybrides differents du meme genre sont en desaccord`() {
        val plantNet = listOf(SpeciesCandidate("Mentha × gracilis", "Menthe gingembre", 70))

        val result = ConfidenceEngine.combineWithAi(ai("Mentha × piperita"), plantNet)

        assertTrue(result.sourcesDisagree)
        assertEquals(56, result.scoreFinal) // 0,7 × 80, sans bonus d'accord
        assertEquals(listOf("Mentha × gracilis"), result.alternatives.map { it.scientificName })
    }

    @Test
    fun `F13 - un meme hybride s'accorde quelle que soit l'ecriture du signe`() {
        listOf("Mentha × piperita", "Mentha ×piperita", "Mentha x piperita", "mentha  X  piperita").forEach { written ->
            val plantNet = listOf(SpeciesCandidate(written, "Menthe poivrée", 70))

            val result = ConfidenceEngine.combineWithAi(ai("Mentha × piperita"), plantNet)

            assertFalse(written, result.sourcesDisagree)
            assertEquals(written, 86, result.scoreFinal)
        }
    }

    // --- F15 : confiance décimale ---

    @Test
    fun `F15 - une confiance ou un score decimal est arrondi au lieu de faire echouer la fiche`() {
        val json = """{"commonName":"Chêne","scientificName":"Quercus robur","confidence":87.5,""" +
            """"alternatives":[{"scientificName":"Quercus petraea","score":12.4}]}"""

        val analysis = AiPrompt.parse(json)

        assertEquals(88, analysis.confidence)
        assertEquals(12, analysis.alternatives.single().score)
    }
}

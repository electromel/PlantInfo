package ch.electromel.plantinfo

import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.remote.ai.AiAnalysis
import ch.electromel.plantinfo.data.remote.ai.AiException
import ch.electromel.plantinfo.data.remote.ai.AiFailureReason
import ch.electromel.plantinfo.data.remote.ai.AiPrompt
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.toEntity
import ch.electromel.plantinfo.domain.ConfidenceEngine
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.util.ImageStorage
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comportements fautifs relevés par l'audit du 2026-11-10 qui ne relèvent ni du pipeline ni des
 * clients IA (ceux-là sont figés dans `IdentificationRepositoryTest`, `AiOrchestratorTest` et
 * `AiClientResponseTest`). Chaque test décrit l'état **actuel** ; la correction devra l'inverser.
 * Référence : `log/2026.11.10-Audit v2 - rapport.md`.
 */
class KnownDefectsTest {

    private fun fiche(id: Long, photo: String) =
        ConfidenceEngine.plantNetOnly(listOf(SpeciesCandidate("Quercus robur", "Chêne", 70)))
            .toEntity(null, listOf(photo), AiProviderType.NONE, dateTime = 0)
            .copy(id = id)

    @Test
    fun `DEFAUT CONNU F1 - tout supprimer vide la base mais ne supprime que les photos recues`() = runBlocking {
        val dao = mockk<IdentificationDao>(relaxed = true)
        val imageStorage = mockk<ImageStorage>(relaxed = true)
        val repository = HistoryRepository(dao, imageStorage, TestStrings())

        // L'écran passe la liste **filtrée** : ici une seule des fiches de la base.
        repository.deleteAll(listOf(fiche(1, "/p/affichee.jpg")))

        coVerify { dao.deleteAll() } // toutes les lignes partent…
        verify(exactly = 1) { imageStorage.delete(any()) } // …mais une seule photo.
        verify { imageStorage.delete("/p/affichee.jpg") }
    }

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
    fun `DEFAUT CONNU F13 - deux hybrides differents du meme genre sont juges identiques`() {
        val plantNet = listOf(SpeciesCandidate("Mentha × gracilis", "Menthe gingembre", 70))

        val result = ConfidenceEngine.combineWithAi(ai("Mentha × piperita"), plantNet)

        // Attendu après correction : désaccord signalé, score abaissé, l'autre hybride en alternative.
        assertFalse(result.sourcesDisagree)
        assertEquals(86, result.scoreFinal) // bonus d'accord indu
        assertTrue(result.alternatives.isEmpty())
    }

    @Test
    fun `DEFAUT CONNU F15 - une confiance decimale fait echouer toute la reponse`() {
        val json = """{"commonName":"Chêne","scientificName":"Quercus robur","confidence":87.5}"""

        // Attendu après correction : confiance arrondie à 88.
        val failure = runCatching { AiPrompt.parse(json) }.exceptionOrNull()

        assertEquals(AiFailureReason.PARSE, (failure as AiException).reason)
    }
}

package ch.electromel.plantinfo.data.repo

import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.testEntity
import ch.electromel.plantinfo.util.AppLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantQaPromptTest {

    private val strings = TestStrings()

    private fun prompt(entity: ch.electromel.plantinfo.data.db.IdentificationEntity, language: AppLanguage = AppLanguage.FRENCH) =
        PlantQaRepository.buildPrompt(entity, "Peut-on la manger ?", language, strings)

    @Test
    fun `le lieu envoye avec la question est arrondi, pas la position exacte`() {
        val text = prompt(testEntity(latitude = 46.123456, longitude = 6.149876))

        assertTrue(text.contains("46.12, 6.15"))
        assertFalse("la position exacte ne doit pas partir chez un tiers", text.contains("46.1234"))
        assertFalse(text.contains("6.1498"))
        assertTrue(text.contains("arrondi"))
    }

    @Test
    fun `sans coordonnees le prompt le dit`() {
        val text = prompt(testEntity(latitude = null, longitude = null))

        assertTrue(text.contains("non disponible"))
    }

    @Test
    fun `la reponse est demandee dans la langue de l'application`() {
        assertTrue(prompt(testEntity(), AppLanguage.GERMAN).contains("German"))
        assertTrue(prompt(testEntity(), AppLanguage.SPANISH).contains("Spanish"))
    }

    @Test
    fun `la question et le rappel de prudence figurent dans le prompt`() {
        val text = prompt(testEntity())

        assertTrue(text.contains("Peut-on la manger ?"))
        assertTrue(text.contains("confirmation par un expert"))
    }
}

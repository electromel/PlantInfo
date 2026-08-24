package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.UseDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing de la réponse JSON des modèles. Le contrat est volontairement tolérant : les modèles
 * omettent des champs, en renvoient de vides, ou inventent des libellés de domaine.
 */
class AiPromptTest {

    @Test
    fun `calendrier, usages et symbolique sont extraits de la reponse`() {
        val analysis = AiPrompt.parse(
            """
            {
              "commonName": "Lavande vraie", "scientificName": "Lavandula angustifolia",
              "confidence": 88,
              "careCalendar": [
                {"label": "Plantation", "period": "Mars à mai", "note": "Sol drainé, plein soleil"},
                {"label": "Taille", "period": "Après la floraison"}
              ],
              "uses": [
                {"domain": "cosmetic", "detail": "Huile essentielle en parfumerie"},
                {"domain": "medicinal", "detail": "Infusion traditionnellement calmante"}
              ],
              "symbolism": "Pureté et sérénité dans le langage des fleurs."
            }
            """.trimIndent(),
        )

        assertEquals(2, analysis.careCalendar.size)
        assertEquals("Plantation", analysis.careCalendar[0].label)
        assertEquals("Mars à mai", analysis.careCalendar[0].period)
        assertEquals("Sol drainé, plein soleil", analysis.careCalendar[0].note)
        // Une opération sans précision reste valide : seule la note est absente.
        assertNull(analysis.careCalendar[1].note)

        assertEquals(UseDomain.COSMETIC, analysis.uses[0].domain)
        assertEquals(UseDomain.MEDICINAL, analysis.uses[1].domain)
        assertTrue(analysis.symbolism!!.startsWith("Pureté"))
    }

    @Test
    fun `un domaine d'usage inconnu retombe sur OTHER sans faire echouer le parsing`() {
        val analysis = AiPrompt.parse(
            """
            {
              "scientificName": "Urtica dioica", "confidence": 70,
              "uses": [{"domain": "textile", "detail": "Fibres pour la corderie"}]
            }
            """.trimIndent(),
        )

        assertEquals(1, analysis.uses.size)
        assertEquals(UseDomain.OTHER, analysis.uses.first().domain)
    }

    @Test
    fun `les entrees de calendrier ou d'usage inexploitables sont ecartees`() {
        val analysis = AiPrompt.parse(
            """
            {
              "scientificName": "Rosa canina", "confidence": 60,
              "careCalendar": [
                {"label": "Taille", "period": ""},
                {"period": "Mars"},
                {"label": "Récolte", "period": "Septembre à octobre"}
              ],
              "uses": [{"domain": "food"}, {"domain": "food", "detail": "Cynorhodons en confiture"}],
              "symbolism": "   "
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Récolte"), analysis.careCalendar.map { it.label })
        assertEquals(listOf("Cynorhodons en confiture"), analysis.uses.map { it.detail })
        // Une symbolique vide ne doit pas afficher une section vide sur la fiche.
        assertNull(analysis.symbolism)
    }

    @Test
    fun `une reponse sans les nouveaux champs reste valide`() {
        val analysis = AiPrompt.parse("""{"scientificName": "Quercus robur", "confidence": 90}""")

        assertTrue(analysis.careCalendar.isEmpty())
        assertTrue(analysis.uses.isEmpty())
        assertNull(analysis.symbolism)
    }
}

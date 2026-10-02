package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.UseDomain
import ch.electromel.plantinfo.util.AppLanguage
import java.time.LocalDate
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
    fun `les methodes de multiplication sont extraites et les entrees incompletes ecartees`() {
        val analysis = AiPrompt.parse(
            """
            {
              "scientificName": "Salvia rosmarinus", "confidence": 90,
              "propagation": [
                {"label": "Bouturage de tige", "howTo": "Prélever un rameau de 10 cm.", "period": "Fin d'été"},
                {"label": "Semis", "howTo": "Semer en surface, lever lente.", "period": "  "},
                {"label": "Marcottage"},
                {"howTo": "Sans libellé"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Bouturage de tige", "Semis"), analysis.propagation.map { it.label })
        assertEquals("Fin d'été", analysis.propagation[0].period)
        // Une période blanche vaut « non précisée », pas une parenthèse vide sur la fiche.
        assertNull(analysis.propagation[1].period)
    }

    @Test
    fun `les photos proposees sans raison sont ecartees et limitees a trois`() {
        val analysis = AiPrompt.parse(
            """
            {
              "scientificName": "Salix alba", "confidence": 55,
              "photoSuggestions": [
                {"organ": "leaf", "reason": "Le dessous d'une feuille : sa pilosité distingue S. alba de S. fragilis."},
                {"organ": "fruit"},
                {"organ": "bark", "reason": "L'écorce du tronc."},
                {"organ": "catkin", "reason": "Les chatons."},
                {"organ": "habit", "reason": "Le port entier."}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(3, analysis.photoSuggestions.size)
        assertEquals(PhotoOrgan.LEAF, analysis.photoSuggestions[0].organ)
        // Un organe inconnu du modèle retombe sur OTHER, la raison dit quoi cadrer.
        assertEquals(PhotoOrgan.OTHER, analysis.photoSuggestions[2].organ)
    }

    @Test
    fun `un organe hors saison garde sa periode, une periode blanche vaut maintenant`() {
        val analysis = AiPrompt.parse(
            """
            {
              "scientificName": "Chlorophytum comosum", "confidence": 95,
              "photoSuggestions": [
                {"organ": "habit", "reason": "Les stolons portant des plantules.", "period": " "},
                {"organ": "flower", "reason": "Les fleurs blanches en étoile.", "period": "mai à août"}
              ]
            }
            """.trimIndent(),
        )

        assertNull(analysis.photoSuggestions[0].period)
        assertEquals("mai à août", analysis.photoSuggestions[1].period)
    }

    @Test
    fun `le prompt porte la date du jour pour ne pas demander de photo hors saison`() {
        val input = AiAnalysisInput(
            images = emptyList(),
            plantNetCandidates = emptyList(),
            gps = null,
            language = AppLanguage.FRENCH,
        )
        val prompt = AiPrompt.buildInstruction(input, today = LocalDate.of(2026, 1, 15))

        assertTrue(prompt.contains("Date du jour : 15 janvier 2026"))
    }

    @Test
    fun `une reponse sans les nouveaux champs reste valide`() {
        val analysis = AiPrompt.parse("""{"scientificName": "Quercus robur", "confidence": 90}""")

        assertTrue(analysis.careCalendar.isEmpty())
        assertTrue(analysis.uses.isEmpty())
        assertTrue(analysis.propagation.isEmpty())
        assertNull(analysis.symbolism)
    }

    @Test
    fun `un objet JSON sans espece n'est pas une identification`() {
        // Réponse bloquée, objet vide, espèce blanche : avant, chacun donnait un « succès » sans nom.
        listOf(
            "{}",
            """{"confidence": 90}""",
            """{"scientificName": "", "confidence": 90}""",
            """{"scientificName": "   ", "commonName": "Rose"}""",
            """{"scientificName": null}""",
            """{"promptFeedback":{"blockReason":"SAFETY"}}""",
        ).forEach { reply ->
            try {
                AiPrompt.parse(reply)
                org.junit.Assert.fail("« $reply » devait être refusée")
            } catch (e: AiException) {
                assertEquals(reply, AiFailureReason.PARSE, e.reason)
            }
        }
    }

    @Test
    fun `le texte autour du JSON reste toleré`() {
        val analysis = AiPrompt.parse(
            """
            Voici le résultat :
            ```json
            {"scientificName": "Quercus robur", "confidence": 90}
            ```
            """.trimIndent(),
        )

        assertEquals("Quercus robur", analysis.scientificName)
    }

    @Test
    fun `le lieu du prompt est arrondi a environ un kilometre`() {
        val input = AiAnalysisInput(
            images = emptyList(),
            plantNetCandidates = emptyList(),
            gps = ch.electromel.plantinfo.domain.model.GpsLocation(46.123456, 6.149876, 412.7, 8f),
            language = AppLanguage.FRENCH,
        )

        val prompt = AiPrompt.buildInstruction(input, today = LocalDate.of(2026, 5, 1))

        assertTrue(prompt.contains("Latitude 46.12, longitude 6.15"))
        assertTrue(prompt.contains("altitude 412 m"))
        assertTrue("la position exacte ne doit pas figurer", !prompt.contains("46.1234"))
        assertTrue(!prompt.contains("6.1498"))
    }

    @Test
    fun `sans position le prompt le dit`() {
        val input = AiAnalysisInput(emptyList(), emptyList(), gps = null, language = AppLanguage.FRENCH)

        assertTrue(AiPrompt.buildInstruction(input).contains("Position GPS non disponible."))
    }
}

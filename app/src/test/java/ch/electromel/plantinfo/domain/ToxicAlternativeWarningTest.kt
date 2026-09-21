package ch.electromel.plantinfo.domain

import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import ch.electromel.plantinfo.domain.model.isToxic
import ch.electromel.plantinfo.domain.model.plausibleToxicAlternatives
import ch.electromel.plantinfo.domain.model.toxicConfusionWarningText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Avertissement de confusion toxique : score final < 70 ET hypothèse toxique plausible (> 10).
 */
class ToxicAlternativeWarningTest {

    private val strings = TestStrings()

    private fun result(
        scoreFinal: Int,
        alternatives: List<SpeciesCandidate>,
        scientific: String = "Allium ursinum",
    ) = IdentificationResult(
        commonName = "Ail des ours",
        scientificName = scientific,
        scorePlantNet = null,
        scoreAi = scoreFinal,
        scoreFinal = scoreFinal,
        aiProvider = AiProviderType.CLAUDE,
        alternatives = alternatives,
        isFungus = false,
        isProtected = false,
        health = null,
        habitat = null,
        description = null,
        matureHeight = null,
        matureDiameter = null,
        timeToMaturity = null,
        edible = true,
        toxic = false,
        edibilityNote = null,
        careCalendar = emptyList(),
        uses = emptyList(),
        symbolism = null,
        gbifKey = null,
        iucnCategory = null,
        sourcesDisagree = false,
        complementaryPhotoRequest = null,
    )

    @Test
    fun `une hypothese toxique plausible sur un score faible declenche l'avertissement`() {
        val r = result(58, listOf(SpeciesCandidate("Colchicum autumnale", "Colchique d'automne", 35)))

        assertEquals(listOf("Colchicum autumnale"), r.plausibleToxicAlternatives().map { it.scientificName })
        val warning = r.toxicConfusionWarningText(strings)
        assertNotNull(warning)
        assertTrue(warning!!.contains("Colchique d'automne (Colchicum autumnale)"))
        assertTrue(warning.contains("58/100"))
    }

    @Test
    fun `un score assure n'affiche pas l'avertissement`() {
        // 80 est la borne haute exclue : au-delà, l'hypothèse concurrente est considérée écartée.
        val toxic = listOf(SpeciesCandidate("Colchicum autumnale", "Colchique", 35))
        assertNull(result(80, toxic).toxicConfusionWarningText(strings))
        assertNotNull(result(79, toxic).toxicConfusionWarningText(strings))
    }

    @Test
    fun `une hypothese toxique trop faible est ignoree`() {
        // 10 est la borne basse exclue : en dessous, l'hypothèse n'est plus plausible.
        assertNull(result(50, listOf(SpeciesCandidate("Conium maculatum", "Grande ciguë", 10)))
            .toxicConfusionWarningText(strings))
        assertNotNull(result(50, listOf(SpeciesCandidate("Conium maculatum", "Grande ciguë", 11)))
            .toxicConfusionWarningText(strings))
    }

    @Test
    fun `une hypothese inoffensive ne declenche rien`() {
        val r = result(45, listOf(SpeciesCandidate("Bellis perennis", "Pâquerette", 40)))
        assertTrue(r.plausibleToxicAlternatives().isEmpty())
        assertNull(r.toxicConfusionWarningText(strings))
    }

    @Test
    fun `un candidat Pl@ntNet sans drapeau est reconnu toxique par la liste locale`() {
        // Cas décisif : les candidats Pl@ntNet arrivent toujours avec toxic == null, et c'est
        // l'hypothèse concurrente promue en cas de désaccord.
        val plantNet = SpeciesCandidate("Amanita phalloides", "Amanite phalloïde", 30, toxic = null)
        assertTrue(plantNet.isToxic)
        assertNotNull(result(55, listOf(plantNet)).toxicConfusionWarningText(strings))
    }

    @Test
    fun `la liste locale l'emporte sur un drapeau IA rassurant`() {
        val wrong = SpeciesCandidate("Colchicum autumnale", "Colchique", 30, toxic = false)
        assertTrue(wrong.isToxic)
    }

    @Test
    fun `le drapeau IA suffit pour une espece absente de la liste locale`() {
        val flagged = SpeciesCandidate("Espece inconnue", null, 30, toxic = true)
        assertTrue(flagged.isToxic)
        assertFalse(ToxicSpeciesChecker.isToxic("Espece inconnue"))
    }

    @Test
    fun `des seuils personnalises remplacent les valeurs par defaut`() {
        val toxic = listOf(SpeciesCandidate("Colchicum autumnale", "Colchique", 15))

        // Score 85 : au-dessus du défaut (80), donc silencieux ; un utilisateur prudent qui monte
        // le seuil à 100 doit être averti.
        assertNull(result(85, toxic).toxicConfusionWarningText(strings))
        assertNotNull(result(85, toxic).toxicConfusionWarningText(strings, ToxicAlertThresholds(maxScore = 100)))

        // À l'inverse, remonter la plausibilité minimale au-dessus de 15 fait taire l'alerte.
        assertNull(
            result(50, toxic).toxicConfusionWarningText(strings, ToxicAlertThresholds(minAlternativeScore = 20)),
        )
    }

    @Test
    fun `les seuils par defaut du reglage sont ceux du domaine`() {
        val defaults = ToxicAlertThresholds()
        assertEquals(IdentificationResult.TOXIC_ALERT_MAX_SCORE, defaults.maxScore)
        assertEquals(IdentificationResult.PLAUSIBLE_ALTERNATIVE_MIN_SCORE, defaults.minAlternativeScore)
        // Les plages réglables doivent contenir les valeurs livrées, sinon le curseur naîtrait hors borne.
        assertTrue(defaults.maxScore in ToxicAlertThresholds.MAX_SCORE_RANGE)
        assertTrue(defaults.minAlternativeScore in ToxicAlertThresholds.MIN_ALTERNATIVE_RANGE)
    }

    @Test
    fun `plusieurs hypotheses toxiques sont toutes citees`() {
        val r = result(40, listOf(
            SpeciesCandidate("Colchicum autumnale", "Colchique", 30),
            SpeciesCandidate("Convallaria majalis", "Muguet", 25),
            SpeciesCandidate("Bellis perennis", "Pâquerette", 20),
        ))

        assertEquals(2, r.plausibleToxicAlternatives().size)
        val warning = r.toxicConfusionWarningText(strings)!!
        assertTrue(warning.contains("restent plausibles"))
        assertTrue(warning.contains("Colchique"))
        assertTrue(warning.contains("Muguet"))
    }
}

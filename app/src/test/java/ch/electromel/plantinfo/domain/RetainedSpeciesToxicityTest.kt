package ch.electromel.plantinfo.domain

import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.domain.model.EdibilityVerdict
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.edibilitySummaryText
import ch.electromel.plantinfo.domain.model.edibilityVerdict
import ch.electromel.plantinfo.domain.model.toxicFromLocalList
import ch.electromel.plantinfo.domain.model.toxicityUnknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toxicité de l'espèce **retenue** (constat F2 de l'audit du 2026-11-10). Règle validée :
 * - espèce dans la liste locale : « Toxique », en disant que cela vient de la liste, même si l'IA
 *   dit le contraire ;
 * - hors liste et sans avis de l'IA : « Toxicité inconnue » ;
 * - hors liste avec un avis de l'IA : inchangé.
 */
class RetainedSpeciesToxicityTest {

    private val strings = TestStrings()
    private val listMention = strings.get(R.string.edibility_toxic_local_list)
    private val unknownMention = strings.get(R.string.edibility_toxicity_unknown)

    /** Fiche Pl@ntNet seule (toxic et edible toujours null), ajustée selon le cas testé. */
    private fun fiche(
        scientific: String,
        toxic: Boolean? = null,
        edible: Boolean? = null,
        note: String? = null,
    ): IdentificationResult =
        ConfidenceEngine.plantNetOnly(listOf(SpeciesCandidate(scientific, null, 90)))
            .copy(toxic = toxic, edible = edible, edibilityNote = note)

    @Test
    fun `espece listee sans avis de l'IA - toxique, d'apres la liste`() {
        val fiche = fiche("Colchicum autumnale")

        assertEquals(EdibilityVerdict.TOXIC, fiche.edibilityVerdict)
        assertTrue(fiche.toxicFromLocalList)
        assertFalse(fiche.toxicityUnknown)
        assertEquals("Toxique — $listMention", fiche.edibilitySummaryText(strings))
    }

    @Test
    fun `espece listee que l'IA dit inoffensive - la liste l'emporte`() {
        val fiche = fiche("Colchicum autumnale", toxic = false, edible = true, note = "Sans danger.")

        assertEquals(EdibilityVerdict.TOXIC, fiche.edibilityVerdict)
        assertTrue(fiche.toxicFromLocalList)
        // La note de l'IA reste visible, mais derrière le verdict et sa provenance.
        assertEquals("Toxique — $listMention — Sans danger.", fiche.edibilitySummaryText(strings))
    }

    @Test
    fun `espece listee que l'IA dit deja toxique - pas de mention de la liste`() {
        val fiche = fiche("Colchicum autumnale", toxic = true, note = "Mortelle.")

        assertEquals(EdibilityVerdict.TOXIC, fiche.edibilityVerdict)
        assertFalse(fiche.toxicFromLocalList)
        assertEquals("Toxique — Mortelle.", fiche.edibilitySummaryText(strings))
    }

    @Test
    fun `hors liste et sans avis de l'IA - toxicite inconnue, dite explicitement`() {
        val fiche = fiche("Quercus robur")

        assertEquals(EdibilityVerdict.UNKNOWN, fiche.edibilityVerdict)
        assertTrue(fiche.toxicityUnknown)
        assertEquals(unknownMention, fiche.edibilitySummaryText(strings))
    }

    @Test
    fun `hors liste avec un avis de l'IA - inchange`() {
        val nonToxic = fiche("Quercus robur", toxic = false)
        val edible = fiche("Malus domestica", toxic = false, edible = true, note = "Fruit comestible.")
        val toxic = fiche("Quercus robur", toxic = true)

        assertEquals(EdibilityVerdict.UNKNOWN, nonToxic.edibilityVerdict)
        assertFalse(nonToxic.toxicityUnknown)
        assertNull(nonToxic.edibilitySummaryText(strings))

        assertEquals(EdibilityVerdict.EDIBLE, edible.edibilityVerdict)
        assertEquals("Comestible — Fruit comestible.", edible.edibilitySummaryText(strings))

        assertEquals(EdibilityVerdict.TOXIC, toxic.edibilityVerdict)
        assertFalse(toxic.toxicFromLocalList)
    }

    @Test
    fun `un champignon d'un genre a risque retenu par l'IA sans avis de toxicite est signale`() {
        val fiche = fiche("Amanita phalloides")

        assertEquals(EdibilityVerdict.TOXIC, fiche.edibilityVerdict)
        assertTrue(fiche.toxicFromLocalList)
    }
}

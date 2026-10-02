package ch.electromel.plantinfo.domain

import ch.electromel.plantinfo.data.remote.ai.AiPrompt
import ch.electromel.plantinfo.domain.model.EdibilityVerdict
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.edibilityVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les listes locales de sécurité s'appliquent à l'espèce **principale** — pas seulement aux
 * alternatives. Chaque test reprend un cas où, avant, la fiche rassurait à tort.
 */
class SafetyFlagsTest {

    private fun ai(json: String) = ConfidenceEngine.combineWithAi(AiPrompt.parse(json), emptyList())

    private fun plantNetOnly(scientific: String) =
        ConfidenceEngine.plantNetOnly(listOf(SpeciesCandidate(scientific, "nom", 90)))

    @Test
    fun `Pl@ntNet seul - une espece toxique listee n'est plus muette`() {
        val brut = plantNetOnly("Colchicum autumnale")
        // L'état de départ du défaut : aucune information de toxicité, donc aucune alerte.
        assertEquals(EdibilityVerdict.UNKNOWN, brut.edibilityVerdict)

        val renforce = SafetyFlags.reinforce(brut)

        assertEquals(true, renforce.toxic)
        assertEquals(EdibilityVerdict.TOXIC, renforce.edibilityVerdict)
    }

    @Test
    fun `l'IA qui declare comestible une espece toxique listee est contredite par la liste`() {
        val trompee = ai(
            """{"commonName":"Amanite","scientificName":"Amanita phalloides","confidence":90,
                "isFungus":false,"edible":true,"toxic":false}""",
        )
        assertEquals(EdibilityVerdict.EDIBLE, trompee.edibilityVerdict)

        val renforce = SafetyFlags.reinforce(trompee)

        assertEquals(EdibilityVerdict.TOXIC, renforce.edibilityVerdict)
        // L'avertissement champignon n'est plus laissé au seul jugement de l'IA.
        assertTrue(renforce.isFungus)
    }

    @Test
    fun `une espece hors liste garde le verdict de l'IA, sans jamais devenir non toxique`() {
        val inconnue = SafetyFlags.reinforce(plantNetOnly("Rosa canina"))
        assertNull(inconnue.toxic)
        assertFalse(inconnue.isFungus)
        assertFalse(inconnue.isProtected)

        val declaree = SafetyFlags.reinforce(
            ai("""{"scientificName":"Rosa canina","confidence":80,"toxic":false,"edible":true}"""),
        )
        assertEquals(false, declaree.toxic)
        assertEquals(EdibilityVerdict.EDIBLE, declaree.edibilityVerdict)
    }

    @Test
    fun `un drapeau leve par l'IA n'est jamais retire`() {
        val result = SafetyFlags.reinforce(
            ai(
                """{"scientificName":"Rosa canina","confidence":80,"isFungus":true,"isProtected":true,"toxic":true}""",
            ),
        )

        assertTrue(result.isFungus)
        assertTrue(result.isProtected)
        assertEquals(true, result.toxic)
    }

    @Test
    fun `une espece menacee au sens UICN est signalee protegee`() {
        val menacee = plantNetOnly("Rosa canina").copy(iucnCategory = "EN")
        assertTrue(SafetyFlags.reinforce(menacee).isProtected)

        val preoccupation = plantNetOnly("Rosa canina").copy(iucnCategory = "LC")
        assertFalse(SafetyFlags.reinforce(preoccupation).isProtected)
    }

    @Test
    fun `la liste suisse leve le drapeau protege`() {
        assertTrue(SafetyFlags.reinforce(plantNetOnly("Ophrys apifera")).isProtected)
    }

    @Test
    fun `le renforcement est idempotent et ne copie rien quand tout est deja en regle`() {
        val deja = SafetyFlags.reinforce(plantNetOnly("Colchicum autumnale"))

        assertEquals(deja, SafetyFlags.reinforce(deja))
        assertSame(deja, SafetyFlags.reinforce(deja))
    }

    @Test
    fun `les genres ajoutes a la liste sont reconnus`() {
        listOf(
            "Aesculus hippocastanum", "Buxus sempervirens", "Euonymus europaeus", "Lantana camara",
            "Cytisus scoparius", "Pteridium aquilinum", "Nicotiana tabacum",
            "Coprinopsis atramentaria", "Tricholoma pardinum", "Suillellus luridus",
            "Helvella crispa", "Hebeloma crustuliniforme", "Psilocybe semilanceata",
        ).forEach { name ->
            assertTrue("$name devrait être signalée toxique", ToxicSpeciesChecker.isToxic(name))
        }
    }

    @Test
    fun `les especes courantes inoffensives ne sont pas signalees`() {
        listOf(
            "Rosa canina", "Quercus robur", "Cantharellus cibarius", "Boletus edulis",
            "Macrolepiota procera", "Solanum lycopersicum", "Tricholoma terreum",
        ).forEach { name ->
            assertFalse("$name ne devrait pas être signalée toxique", ToxicSpeciesChecker.isToxic(name))
        }
    }

    @Test
    fun `les genres de champignons ajoutes activent l'avertissement mycologique`() {
        listOf("Hebeloma crustuliniforme", "Verpa bohemica", "Suillellus luridus", "Neoboletus luridiformis")
            .forEach { assertTrue(it, FungusChecker.isFungus(it)) }
    }
}

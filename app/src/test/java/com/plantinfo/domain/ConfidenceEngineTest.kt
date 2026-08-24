package com.plantinfo.domain

import com.plantinfo.data.remote.ai.AiAnalysis
import com.plantinfo.domain.model.CareTask
import com.plantinfo.domain.model.SpeciesCandidate
import com.plantinfo.domain.model.SpeciesUse
import com.plantinfo.domain.model.UseDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceEngineTest {

    private fun ai(
        scientific: String,
        confidence: Int,
        isFungus: Boolean = false,
        alternatives: List<SpeciesCandidate> = emptyList(),
    ) = AiAnalysis(
        commonName = "Test",
        scientificName = scientific,
        confidence = confidence,
        isFungus = isFungus,
        isProtected = false,
        alternatives = alternatives,
        healthStatus = "Bon",
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
        symbolism = null,
        complementary = null,
    )

    @Test
    fun `accord entre sources renforce le score et ne signale pas de divergence`() {
        val candidates = listOf(SpeciesCandidate("Quercus robur", "Chêne", 70))
        val result = ConfidenceEngine.combineWithAi(ai("Quercus robur", 80), candidates)

        assertFalse(result.sourcesDisagree)
        // 0.6*80 + 0.4*70 = 76, +10 bonus = 86
        assertEquals(86, result.scoreFinal)
    }

    @Test
    fun `desaccord avec un Pl@ntNet confiant est signale`() {
        val candidates = listOf(SpeciesCandidate("Acer campestre", "Érable", 65))
        val result = ConfidenceEngine.combineWithAi(ai("Fagus sylvatica", 80), candidates)

        assertTrue(result.sourcesDisagree)
        // 0.7*80 = 56
        assertEquals(56, result.scoreFinal)
        // le candidat Pl@ntNet en désaccord devient la première alternative
        assertEquals("Acer campestre", result.alternatives.first().scientificName)
    }

    @Test
    fun `champignon sans reference Pl@ntNet suit la confiance IA`() {
        val result = ConfidenceEngine.combineWithAi(ai("Amanita muscaria", 72, isFungus = true), emptyList())

        assertFalse(result.sourcesDisagree)
        assertEquals(72, result.scoreFinal)
        assertTrue(result.isFungus)
    }

    @Test
    fun `score bas declenche une demande de photo complementaire`() {
        val result = ConfidenceEngine.combineWithAi(ai("Rosa canina", 40), emptyList())
        assertTrue(result.scoreFinal < 60)
        assertTrue(result.complementaryPhotoRequest != null)
    }

    @Test
    fun `les metadonnees Pl@ntNet suivent l'espece quand les sources s'accordent`() {
        val candidates = listOf(SpeciesCandidate("Quercus robur", "Chêne", 70, gbifKey = 2878688, iucnCategory = "LC"))
        val result = ConfidenceEngine.combineWithAi(ai("Quercus robur", 80), candidates)

        assertEquals(2878688L, result.gbifKey)
        assertEquals("LC", result.iucnCategory)
    }

    @Test
    fun `les metadonnees Pl@ntNet sont ecartees quand l'IA corrige l'espece`() {
        // Sinon la carte interrogerait le taxon GBIF d'Acer campestre en affichant Fagus sylvatica.
        val candidates = listOf(SpeciesCandidate("Acer campestre", "Érable", 65, gbifKey = 3189866, iucnCategory = "LC"))
        val result = ConfidenceEngine.combineWithAi(ai("Fagus sylvatica", 80), candidates)

        assertEquals(null, result.gbifKey)
        assertEquals(null, result.iucnCategory)
    }

    @Test
    fun `un candidat Pl@ntNet non prioritaire cede ses metadonnees si l'IA le retient`() {
        val candidates = listOf(
            SpeciesCandidate("Acer campestre", "Érable", 65, gbifKey = 3189866),
            SpeciesCandidate("Fagus sylvatica", "Hêtre", 30, gbifKey = 2882316, iucnCategory = "LC"),
        )
        val result = ConfidenceEngine.combineWithAi(ai("Fagus sylvatica", 80), candidates)

        assertEquals(2882316L, result.gbifKey)
        assertEquals("LC", result.iucnCategory)
    }

    @Test
    fun `calendrier, usages et symbolique de l'IA sont repris tels quels`() {
        val analysis = ai("Lavandula angustifolia", 85).copy(
            careCalendar = listOf(CareTask("Taille", "Après la floraison")),
            uses = listOf(SpeciesUse(UseDomain.COSMETIC, "Parfumerie")),
            symbolism = "Sérénité",
        )
        val result = ConfidenceEngine.combineWithAi(analysis, emptyList())

        assertEquals("Taille", result.careCalendar.single().label)
        assertEquals(UseDomain.COSMETIC, result.uses.single().domain)
        assertEquals("Sérénité", result.symbolism)
    }

    @Test
    fun `resultat Pl@ntNet seul n'invente ni calendrier ni usages`() {
        val result = ConfidenceEngine.plantNetOnly(listOf(SpeciesCandidate("Bellis perennis", "Pâquerette", 88)))

        assertTrue(result.careCalendar.isEmpty())
        assertTrue(result.uses.isEmpty())
        assertEquals(null, result.symbolism)
    }

    @Test
    fun `resultat Pl@ntNet seul utilise le meilleur candidat`() {
        val candidates = listOf(
            SpeciesCandidate("Bellis perennis", "Pâquerette", 88),
            SpeciesCandidate("Leucanthemum vulgare", "Marguerite", 40),
        )
        val result = ConfidenceEngine.plantNetOnly(candidates)

        assertEquals("Bellis perennis", result.scientificName)
        assertEquals(88, result.scoreFinal)
        assertEquals(1, result.alternatives.size)
        assertEquals(null, result.scoreAi)
    }
}

package com.plantinfo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le coût affiché après chaque identification et chaque question est un chiffre que l'utilisateur
 * va croire : ces tests fixent les deux façons de le trahir — se tromper de tarif, et afficher
 * « 0 » quand on ne sait pas.
 */
class TokenUsageTest {

    @Test
    fun `le cout suit le tarif du modele, entree et sortie separement`() {
        // 1 M de jetons d'entrée + 1 M de sortie sur claude-sonnet-5 : 3,00 + 15,00 $.
        val usage = TokenUsage("claude-sonnet-5", inputTokens = 1_000_000, outputTokens = 1_000_000)

        assertEquals(18.00, AiPricing.costUsd(usage)!!, 1e-9)
    }

    @Test
    fun `un modele sans tarif connu n'invente pas de cout`() {
        val usage = TokenUsage("un-modele-futur", inputTokens = 5_000, outputTokens = 500)

        assertNull(AiPricing.costUsd(usage))
        // …et l'affichage ne doit pas non plus fabriquer un montant.
        assertNull(usage.costText())
    }

    @Test
    fun `un cout infime s'affiche comme infime, jamais comme gratuit`() {
        // 10 jetons d'entrée sur Gemini Flash : de l'ordre de 7,5 millionièmes de dollar.
        val usage = TokenUsage("gemini-flash-latest", inputTokens = 10, outputTokens = 0)

        val text = usage.costText()!!
        assertTrue("attendu un « moins que », obtenu : $text", text.startsWith("< "))
    }

    @Test
    fun `le total additionne entree et sortie`() {
        val usage = TokenUsage("gpt-4o", inputTokens = 982, outputTokens = 252)

        assertEquals(1_234, usage.totalTokens)
        assertTrue(usage.tokensText().contains("1"))
        assertTrue(usage.tokensText().contains("entrée"))
    }

    @Test
    fun `une consommation nulle est consideree vide - rien a afficher`() {
        assertTrue(TokenUsage("gpt-4o", 0, 0).isEmpty)
        assertTrue(!TokenUsage("gpt-4o", 1, 0).isEmpty)
    }

    @Test
    fun `additionner deux appels du meme modele conserve le modele`() {
        val total = TokenUsage("gpt-4o", 100, 20) + TokenUsage("gpt-4o", 50, 10)

        assertEquals("gpt-4o", total.model)
        assertEquals(150, total.inputTokens)
        assertEquals(30, total.outputTokens)
    }
}

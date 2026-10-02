package ch.electromel.plantinfo.data.keys

import ch.electromel.plantinfo.domain.model.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

/** Règles du stockage des clés qui ne dépendent pas du Keystore, donc testables en JVM. */
class ApiKeyStoreRulesTest {

    private val order = AiProviderType.DEFAULT_FALLBACK_ORDER

    private fun hasKeys(vararg types: AiProviderType): (AiProviderType) -> Boolean = { it in types }

    // --- Mode « Gemini gratuit seul » ---

    @Test
    fun `avec une cle Gemini, le mode ecarte les IA payantes`() {
        val usable = usableAiProviders(
            order, freeGeminiOnly = true,
            hasKey = hasKeys(AiProviderType.GEMINI, AiProviderType.CLAUDE),
        )

        assertEquals(listOf(AiProviderType.GEMINI), usable)
    }

    @Test
    fun `sans cle Gemini, le mode n'ignore plus la cle que l'utilisateur vient de saisir`() {
        // Le défaut corrigé : une clé Claude validée, puis « aucune clé IA » à l'identification.
        val usable = usableAiProviders(order, freeGeminiOnly = true, hasKey = hasKeys(AiProviderType.CLAUDE))

        assertEquals(listOf(AiProviderType.CLAUDE), usable)
    }

    @Test
    fun `mode desactive - toutes les cles sont utilisees dans l'ordre de repli`() {
        val usable = usableAiProviders(
            listOf(AiProviderType.GPT, AiProviderType.CLAUDE, AiProviderType.GEMINI),
            freeGeminiOnly = false,
            hasKey = hasKeys(AiProviderType.GEMINI, AiProviderType.CLAUDE, AiProviderType.GPT),
        )

        assertEquals(listOf(AiProviderType.GPT, AiProviderType.CLAUDE, AiProviderType.GEMINI), usable)
    }

    @Test
    fun `aucune cle donne une liste vide`() {
        assertTrue(usableAiProviders(order, freeGeminiOnly = true, hasKey = hasKeys()).isEmpty())
    }

    @Test
    fun `l'etat des cles suit la meme regle que l'orchestrateur`() {
        fun snapshot(vararg providers: ApiProvider, freeOnly: Boolean = true) = KeysSnapshot(
            present = ApiProvider.entries.associateWith { it in providers },
            fallbackOrder = order,
            freeGeminiOnly = freeOnly,
        )

        // Seulement Claude : l'IA est disponible, le mode n'est pas « actif ».
        val claudeSeule = snapshot(ApiProvider.PLANTNET, ApiProvider.CLAUDE)
        assertTrue(claudeSeule.hasAi)
        assertFalse(claudeSeule.geminiOnlyActive)

        // Gemini + Claude : le mode s'applique, seule Gemini compte.
        val geminiEtClaude = snapshot(ApiProvider.GEMINI, ApiProvider.CLAUDE)
        assertTrue(geminiEtClaude.geminiOnlyActive)
        assertTrue(geminiEtClaude.hasAi)

        // Aucune IA du tout.
        assertFalse(snapshot(ApiProvider.PLANTNET).hasAi)
    }

    // --- Récupération d'un stockage chiffré illisible ---

    @Test
    fun `un stockage sain est ouvert sans rien detruire`() {
        var resets = 0
        val result = openWithRecovery(open = { "prefs" }, reset = { resets++ })

        assertEquals("prefs", result)
        assertEquals(0, resets)
    }

    @Test
    fun `une panne passagere se regle d'un second essai, sans detruire les cles`() {
        var attempts = 0
        var resets = 0

        val result = openWithRecovery(
            open = { if (attempts++ == 0) throw GeneralSecurityException("Keystore indisponible") else "prefs" },
            reset = { resets++ },
        )

        assertEquals("prefs", result)
        assertEquals("les clés de l'utilisateur ne sont pas effacées pour une panne passagère", 0, resets)
    }

    @Test
    fun `un stockage durablement illisible est recree a vide au lieu de planter a chaque lancement`() {
        var corrupted = true
        var resets = 0

        val result = openWithRecovery(
            open = { if (corrupted) throw GeneralSecurityException("MAC verification failed") else "neuf" },
            reset = { resets++; corrupted = false },
        )

        assertEquals("neuf", result)
        assertEquals(1, resets)
    }

    @Test
    fun `les trois familles d'erreurs de dechiffrement sont recuperees`() {
        listOf<() -> Nothing>(
            { throw GeneralSecurityException("a") },
            { throw IOException("b") },
            { throw SecurityException("c") },
        ).forEach { failure ->
            var corrupted = true
            val result = openWithRecovery(
                open = { if (corrupted) failure() else "ok" },
                reset = { corrupted = false },
            )
            assertEquals("ok", result)
        }
    }

    @Test
    fun `si la recreation echoue aussi, l'erreur remonte`() {
        try {
            openWithRecovery<String>(open = { throw GeneralSecurityException("irrécupérable") }, reset = {})
            fail("l'erreur devait remonter")
        } catch (e: GeneralSecurityException) {
            assertSame(GeneralSecurityException::class.java, e.javaClass)
        }
    }

    @Test
    fun `une erreur sans rapport avec le chiffrement n'est pas avalee`() {
        var resets = 0
        try {
            openWithRecovery<String>(open = { throw IllegalStateException("bogue") }, reset = { resets++ })
            fail("l'erreur devait remonter")
        } catch (e: IllegalStateException) {
            assertEquals(0, resets)
        }
    }
}

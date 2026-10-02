package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.aiInput
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.domain.model.AiProviderType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Repli entre fournisseurs : un échec, quelle que soit sa forme, passe au suivant. */
class AiOrchestratorTest {

    private val analysis = AiPrompt.parse("""{"scientificName":"Rosa canina","confidence":80}""")

    private class FakeProvider(
        override val type: AiProviderType,
        private val onCall: () -> Unit = {},
    ) : AiProvider {
        var analyzeCalls = 0
        override suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis {
            analyzeCalls++
            onCall()
            return AiPrompt.parse("""{"scientificName":"Rosa canina","confidence":80}""")
        }

        override suspend fun ask(prompt: String, apiKey: String): AiAnswer {
            onCall()
            return AiAnswer("réponse de $type", null)
        }

        override suspend fun testKey(apiKey: String): Boolean {
            onCall()
            return true
        }
    }

    private fun orchestrator(vararg providers: FakeProvider): AiOrchestrator {
        val keyStore = mockk<ApiKeyStore>()
        every { keyStore.availableAiProvidersInOrder() } returns providers.map { it.type }
        providers.forEach { every { keyStore.getKey(ApiProvider.forAiType(it.type)!!) } returns "cle-${it.type}" }
        return AiOrchestrator(keyStore, providers.toList())
    }

    private fun fails(reason: AiFailureReason) = { throw AiException(reason, "échec simulé") }

    @Test
    fun `sans cle IA, l'orchestrateur le dit au lieu d'echouer`() = runBlocking {
        val keyStore = mockk<ApiKeyStore>()
        every { keyStore.availableAiProvidersInOrder() } returns emptyList()
        val orchestrator = AiOrchestrator(keyStore, emptyList())

        assertTrue(orchestrator.analyze(aiInput()) is AiOutcome.NoProvidersConfigured)
        assertTrue(orchestrator.ask("q") is AiAnswerOutcome.NoProvidersConfigured)
    }

    @Test
    fun `le premier fournisseur qui repond gagne et les suivants ne sont pas appeles`() = runBlocking {
        val first = FakeProvider(AiProviderType.CLAUDE)
        val second = FakeProvider(AiProviderType.GEMINI)

        val outcome = orchestrator(first, second).analyze(aiInput())

        assertEquals(AiProviderType.CLAUDE, (outcome as AiOutcome.Success).provider)
        assertEquals(0, second.analyzeCalls)
    }

    @Test
    fun `un echec typé passe au fournisseur suivant`() = runBlocking {
        val outcome = orchestrator(
            FakeProvider(AiProviderType.CLAUDE, fails(AiFailureReason.BILLING)),
            FakeProvider(AiProviderType.GEMINI),
        ).analyze(aiInput())

        assertEquals(AiProviderType.GEMINI, (outcome as AiOutcome.Success).provider)
    }

    @Test
    fun `une exception inattendue passe elle aussi au suivant au lieu de faire tomber l'appelant`() = runBlocking {
        // Avant : seule AiException était attrapée, tout le reste remontait jusqu'au plantage.
        val outcome = orchestrator(
            FakeProvider(AiProviderType.CLAUDE) { throw IllegalStateException("format imprévu") },
            FakeProvider(AiProviderType.GEMINI) { throw java.io.IOException("coupure") },
            FakeProvider(AiProviderType.GPT),
        ).analyze(aiInput())

        assertEquals(AiProviderType.GPT, (outcome as AiOutcome.Success).provider)
    }

    @Test
    fun `tous en echec - les motifs sont conserves et l'inattendu compte comme non reseau`() = runBlocking {
        val outcome = orchestrator(
            FakeProvider(AiProviderType.CLAUDE, fails(AiFailureReason.NETWORK)),
            FakeProvider(AiProviderType.GEMINI) { throw IllegalArgumentException("bizarre") },
        ).analyze(aiInput()) as AiOutcome.AllFailed

        assertEquals(
            listOf(AiFailureReason.NETWORK, AiFailureReason.UNKNOWN),
            outcome.failures.map { it.reason },
        )
        // Un réessai différé n'a de sens que si **toutes** les pannes sont du réseau.
        assertFalse(outcome.allNetwork)
    }

    @Test
    fun `que des pannes reseau - la mise en file hors-ligne reste possible`() = runBlocking {
        val outcome = orchestrator(
            FakeProvider(AiProviderType.CLAUDE, fails(AiFailureReason.NETWORK)),
            FakeProvider(AiProviderType.GEMINI, fails(AiFailureReason.NETWORK)),
        ).analyze(aiInput()) as AiOutcome.AllFailed

        assertTrue(outcome.allNetwork)
    }

    @Test
    fun `l'annulation traverse l'orchestrateur sans etre prise pour un echec`() {
        val orchestrator = orchestrator(
            FakeProvider(AiProviderType.CLAUDE) { throw CancellationException("écran quitté") },
            FakeProvider(AiProviderType.GEMINI),
        )

        try {
            runBlocking { orchestrator.analyze(aiInput()) }
            fail("l'annulation devait remonter")
        } catch (e: CancellationException) {
            // attendu : le suivant n'est pas essayé pour un appelant qui a déjà disparu
        }
    }

    @Test
    fun `les questions suivent le meme repli`() = runBlocking {
        val outcome = orchestrator(
            FakeProvider(AiProviderType.CLAUDE) { throw IllegalStateException("format imprévu") },
            FakeProvider(AiProviderType.GEMINI),
        ).ask("q")

        assertEquals("réponse de GEMINI", (outcome as AiAnswerOutcome.Success).answer)
    }

    @Test
    fun `tester une cle - un echec inattendu n'accuse pas la cle`() = runBlocking {
        val orchestrator = orchestrator(
            FakeProvider(AiProviderType.CLAUDE) { throw IllegalStateException("format imprévu") },
            FakeProvider(AiProviderType.GEMINI, fails(AiFailureReason.INVALID_KEY)),
            FakeProvider(AiProviderType.GPT),
        )

        assertEquals(AiFailureReason.UNKNOWN, orchestrator.testKey(AiProviderType.CLAUDE, "k"))
        assertEquals(AiFailureReason.INVALID_KEY, orchestrator.testKey(AiProviderType.GEMINI, "k"))
        assertNull(orchestrator.testKey(AiProviderType.GPT, "k"))
    }
}

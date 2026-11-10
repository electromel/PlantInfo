package ch.electromel.plantinfo.data.remote.ai

import android.util.Log
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import ch.electromel.plantinfo.util.AppLanguage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repli entre fournisseurs IA : ordre configuré, bascule à chaque échec typé, issue typée. */
class AiOrchestratorTest {

    private val keyStore = mockk<ApiKeyStore>()
    private val claude = mockk<ClaudeClient>()
    private val gemini = mockk<GeminiClient>()
    private val openai = mockk<OpenAiClient>()
    private val additional = mockk<AdditionalAiProviders>()

    private lateinit var orchestrator: AiOrchestrator

    private val input = AiAnalysisInput(emptyList(), emptyList(), null, AppLanguage.FRENCH)

    private fun analysis(name: String) = AiAnalysis(
        commonName = name,
        scientificName = name,
        confidence = 80,
        isFungus = false,
        isProtected = false,
        alternatives = emptyList(),
        healthStatus = "",
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
        propagation = emptyList(),
        symbolism = null,
        photoSuggestions = emptyList(),
    )

    @Before
    fun setUp() {
        // android.util.Log n'existe pas en test JVM : l'orchestrateur journalise chaque repli.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { claude.type } returns AiProviderType.CLAUDE
        every { gemini.type } returns AiProviderType.GEMINI
        every { openai.type } returns AiProviderType.GPT
        every { additional.all } returns emptyList()
        every { keyStore.getKey(any()) } answers { "cle-${firstArg<ApiProvider>().name}" }
        orchestrator = AiOrchestrator(keyStore, claude, gemini, openai, additional)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun order(vararg types: AiProviderType) {
        every { keyStore.availableAiProvidersInOrder() } returns types.toList()
    }

    @Test
    fun `aucun fournisseur configure`() = runBlocking {
        order()

        assertEquals(AiOutcome.NoProvidersConfigured, orchestrator.analyze(input))
        assertEquals(AiAnswerOutcome.NoProvidersConfigured, orchestrator.ask("q"))
    }

    @Test
    fun `le premier fournisseur qui repond l'emporte, avec sa propre cle`() = runBlocking {
        order(AiProviderType.GEMINI, AiProviderType.CLAUDE)
        coEvery { gemini.analyze(input, "cle-GEMINI") } returns analysis("Quercus robur")

        val outcome = orchestrator.analyze(input) as AiOutcome.Success

        assertEquals(AiProviderType.GEMINI, outcome.provider)
        coVerify(exactly = 0) { claude.analyze(any(), any()) }
    }

    @Test
    fun `un echec fait basculer au fournisseur suivant`() = runBlocking {
        order(AiProviderType.CLAUDE, AiProviderType.GEMINI)
        coEvery { claude.analyze(any(), any()) } throws AiException(AiFailureReason.BILLING, "sans crédit")
        coEvery { gemini.analyze(any(), any()) } returns analysis("Quercus robur")

        val outcome = orchestrator.analyze(input) as AiOutcome.Success

        assertEquals(AiProviderType.GEMINI, outcome.provider)
    }

    @Test
    fun `tous en echec reseau - la mise en file est pertinente`() = runBlocking {
        order(AiProviderType.CLAUDE, AiProviderType.GEMINI)
        coEvery { claude.analyze(any(), any()) } throws AiException(AiFailureReason.NETWORK, "x")
        coEvery { gemini.analyze(any(), any()) } throws AiException(AiFailureReason.NETWORK, "x")

        val outcome = orchestrator.analyze(input) as AiOutcome.AllFailed

        assertTrue(outcome.allNetwork)
        assertEquals(listOf(AiProviderType.CLAUDE, AiProviderType.GEMINI), outcome.failures.map { it.provider })
    }

    @Test
    fun `un seul echec non reseau suffit a exclure la mise en file`() = runBlocking {
        order(AiProviderType.CLAUDE, AiProviderType.GEMINI)
        coEvery { claude.analyze(any(), any()) } throws AiException(AiFailureReason.NETWORK, "x")
        coEvery { gemini.analyze(any(), any()) } throws AiException(AiFailureReason.QUOTA, "x")

        val outcome = orchestrator.analyze(input) as AiOutcome.AllFailed

        assertFalse(outcome.allNetwork)
        assertEquals(AiFailureReason.QUOTA, outcome.lastReason)
    }

    @Test
    fun `une question suit le meme repli et rapporte la consommation`() = runBlocking {
        order(AiProviderType.CLAUDE, AiProviderType.GPT)
        val usage = TokenUsage("gpt-4o", 10, 20)
        coEvery { claude.ask(any(), any()) } throws AiException(AiFailureReason.INVALID_KEY, "x")
        coEvery { openai.ask("q", "cle-OPENAI") } returns AiAnswer("réponse", usage)

        val outcome = orchestrator.ask("q") as AiAnswerOutcome.Success

        assertEquals("réponse", outcome.answer)
        assertEquals(AiProviderType.GPT, outcome.provider)
        assertEquals(usage, outcome.usage)
    }

    @Test
    fun `une question sans aucun succes rend les motifs par fournisseur`() = runBlocking {
        order(AiProviderType.GEMINI)
        coEvery { gemini.ask(any(), any()) } throws AiException(AiFailureReason.QUOTA, "x")

        val outcome = orchestrator.ask("q") as AiAnswerOutcome.AllFailed

        assertEquals(listOf(ProviderFailure(AiProviderType.GEMINI, AiFailureReason.QUOTA)), outcome.failures)
    }

    @Test
    fun `le test d'une cle rend null si elle marche, sinon le motif`() = runBlocking {
        coEvery { gemini.testKey("bonne") } returns true
        coEvery { gemini.testKey("mauvaise") } throws AiException(AiFailureReason.INVALID_KEY, "x")

        assertNull(orchestrator.testKey(AiProviderType.GEMINI, "bonne"))
        assertEquals(AiFailureReason.INVALID_KEY, orchestrator.testKey(AiProviderType.GEMINI, "mauvaise"))
        // Fournisseur déclaré mais non câblé : la clé n'est pas testable.
        assertEquals(AiFailureReason.UNKNOWN, orchestrator.testKey(AiProviderType.MISTRAL, "x"))
    }

    // --- Défaut connu (audit du 2026-11-10) ---

    @Test
    fun `DEFAUT CONNU F4 - une exception non typee traverse l'orchestrateur sans repli`() {
        order(AiProviderType.CLAUDE, AiProviderType.GEMINI)
        coEvery { claude.analyze(any(), any()) } throws IllegalStateException("réponse illisible")
        coEvery { gemini.analyze(any(), any()) } returns analysis("Quercus robur")

        // Attendu après correction : repli sur Gemini, l'échec de Claude étant classé PARSE.
        val failure = runCatching { runBlocking { orchestrator.analyze(input) } }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        coVerify(exactly = 0) { gemini.analyze(any(), any()) }
    }
}

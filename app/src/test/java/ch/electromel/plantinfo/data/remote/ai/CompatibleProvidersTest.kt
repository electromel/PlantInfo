package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.completeFallbackOrder
import ch.electromel.plantinfo.domain.model.AiPricing
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Câblage des fournisseurs compatibles OpenAI : tarifs, clés, repli, erreurs, jetons. */
class CompatibleProvidersTest {

    private val allConfigs = CompatibleProviderConfig.ADDITIONAL + CompatibleProviderConfig.OPENAI

    @Test
    fun `chaque modele appele a un tarif`() {
        // Sans tarif, la carte « Coût » afficherait les jetons sans montant, et l'assistant n'aurait
        // aucun ordre de grandeur à annoncer.
        allConfigs.forEach { config ->
            assertNotNull(config.model, AiPricing.costUsd(TokenUsage(config.model, 1_000, 1_000)))
            assertNotNull(config.model, AiPricing.typicalIdentificationCostText(config.model))
        }
    }

    @Test
    fun `chaque IA a une cle et un client, et une seule`() {
        val types = AiProviderType.entries.filter { it != AiProviderType.NONE }
        types.forEach { type -> assertNotNull(type.name, ApiProvider.forAiType(type)) }
        val served = listOf(AiProviderType.CLAUDE, AiProviderType.GEMINI) + allConfigs.map { it.type }
        assertEquals(types.toSet(), served.toSet())
        assertEquals(served.size, served.toSet().size)
    }

    @Test
    fun `un ordre de repli enregistre avant l'ajout des fournisseurs est complete a la fin`() {
        val order = completeFallbackOrder("GEMINI,CLAUDE,GPT")

        assertEquals(listOf(AiProviderType.GEMINI, AiProviderType.CLAUDE, AiProviderType.GPT), order.take(3))
        assertEquals(AiProviderType.DEFAULT_FALLBACK_ORDER.toSet(), order.toSet())
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun `un ordre absent ou illisible donne l'ordre par defaut`() {
        assertEquals(AiProviderType.DEFAULT_FALLBACK_ORDER, completeFallbackOrder(null))
        assertEquals(AiProviderType.DEFAULT_FALLBACK_ORDER, completeFallbackOrder("RETIRE,NONE"))
    }

    @Test
    fun `credit epuise est distingue d'une cle refusee`() {
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(402, """{"error":"Insufficient Balance"}"""))
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(403, "Your team doesn't have any credits"))
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(429, "Your account balance is insufficient"))
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(400, """{"code":"Arrearage"}"""))
        assertEquals(AiFailureReason.INVALID_KEY, HttpSupport.reasonForStatus(403, "Forbidden"))
        assertEquals(AiFailureReason.INVALID_KEY, HttpSupport.reasonForStatus(401, "Invalid API key"))
        assertEquals(AiFailureReason.QUOTA, HttpSupport.reasonForStatus(429, "Rate limit reached"))
    }

    @Test
    fun `le raisonnement compte hors completion_tokens est facture en sortie`() {
        val usage = Json.parseToJsonElement(
            """{"prompt_tokens":3000,"completion_tokens":1200,"total_tokens":4700,
               "completion_tokens_details":{"reasoning_tokens":500}}""",
        ).jsonObject

        val result = OpenAiCompatibleClient.usageFromJson("grok-4.3", usage)!!

        assertEquals(3000, result.inputTokens)
        assertEquals(1700, result.outputTokens)
    }

    @Test
    fun `sans total, la sortie vaut completion_tokens`() {
        val usage = Json.parseToJsonElement("""{"prompt_tokens":100,"completion_tokens":40}""").jsonObject

        assertEquals(40, OpenAiCompatibleClient.usageFromJson("gpt-4o", usage)!!.outputTokens)
    }
}

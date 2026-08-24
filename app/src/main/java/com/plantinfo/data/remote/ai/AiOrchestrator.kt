package com.plantinfo.data.remote.ai

import android.util.Log
import com.plantinfo.data.keys.ApiKeyStore
import com.plantinfo.data.keys.ApiProvider
import com.plantinfo.domain.model.AiProviderType
import com.plantinfo.domain.model.TokenUsage
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat de l'orchestration IA, incluant les cas de repli épuisé (§3.1). */
sealed interface AiOutcome {
    data class Success(val analysis: AiAnalysis, val provider: AiProviderType) : AiOutcome

    /** Aucune clé IA configurée → l'appelant affiche le résultat Pl@ntNet brut. */
    data object NoProvidersConfigured : AiOutcome

    /**
     * Tous les fournisseurs configurés ont échoué. [failures] porte le motif de chaque fournisseur
     * essayé (pour des messages honnêtes) ; [allNetwork] indique qu'un réessai différé (file
     * d'attente) est pertinent.
     */
    data class AllFailed(val failures: List<ProviderFailure>, val allNetwork: Boolean) : AiOutcome {
        val lastReason: AiFailureReason
            get() = failures.lastOrNull()?.reason ?: AiFailureReason.UNKNOWN
    }
}

/** Résultat d'une question libre posée à l'IA sur une plante identifiée (§Q&A). */
sealed interface AiAnswerOutcome {
    data class Success(
        val answer: String,
        val provider: AiProviderType,
        /** Jetons consommés par la question, ou null si le fournisseur n'en rapporte pas. */
        val usage: TokenUsage?,
    ) : AiAnswerOutcome
    data object NoProvidersConfigured : AiAnswerOutcome
    data class AllFailed(val failures: List<ProviderFailure>) : AiAnswerOutcome
}

/**
 * Applique la logique de repli entre fournisseurs IA : parcourt l'ordre de priorité configuré,
 * ne retient que les fournisseurs disposant d'une clé, et bascule au suivant à chaque échec
 * (clé invalide, quota, réseau, serveur). Renvoie le premier succès, ou un état d'échec typé.
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val keyStore: ApiKeyStore,
    claude: ClaudeClient,
    gemini: GeminiClient,
    openai: OpenAiClient,
) {
    private val providers: Map<AiProviderType, AiProvider> = mapOf(
        AiProviderType.CLAUDE to claude,
        AiProviderType.GEMINI to gemini,
        AiProviderType.GPT to openai,
    )

    suspend fun analyze(input: AiAnalysisInput): AiOutcome {
        val order = keyStore.availableAiProvidersInOrder()
        if (order.isEmpty()) return AiOutcome.NoProvidersConfigured

        val failures = mutableListOf<ProviderFailure>()
        var allNetwork = true
        for (type in order) {
            val provider = providers[type] ?: continue
            val apiProvider = ApiProvider.forAiType(type) ?: continue
            val key = keyStore.getKey(apiProvider) ?: continue
            try {
                val analysis = provider.analyze(input, key)
                return AiOutcome.Success(analysis, type)
            } catch (e: AiException) {
                Log.w(TAG, "Fournisseur ${type.label} en échec : ${e.reason} — ${e.message}")
                failures += ProviderFailure(type, e.reason)
                if (e.reason != AiFailureReason.NETWORK) allNetwork = false
                // On passe au fournisseur suivant quel que soit le motif.
            }
        }
        return AiOutcome.AllFailed(failures, allNetwork)
    }

    /**
     * Répond à une question libre en réutilisant l'ordre de repli configuré. Contrairement à
     * l'analyse, un échec réseau ne met rien en file : la question est interactive et ré-essayable.
     */
    suspend fun ask(prompt: String): AiAnswerOutcome {
        val order = keyStore.availableAiProvidersInOrder()
        if (order.isEmpty()) return AiAnswerOutcome.NoProvidersConfigured

        val failures = mutableListOf<ProviderFailure>()
        for (type in order) {
            val provider = providers[type] ?: continue
            val apiProvider = ApiProvider.forAiType(type) ?: continue
            val key = keyStore.getKey(apiProvider) ?: continue
            try {
                val answer = provider.ask(prompt, key)
                return AiAnswerOutcome.Success(answer.text, type, answer.usage)
            } catch (e: AiException) {
                Log.w(TAG, "Question IA — ${type.label} en échec : ${e.reason} — ${e.message}")
                failures += ProviderFailure(type, e.reason)
            }
        }
        return AiAnswerOutcome.AllFailed(failures)
    }

    /** Teste une clé pour l'écran Paramètres. Renvoie null si OK, sinon le motif d'échec. */
    suspend fun testKey(type: AiProviderType, apiKey: String): AiFailureReason? {
        val provider = providers[type] ?: return AiFailureReason.UNKNOWN
        return try {
            provider.testKey(apiKey)
            null
        } catch (e: AiException) {
            e.reason
        }
    }

    private companion object {
        const val TAG = "AiOrchestrator"
    }
}

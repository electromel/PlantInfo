package ch.electromel.plantinfo.data.remote.ai

import android.util.Log
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import kotlinx.coroutines.CancellationException
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
    additional: AdditionalAiProviders,
) {
    private val providers: Map<AiProviderType, AiProvider> =
        (listOf(claude, gemini, openai) + additional.all).associateBy { it.type }

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
            } catch (e: CancellationException) {
                throw e // annulation coopérative : ce n'est pas un échec du fournisseur
            } catch (e: Exception) {
                val reason = e.failureReason()
                Log.w(TAG, "Fournisseur ${type.label} en échec : $reason — ${e.message}")
                failures += ProviderFailure(type, reason)
                if (reason != AiFailureReason.NETWORK) allNetwork = false
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.failureReason()
                Log.w(TAG, "Question IA — ${type.label} en échec : $reason — ${e.message}")
                failures += ProviderFailure(type, reason)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.failureReason(fallback = AiFailureReason.UNKNOWN)
        }
    }

    /**
     * Motif d'un échec de fournisseur. Une exception qui n'est pas une [AiException] (réponse au
     * format inattendu, requête impossible à construire) ne doit jamais remonter jusqu'à l'écran :
     * elle compte comme un échec de ce fournisseur, et le repli continue.
     */
    private fun Exception.failureReason(fallback: AiFailureReason = AiFailureReason.PARSE): AiFailureReason =
        (this as? AiException)?.reason ?: fallback

    private companion object {
        const val TAG = "AiOrchestrator"
    }
}

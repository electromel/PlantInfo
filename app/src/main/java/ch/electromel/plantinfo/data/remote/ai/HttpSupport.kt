package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.data.remote.fetch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Base64

/** Utilitaires partagés par les clients IA : mapping des erreurs HTTP et exécution des appels. */
internal object HttpSupport {

    const val JSON_MEDIA = "application/json; charset=utf-8"

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Traduit un code HTTP (et le corps d'erreur) en motif de repli typé. */
    fun reasonForStatus(code: Int, body: String = ""): AiFailureReason = when {
        // Solde/crédit épuisé côté compte, distinct d'une clé refusée ou d'une limite de débit :
        // Anthropic répond 400 « credit balance is too low », OpenAI 429 « insufficient_quota »,
        // DeepSeek et OpenRouter 402, xAI 403 « …credits… », Moonshot 429 « …balance… »,
        // Alibaba 400 « Arrearage ». Testé avant 401/403 : un 403 sans crédit n'est pas une clé fausse.
        code == 402 -> AiFailureReason.BILLING
        code == 403 && body.contains("credits", ignoreCase = true) -> AiFailureReason.BILLING
        code == 400 && body.contains("credit balance", ignoreCase = true) -> AiFailureReason.BILLING
        code == 400 && body.contains("Arrearage", ignoreCase = true) -> AiFailureReason.BILLING
        code == 429 && body.contains("insufficient_quota", ignoreCase = true) -> AiFailureReason.BILLING
        code == 429 && body.contains("balance", ignoreCase = true) -> AiFailureReason.BILLING
        code == 401 || code == 403 -> AiFailureReason.INVALID_KEY
        code == 429 -> AiFailureReason.QUOTA
        code in 500..599 -> AiFailureReason.SERVER
        else -> AiFailureReason.UNKNOWN
    }

    /**
     * Exécute une requête et renvoie le corps en texte si 2xx ; sinon lève AiException avec le motif
     * adéquat. Les erreurs réseau (timeout, pas de connexion, coupure pendant la lecture du corps)
     * deviennent AiFailureReason.NETWORK : le repli, la file hors-ligne et les messages ne doivent
     * jamais recevoir une exception brute.
     */
    suspend fun execute(client: OkHttpClient, request: Request, providerLabel: String): String {
        val reply = try {
            client.fetch(request)
        } catch (e: IOException) {
            throw AiException(AiFailureReason.NETWORK, "$providerLabel : erreur réseau", e)
        }
        if (!reply.isSuccessful) {
            throw AiException(
                reasonForStatus(reply.code, reply.body),
                "$providerLabel : HTTP ${reply.code} — ${reply.body.take(300)}",
            )
        }
        return reply.body
    }

    /**
     * Lit la réponse d'un fournisseur. Un format inattendu (page de portail captif en 200, `content`
     * d'une forme que le client ne connaît pas…) fait lever `IllegalArgumentException` ou
     * `SerializationException` aux accesseurs JSON : ces exceptions-là ne sont pas typées pour le
     * repli. On les convertit donc ici en [AiFailureReason.PARSE], qui fait passer au fournisseur
     * suivant au lieu de faire tomber l'application.
     */
    fun <T> parseResponse(providerLabel: String, block: () -> T): T = try {
        block()
    } catch (e: AiException) {
        throw e
    } catch (e: Exception) {
        throw AiException(AiFailureReason.PARSE, "$providerLabel : réponse inattendue", e)
    }
}

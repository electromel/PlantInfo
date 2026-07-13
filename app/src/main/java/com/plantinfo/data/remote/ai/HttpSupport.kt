package com.plantinfo.data.remote.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Utilitaires partagés par les clients IA : mapping des erreurs HTTP et exécution des appels. */
internal object HttpSupport {

    const val JSON_MEDIA = "application/json; charset=utf-8"

    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Traduit un code HTTP (et le corps d'erreur) en motif de repli typé. */
    fun reasonForStatus(code: Int, body: String = ""): AiFailureReason = when {
        code == 401 || code == 403 -> AiFailureReason.INVALID_KEY
        // Solde/crédit épuisé côté compte, distinct d'une simple limite de débit :
        // Anthropic répond 400 « credit balance is too low », OpenAI 429 « insufficient_quota ».
        code == 400 && body.contains("credit balance", ignoreCase = true) -> AiFailureReason.BILLING
        code == 429 && body.contains("insufficient_quota", ignoreCase = true) -> AiFailureReason.BILLING
        code == 429 -> AiFailureReason.QUOTA
        code in 500..599 -> AiFailureReason.SERVER
        else -> AiFailureReason.UNKNOWN
    }

    /**
     * Exécute une requête et renvoie le corps en texte si 2xx ; sinon lève AiException avec le motif
     * adéquat. Les erreurs réseau (timeout, pas de connexion) deviennent AiFailureReason.NETWORK.
     */
    suspend fun execute(client: OkHttpClient, request: Request, providerLabel: String): String =
        withContext(Dispatchers.IO) {
            val response: Response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw AiException(AiFailureReason.NETWORK, "$providerLabel : erreur réseau", e)
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw AiException(
                        reasonForStatus(it.code, body),
                        "$providerLabel : HTTP ${it.code} — ${body.take(300)}",
                    )
                }
                body
            }
        }
}

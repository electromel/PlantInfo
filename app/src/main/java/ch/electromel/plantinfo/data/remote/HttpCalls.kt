package ch.electromel.plantinfo.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Réponse HTTP entièrement lue : le code et le corps (vide si le serveur n'en a pas envoyé). */
internal data class HttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Exécute [request] et rend la réponse **entièrement lue**.
 *
 * Deux garanties que `newCall(request).execute()` ne donne pas :
 * - **toute** panne réseau est une [IOException], y compris celle qui survient pendant la lecture
 *   du corps (coupure à mi-réponse, délai de lecture dépassé) — l'appelant n'a qu'un cas à traiter ;
 * - l'appel est **annulé** avec la coroutine : quand l'utilisateur quitte l'écran, la requête s'arrête
 *   au lieu de se poursuivre — et d'être facturée par le fournisseur — pour rien.
 */
internal suspend fun OkHttpClient.fetch(request: Request): HttpReply =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val reply = try {
                    response.use { HttpReply(it.code, it.body?.string().orEmpty()) }
                } catch (e: Exception) {
                    // IOException le plus souvent ; tout autre échec de lecture est traité de même
                    // plutôt que de remonter sur le thread du dispatcher OkHttp.
                    if (continuation.isActive) {
                        continuation.resumeWithException(e as? IOException ?: IOException(e))
                    }
                    return
                }
                if (continuation.isActive) continuation.resume(reply)
            }
        })
    }

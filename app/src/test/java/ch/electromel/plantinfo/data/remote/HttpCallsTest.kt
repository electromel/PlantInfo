package ch.electromel.plantinfo.data.remote

import ch.electromel.plantinfo.FakeHttp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class HttpCallsTest {

    private val request = Request.Builder().url("https://example.test/").build()

    @Test
    fun `une reponse complete rend le code et le corps`() = runBlocking {
        val reply = FakeHttp.client { FakeHttp.json(it, """{"ok":true}""", code = 201) }.fetch(request)

        assertEquals(201, reply.code)
        assertEquals("""{"ok":true}""", reply.body)
        assertTrue(reply.isSuccessful)
    }

    @Test
    fun `un code d'erreur n'est pas une exception, l'appelant decide`() = runBlocking {
        val reply = FakeHttp.client { FakeHttp.json(it, "nope", code = 429) }.fetch(request)

        assertEquals(429, reply.code)
        assertTrue(!reply.isSuccessful)
    }

    @Test
    fun `une coupure pendant la lecture du corps est une IOException`() {
        try {
            runBlocking { FakeHttp.client { FakeHttp.brokenBody(it) }.fetch(request) }
            fail("une IOException était attendue")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("reset"))
        }
    }

    @Test
    fun `annuler la coroutine annule vraiment l'appel HTTP`() = runBlocking {
        val inFlight = CompletableDeferred<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            inFlight.complete(chain.call())
            // Serveur muet : la requête reste en vol jusqu'à son annulation (ou 5 s au pire).
            val deadline = System.currentTimeMillis() + 5_000
            while (!chain.call().isCanceled() && System.currentTimeMillis() < deadline) Thread.sleep(10)
            throw IOException("annulé ou trop lent")
        }.build()

        val job = launch(Dispatchers.Default) { client.fetch(request) }
        val call = withTimeout(2_000) { inFlight.await() }
        assertFalse(call.isCanceled())

        job.cancel()
        withTimeout(2_000) { job.join() }

        // C'est tout l'objet : sans cela la requête continue, et le fournisseur la facture, alors
        // que plus personne n'attend la réponse.
        val deadline = System.currentTimeMillis() + 2_000
        while (!call.isCanceled() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("l'appel OkHttp devait être annulé avec la coroutine", call.isCanceled())
        assertTrue(job.isCancelled)
    }
}

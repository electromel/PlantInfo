package ch.electromel.plantinfo.data.remote.plantnet

import ch.electromel.plantinfo.FakeHttp
import ch.electromel.plantinfo.data.remote.LogRedactor
import ch.electromel.plantinfo.data.remote.ai.AiImage
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlantNetClientTest {

    private val photo = listOf(AiImage(byteArrayOf(1, 2, 3), "image/jpeg"))
    private val oneResult = """
        {"results":[{"score":0.87,"species":{"scientificNameWithoutAuthor":"Rosa canina","commonNames":["Eglantier"]},
         "gbif":{"id":"3004591"},"iucn":{"category":"LC"}}]}
    """.trimIndent()

    private fun identify(client: PlantNetClient, language: String = "fr", key: String = "secret") =
        runBlocking { client.identify(photo, listOf("leaf"), key, language) }

    @Test
    fun `la langue de l'application est transmise, pas un francais en dur`() {
        val seen = mutableListOf<Request>()
        val client = PlantNetClient(FakeHttp.client { seen += it; FakeHttp.json(it, oneResult) })

        listOf("fr", "en", "de", "it", "es").forEach { identify(client, language = it) }

        assertEquals(
            listOf("fr", "en", "de", "it", "es"),
            seen.map { it.url.queryParameter("lang") },
        )
    }

    @Test
    fun `une cle avec des caracteres reserves ne casse ni ne detourne la requete`() {
        lateinit var request: Request
        val client = PlantNetClient(FakeHttp.client { request = it; FakeHttp.json(it, oneResult) })

        identify(client, key = "ab&nb-results=999#x y")

        // La clé reste UNE valeur de api-key : elle n'a ni modifié nb-results ni coupé l'URL.
        assertEquals("ab&nb-results=999#x y", request.url.queryParameter("api-key"))
        assertEquals("5", request.url.queryParameter("nb-results"))
    }

    @Test
    fun `l'URL encodee reste expurgee des logs`() {
        lateinit var request: Request
        val client = PlantNetClient(FakeHttp.client { request = it; FakeHttp.json(it, oneResult) })

        identify(client, key = "cle-secrete")

        val logged = LogRedactor.redact("--> POST ${request.url}")
        assertFalse(logged.contains("cle-secrete"))
    }

    @Test
    fun `les candidats sont lus avec leur score et leurs metadonnees`() {
        val client = PlantNetClient(FakeHttp.client { FakeHttp.json(it, oneResult) })

        val result = identify(client)

        assertNull(result.error)
        val top = result.candidates.single()
        assertEquals("Rosa canina", top.scientificName)
        assertEquals("Eglantier", top.commonName)
        assertEquals(87, top.score)
        assertEquals(3004591L, top.gbifKey)
        assertEquals("LC", top.iucnCategory)
    }

    @Test
    fun `chaque code d'erreur a son motif`() {
        val expected = mapOf(
            401 to PlantNetError.INVALID_KEY, 403 to PlantNetError.INVALID_KEY,
            404 to PlantNetError.NO_MATCH, 429 to PlantNetError.QUOTA,
            500 to PlantNetError.SERVER, 418 to PlantNetError.UNKNOWN,
        )
        expected.forEach { (code, error) ->
            val client = PlantNetClient(FakeHttp.client { FakeHttp.json(it, "{}", code = code) })
            assertEquals("HTTP $code", error, identify(client).error)
        }
    }

    @Test
    fun `une coupure en cours de lecture est une panne reseau, pas un plantage`() {
        val client = PlantNetClient(FakeHttp.client { FakeHttp.brokenBody(it) })
        assertEquals(PlantNetError.NETWORK, identify(client).error)

        val offline = PlantNetClient(FakeHttp.client { throw IOException("pas de réseau") })
        assertEquals(PlantNetError.NETWORK, identify(offline).error)
    }

    @Test
    fun `une reponse illisible est une erreur inconnue, pas un plantage`() {
        val client = PlantNetClient(FakeHttp.client { FakeHttp.html(it, "<html>portail</html>") })

        val result = identify(client)

        assertEquals(PlantNetError.UNKNOWN, result.error)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `sans resultat l'erreur est NO_MATCH`() {
        val client = PlantNetClient(FakeHttp.client { FakeHttp.json(it, """{"results":[]}""") })

        assertEquals(PlantNetError.NO_MATCH, identify(client).error)
    }

    @Test
    fun `tester une cle interprete le code HTTP`() {
        fun verdict(code: Int) = runBlocking {
            PlantNetClient(FakeHttp.client { FakeHttp.json(it, "{}", code = code) }).testKey("k")
        }

        assertEquals(PlantNetError.INVALID_KEY, verdict(401))
        assertEquals(PlantNetError.QUOTA, verdict(429))
        assertEquals(PlantNetError.SERVER, verdict(502))
        assertNull(verdict(400)) // clé acceptée, requête incomplète
        val offline = runBlocking {
            PlantNetClient(FakeHttp.client { throw IOException("hors ligne") }).testKey("k")
        }
        assertEquals(PlantNetError.NETWORK, offline)
    }
}

package ch.electromel.plantinfo.data.remote.web

import ch.electromel.plantinfo.util.AppLanguage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Recherche d'une photo sur Wikipédia pour une fiche « par nom », sans réseau. */
class WebPhotoClientTest {

    private val requested = mutableListOf<String>()

    /** Sert [search] aux appels de recherche (par langue) et [image] aux téléchargements. */
    private fun client(search: (lang: String) -> String, image: ByteArray = byteArrayOf(1, 2, 3)) =
        WebPhotoClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url
                requested += url.toString()
                val body = if (url.encodedPath.endsWith("api.php")) {
                    search(url.host.substringBefore('.')).toResponseBody()
                } else {
                    image.toResponseBody()
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("").body(body).build()
            }.build(),
        )

    private fun pages(vararg entries: String) = """{"query":{"pages":{${entries.joinToString(",")}}}}"""

    private fun page(id: Int, title: String, index: Int, source: String?) =
        """"$id":{"title":"$title","index":$index""" +
            (source?.let { ""","thumbnail":{"source":"$it"}""" } ?: "") + "}"

    @Test
    fun `la premiere page pertinente qui a une vraie photo est retenue`() = runBlocking {
        val json = pages(
            page(3, "Chêne pédonculé", 3, "https://upload.example/chene.jpg"),
            page(1, "Chêne (homonymie)", 1, null),
            page(2, "Répartition du chêne", 2, "https://upload.example/carte.svg.png"),
        )

        val photo = client({ json }).findPhoto("chêne", AppLanguage.FRENCH)

        assertEquals("Chêne pédonculé", photo?.articleTitle)
        assertArrayEquals(byteArrayOf(1, 2, 3), photo?.bytes)
        assertTrue(requested.first().startsWith("https://fr.wikipedia.org/"))
    }

    @Test
    fun `sans image dans la langue de l'app, l'edition anglaise est interrogee`() = runBlocking {
        val photo = client({ lang ->
            if (lang == "en") pages(page(1, "Oak", 1, "https://upload.example/oak.jpg")) else """{"batchcomplete":""}"""
        }).findPhoto("chêne", AppLanguage.FRENCH)

        assertEquals("Oak", photo?.articleTitle)
    }

    @Test
    fun `aucune edition n'a d'image - null, sans exception`() = runBlocking {
        assertNull(client({ """{"batchcomplete":""}""" }).findPhoto("zzz", AppLanguage.ENGLISH))
    }

    @Test
    fun `F7 - une reponse 200 qui n'est pas du JSON est un probleme de reseau`() {
        val failure = runCatching {
            runBlocking { client({ "<html>Portail captif</html>" }).findPhoto("chêne", AppLanguage.FRENCH) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }
}

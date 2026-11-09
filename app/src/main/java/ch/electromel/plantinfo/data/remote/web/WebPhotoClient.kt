package ch.electromel.plantinfo.data.remote.web

import ch.electromel.plantinfo.data.remote.ai.awaitResponse
import ch.electromel.plantinfo.util.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cherche sur le web une photo d'illustration pour un nom de plante saisi à la main.
 *
 * Source : Wikipédia (image principale de l'article, hébergée sur Wikimedia Commons, donc sous
 * licence libre), sans clé d'API. On interroge d'abord Wikipédia dans la langue de l'application,
 * puis en anglais, la plus complète. Seul le texte saisi quitte l'appareil, jamais une donnée sur
 * l'utilisateur.
 */
@Singleton
class WebPhotoClient @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Une photo trouvée : octets de l'image et titre de l'article dont elle est tirée. */
    class WebPhoto(val bytes: ByteArray, val articleTitle: String)

    /**
     * @return la photo, ou null si aucune édition de Wikipédia n'a d'image pour ce nom.
     * @throws IOException en cas de problème réseau (l'appelant distingue ainsi « hors-ligne » de
     *   « introuvable »).
     */
    suspend fun findPhoto(name: String, language: AppLanguage): WebPhoto? =
        withContext(Dispatchers.IO) {
            for (lang in listOf(language.tag, "en").distinct()) {
                val hit = searchImage(name, lang) ?: continue
                val bytes = download(hit.imageUrl) ?: continue
                return@withContext WebPhoto(bytes, hit.title)
            }
            null
        }

    private class Hit(val title: String, val imageUrl: String)

    private suspend fun searchImage(name: String, lang: String): Hit? {
        val url = "https://$lang.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", name)
            .addQueryParameter("gsrlimit", SEARCH_LIMIT.toString())
            .addQueryParameter("prop", "pageimages")
            .addQueryParameter("piprop", "thumbnail")
            .addQueryParameter("pithumbsize", THUMB_SIZE.toString())
            .build()
        val body = get(url.toString())?.string() ?: return null
        val pages = json.decodeFromString<QueryDto>(body).query?.pages?.values.orEmpty()
        // `index` donne le rang de pertinence de la recherche ; les pages sans image sont écartées,
        // ainsi que les SVG (cartes de répartition, schémas) rendus en PNG.
        return pages.sortedBy { it.index ?: Int.MAX_VALUE }
            .firstNotNullOfOrNull { page ->
                val src = page.thumbnail?.source ?: return@firstNotNullOfOrNull null
                if (src.contains(".svg", ignoreCase = true)) return@firstNotNullOfOrNull null
                Hit(page.title.orEmpty(), src)
            }
    }

    private suspend fun download(url: String): ByteArray? {
        val body = get(url) ?: return null
        val bytes = body.bytes()
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
    }

    /** GET ; null si le serveur répond une erreur HTTP (page absente, limite de débit…). */
    private suspend fun get(url: String): okhttp3.ResponseBody? {
        // Wikimedia exige un User-Agent qui identifie l'application — sans coordonnées personnelles.
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = client.newCall(request).awaitResponse()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response.body ?: run { response.close(); null }
    }

    @Serializable
    private data class QueryDto(val query: PagesDto? = null)

    @Serializable
    private data class PagesDto(val pages: Map<String, PageDto>? = null)

    @Serializable
    private data class PageDto(
        val title: String? = null,
        val index: Int? = null,
        val thumbnail: ThumbDto? = null,
    )

    @Serializable
    private data class ThumbDto(val source: String? = null)

    private companion object {
        const val SEARCH_LIMIT = 5
        const val THUMB_SIZE = 1200
        const val MAX_BYTES = 8 * 1024 * 1024
        const val USER_AGENT = "PlantInfo-Android (plant identification app)"
    }
}

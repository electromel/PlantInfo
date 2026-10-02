package ch.electromel.plantinfo.data.remote.gbif

import ch.electromel.plantinfo.FakeHttp
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbifClientTest {

    // --- Quelle clé GBIF retenir ---

    @Test
    fun `une correspondance exacte a l'espece est retenue`() {
        assertEquals(2927305L, GbifClient.acceptedUsageKey(2927305, "EXACT", "SPECIES"))
        assertEquals(1L, GbifClient.acceptedUsageKey(1, "FUZZY", "SUBSPECIES"))
        assertEquals(1L, GbifClient.acceptedUsageKey(1, "EXACT", "VARIETY"))
    }

    @Test
    fun `un nom inconnu qui retombe sur le genre n'est pas pris pour l'espece`() {
        // Réponse réelle de GBIF pour « Lavandula inventus » : la clé du genre, HIGHERRANK.
        assertNull(GbifClient.acceptedUsageKey(2927302, "HIGHERRANK", "GENUS"))
    }

    @Test
    fun `un rang superieur a l'espece est refuse meme en correspondance exacte`() {
        assertNull(GbifClient.acceptedUsageKey(2927302, "EXACT", "GENUS"))
        assertNull(GbifClient.acceptedUsageKey(5, "EXACT", "FAMILY"))
    }

    @Test
    fun `aucune cle ou aucune correspondance donne null`() {
        assertNull(GbifClient.acceptedUsageKey(null, "EXACT", "SPECIES"))
        assertNull(GbifClient.acceptedUsageKey(1, "NONE", null))
        assertNull(GbifClient.acceptedUsageKey(1, null, "SPECIES"))
    }

    // --- Requêtes émises ---

    @Test
    fun `les occurrences cultivees ou fossiles sont exclues de l'aire`() {
        val urls = mutableListOf<Request>()
        val client = GbifClient(
            FakeHttp.client {
                urls += it
                FakeHttp.json(it, """{"results":[{"decimalLatitude":46.2,"decimalLongitude":6.1}]}""")
            },
        )

        val range = runBlocking { client.fetchRange("Lavandula angustifolia", gbifKey = 2927305) }

        assertTrue(range.hasData)
        val query = urls.single().url
        assertEquals("PRESENT", query.queryParameter("occurrenceStatus"))
        val basis = query.queryParameterValues("basisOfRecord")
        assertTrue(basis.contains("HUMAN_OBSERVATION"))
        assertFalse("les spécimens vivants (jardins) faussent l'aire", basis.contains("LIVING_SPECIMEN"))
        assertFalse(basis.contains("FOSSIL_SPECIMEN"))
        assertEquals("2927305", query.queryParameter("taxonKey"))
    }

    @Test
    fun `un nom retombant sur le genre ne declenche aucune requete d'occurrences`() {
        val urls = mutableListOf<Request>()
        val client = GbifClient(
            FakeHttp.client {
                urls += it
                FakeHttp.json(it, """{"usageKey":2927302,"matchType":"HIGHERRANK","rank":"GENUS"}""")
            },
        )

        val range = runBlocking { client.fetchRange("Lavandula inventus") }

        assertFalse(range.hasData)
        assertEquals("seule la résolution du nom est demandée", 1, urls.size)
        assertTrue(urls.single().url.encodedPath.endsWith("/species/match"))
    }
}

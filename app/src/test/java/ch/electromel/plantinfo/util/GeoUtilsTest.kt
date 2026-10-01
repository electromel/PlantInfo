package ch.electromel.plantinfo.util

import ch.electromel.plantinfo.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cadrage initial de la carte : le lieu de prise de vue et les occurrences voisines, pas la planète. */
class GeoUtilsTest {

    private val grenoble = LatLng(45.19, 5.72)
    private val madrid = LatLng(40.42, -3.70)       // ~1 000 km
    private val rome = LatLng(41.90, 12.50)         // ~ 900 km
    private val sydney = LatLng(-33.87, 151.21)     // ~ 16 000 km
    private val santiago = LatLng(-33.45, -70.67)   // ~ 11 000 km

    @Test
    fun `distance - Paris Londres vaut environ 344 km`() {
        val d = GeoUtils.distanceKm(LatLng(48.8566, 2.3522), LatLng(51.5074, -0.1278))
        assertEquals(344.0, d, 5.0)
    }

    @Test
    fun `distance - un point a lui-meme vaut zero`() {
        assertEquals(0.0, GeoUtils.distanceKm(grenoble, grenoble), 1e-9)
    }

    @Test
    fun `espece naturalisee - les occurrences lointaines sont laissees hors du cadrage`() {
        val framed = GeoUtils.framingPoints(grenoble, listOf(madrid, rome, sydney, santiago))

        assertTrue(madrid in framed && rome in framed)
        assertFalse(sydney in framed || santiago in framed)
        assertTrue("le lieu de prise de vue est toujours cadre", grenoble in framed)
    }

    @Test
    fun `aucune occurrence proche - on garde tout pour voir les deux malgre la distance`() {
        val framed = GeoUtils.framingPoints(grenoble, listOf(sydney, santiago))

        assertEquals(setOf(sydney, santiago, grenoble), framed.toSet())
    }

    @Test
    fun `sans lieu de prise de vue - toutes les occurrences sont cadrees`() {
        val all = listOf(madrid, sydney)

        assertEquals(all, GeoUtils.framingPoints(null, all))
    }

    @Test
    fun `aucune occurrence - seul le lieu de prise de vue est cadre`() {
        assertEquals(listOf(grenoble), GeoUtils.framingPoints(grenoble, emptyList()))
    }
}

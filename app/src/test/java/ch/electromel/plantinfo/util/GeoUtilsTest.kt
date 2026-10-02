package ch.electromel.plantinfo.util

import ch.electromel.plantinfo.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GeoUtilsTest {

    @Test
    fun `un lieu envoye a un tiers est arrondi a environ un kilometre`() {
        assertEquals("46.12, 6.15", GeoUtils.approximateCoordinates(46.123456, 6.149876))
        assertEquals("-33.87, 151.21", GeoUtils.approximateCoordinates(-33.8688, 151.2093))
    }

    @Test
    fun `le point decimal ne depend pas de la langue du telephone`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("46.12, 6.15", GeoUtils.approximateCoordinates(46.123456, 6.149876))
            assertEquals("46.12346, 6.14988", GeoUtils.shareableCoordinates(46.123456, 6.149876, protectedSpecies = false))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `une fiche partagee garde la precision complete, sauf pour une espece protegee`() {
        assertEquals("46.12346, 6.14988", GeoUtils.shareableCoordinates(46.123456, 6.149876, protectedSpecies = false))
        assertEquals("46.12, 6.15", GeoUtils.shareableCoordinates(46.123456, 6.149876, protectedSpecies = true))
    }

    @Test
    fun `l'arrondi ne livre pas la position exacte`() {
        val exact = 46.123456
        val sent = GeoUtils.approximateCoordinates(exact, 6.0).substringBefore(",")
        assertTrue(sent.length < exact.toString().length)
        // 2 décimales de latitude ≈ 1,1 km : l'erreur peut atteindre 0,005° mais jamais le mètre près.
        assertTrue(Math.abs(sent.toDouble() - exact) <= 0.005 + 1e-9)
    }

    @Test
    fun `l'enveloppe convexe d'un carre avec un point interieur ignore le point interieur`() {
        val hull = GeoUtils.convexHull(
            listOf(LatLng(0.0, 0.0), LatLng(0.0, 2.0), LatLng(2.0, 2.0), LatLng(2.0, 0.0), LatLng(1.0, 1.0)),
        )

        assertEquals(4, hull.size)
    }

    @Test
    fun `moins de trois points distincts ne font pas de polygone`() {
        assertTrue(GeoUtils.convexHull(listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.0), LatLng(1.0, 1.0))).isEmpty())
    }
}

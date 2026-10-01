package ch.electromel.plantinfo.data.remote.gbif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les tuiles GBIF sont demandées par espèce, en hexagones, dans le référentiel de la carte de fond. */
class GbifDensityTileSourceTest {

    @Test
    fun `url - cle taxonomique, position de tuile et style hexagonal vert`() {
        val url = GbifDensityTileSource.tileUrl(taxonKey = 5355395L, zoom = 4, x = 8, y = 5)

        assertEquals(
            "https://api.gbif.org/v2/map/occurrence/density/4/8/5@Hx.png" +
                "?taxonKey=5355395&srs=EPSG:3857&bin=hex&hexPerTile=10&style=green.poly",
            url,
        )
    }

    @Test
    fun `url - meme referentiel que OpenStreetMap, pour que les tuiles s'alignent sur le fond`() {
        // EPSG:3857 (Web Mercator) : un autre référentiel décalerait les hexagones sur la carte.
        assertTrue("srs=EPSG:3857" in GbifDensityTileSource.tileUrl(1L, 0, 0, 0))
    }

    @Test
    fun `tuiles de 256 px comme le fond de carte - le suffixe Hx, pas 1x qui sert du 512 px`() {
        assertEquals(256, GbifDensityTileSource.TILE_SIZE_PX)
        assertTrue("@Hx.png" in GbifDensityTileSource.tileUrl(1L, 0, 0, 0))
    }

    @Test
    fun `chaque espece a sa propre source - sinon le cache disque melangerait les especes`() {
        assertEquals("gbif-density-1", GbifDensityTileSource(1L).name())
        assertEquals("gbif-density-2", GbifDensityTileSource(2L).name())
    }
}

package ch.electromel.plantinfo.data.remote.gbif

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Tuiles de densité des occurrences GBIF d'une espèce (API Maps v2), à superposer à la carte de fond.
 *
 * C'est GBIF qui agrège **toutes** ses observations de l'espèce en hexagones et ne dessine que ceux
 * qui en contiennent : la zone ne couvre donc que les endroits où la plante a réellement été vue,
 * là où une enveloppe tracée autour d'un échantillon de 300 points débordait sur les océans.
 *
 * Une tuile sans aucune observation répond `204 No Content` ; osmdroid la laisse transparente.
 */
class GbifDensityTileSource(private val taxonKey: Long) : OnlineTileSourceBase(
    // Le nom indexe le cache disque d'osmdroid : il doit changer d'espèce en espèce, sans quoi les
    // tuiles de l'une seraient resservies pour l'autre.
    "gbif-density-$taxonKey",
    MIN_ZOOM,
    MAX_ZOOM,
    TILE_SIZE_PX,
    ".png",
    arrayOf(BASE_URL),
) {
    override fun getTileURLString(pMapTileIndex: Long): String = tileUrl(
        taxonKey = taxonKey,
        zoom = MapTileIndex.getZoom(pMapTileIndex),
        x = MapTileIndex.getX(pMapTileIndex),
        y = MapTileIndex.getY(pMapTileIndex),
    )

    companion object {
        const val BASE_URL = "https://api.gbif.org/v2/map/occurrence/density/"
        const val MIN_ZOOM = 0

        /** Au-delà, GBIF ne renvoie plus de tuile utile ; osmdroid agrandit la tuile du zoom 14. */
        const val MAX_ZOOM = 14

        /**
         * Taille des tuiles, la même que le fond de carte. Le suffixe `@Hx` de l'URL demande à GBIF du
         * 256 px (son `@1x` sert du 512 px) : osmdroid décode chaque tuile dans un tampon de cette
         * taille et n'affiche rien d'une image plus grande, sans jamais le signaler.
         */
        const val TILE_SIZE_PX = 256

        /** Nombre d'hexagones sur la largeur d'une tuile : assez gros pour rester lisibles. */
        const val HEXES_PER_TILE = 10

        fun tileUrl(taxonKey: Long, zoom: Int, x: Int, y: Int): String =
            "$BASE_URL$zoom/$x/$y@Hx.png" +
                "?taxonKey=$taxonKey&srs=EPSG:3857&bin=hex&hexPerTile=$HEXES_PER_TILE&style=green.poly"
    }
}

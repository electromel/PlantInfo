package ch.electromel.plantinfo.util

import ch.electromel.plantinfo.domain.model.LatLng

/** Petites fonctions géométriques pour cadrer la carte de l'aire de répartition. */
object GeoUtils {

    /** Rayon autour du lieu de prise de vue dans lequel on cadre les occurrences GBIF (~un continent). */
    const val FRAMING_RADIUS_KM = 2000.0

    private const val EARTH_RADIUS_KM = 6371.0

    /** Distance orthodromique approchée (haversine), en kilomètres. */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
            Math.sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Points à cadrer pour voir à la fois le lieu de prise de vue [captured] et l'aire de répartition.
     *
     * Une espèce naturalisée a des occurrences aux antipodes : tout englober donnerait un planisphère
     * où le point de prise de vue n'est plus qu'un grain. On ne garde donc que les [occurrences] à
     * moins de [radiusKm] du point. Si aucune n'est aussi proche (l'espèce vit loin de là), on garde
     * tout pour que les deux restent visibles malgré la distance. Sans [captured], toutes les
     * occurrences sont cadrées.
     */
    fun framingPoints(
        captured: LatLng?,
        occurrences: List<LatLng>,
        radiusKm: Double = FRAMING_RADIUS_KM,
    ): List<LatLng> {
        if (captured == null) return occurrences
        val near = occurrences.filter { distanceKm(captured, it) <= radiusKm }
        return (near.ifEmpty { occurrences }) + captured
    }
}

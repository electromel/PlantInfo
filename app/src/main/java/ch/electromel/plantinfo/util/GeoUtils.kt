package ch.electromel.plantinfo.util

import ch.electromel.plantinfo.domain.model.LatLng

/** Petites fonctions géométriques pour l'affichage de l'aire de répartition. */
object GeoUtils {

    /**
     * Enveloppe convexe (Andrew's monotone chain) d'un nuage de points. Sert à tracer une zone
     * semi-transparente approximative à partir des occurrences GBIF. Renvoie une liste vide si
     * moins de 3 points distincts (pas de polygone traçable).
     */
    fun convexHull(input: List<LatLng>): List<LatLng> {
        val points = input.distinctBy { it.lat to it.lng }
            .sortedWith(compareBy({ it.lng }, { it.lat }))
        if (points.size < 3) return emptyList()

        fun cross(o: LatLng, a: LatLng, b: LatLng): Double =
            (a.lng - o.lng) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lng - o.lng)

        val lower = ArrayList<LatLng>()
        for (p in points) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = ArrayList<LatLng>()
        for (p in points.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }
}

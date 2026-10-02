package ch.electromel.plantinfo.util

import ch.electromel.plantinfo.domain.model.LatLng
import java.util.Locale

/** Petites fonctions géométriques pour l'affichage de l'aire de répartition. */
object GeoUtils {

    /**
     * Décimales conservées quand un lieu quitte l'appareil : 2 décimales de latitude valent environ
     * 1,1 km. Suffisant pour le climat, la région et l'altitude — tout ce dont une IA se sert pour
     * juger la plausibilité d'une espèce — sans livrer l'endroit exact d'une plante, d'un champignon
     * ou du domicile de l'utilisateur à un tiers.
     */
    const val APPROXIMATE_DECIMALS = 2

    /** Précision complète affichée à l'utilisateur sur sa propre fiche (5 décimales ≈ 1 m). */
    const val PRECISE_DECIMALS = 5

    /** « 46.12, 6.15 » : le lieu arrondi à ~1 km, point décimal quelle que soit la langue. */
    fun approximateCoordinates(latitude: Double, longitude: Double): String =
        formatCoordinates(latitude, longitude, APPROXIMATE_DECIMALS)

    /**
     * Coordonnées d'une fiche partagée ou exportée en PDF. Complètes par défaut ; arrondies pour une
     * espèce **protégée** : l'application déconseille elle-même d'y toucher, la fiche ne doit pas
     * en livrer l'emplacement précis à quiconque la reçoit.
     */
    fun shareableCoordinates(latitude: Double, longitude: Double, protectedSpecies: Boolean): String =
        formatCoordinates(
            latitude, longitude,
            if (protectedSpecies) APPROXIMATE_DECIMALS else PRECISE_DECIMALS,
        )

    private fun formatCoordinates(latitude: Double, longitude: Double, decimals: Int): String =
        "%.${decimals}f, %.${decimals}f".format(Locale.US, latitude, longitude)

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

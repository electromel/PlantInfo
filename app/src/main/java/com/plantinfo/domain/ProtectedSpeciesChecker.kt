package com.plantinfo.domain

/**
 * Vérification indicative du statut protégé d'une espèce en Suisse (§2.4).
 *
 * ATTENTION : liste **indicative et non exhaustive**, basée sur les espèces protégées au niveau
 * fédéral (OPN, annexe 2) et les genres emblématiques largement protégés. Elle complète le drapeau
 * renvoyé par l'IA : en cas de doute, la réglementation cantonale/fédérale fait foi. On privilégie
 * ici la prudence (mieux vaut sur-signaler que manquer une espèce protégée, l'objectif étant de
 * déconseiller la cueillette).
 */
object ProtectedSpeciesChecker {

    // Toutes les orchidées indigènes sont protégées en Suisse : on liste leurs genres.
    private val orchidGenera = setOf(
        "orchis", "ophrys", "dactylorhiza", "anacamptis", "cypripedium", "gymnadenia",
        "platanthera", "epipactis", "cephalanthera", "neottia", "spiranthes", "traunsteinera",
        "nigritella", "himantoglossum", "serapias", "coeloglossum", "listera", "goodyera",
        "pseudorchis", "chamorchis", "corallorhiza", "hammarbya", "liparis", "malaxis",
        "epipogium", "limodorum",
    )

    // Genres dont les espèces indigènes sont largement protégées (montagne, zones humides, bulbeuses).
    private val protectedGenera = orchidGenera + setOf(
        "leontopodium", "eryngium", "gentiana", "gentianella", "lilium", "tulipa", "pulsatilla",
        "aquilegia", "daphne", "ilex", "ruscus", "cyclamen", "paradisea", "narcissus", "iris",
        "androsace", "pinguicula", "drosera", "nuphar", "nymphaea", "trollius", "aconitum",
        "adonis", "fritillaria", "gagea", "sternbergia", "galanthus", "leucojum", "crocus",
        "colchicum", "asphodelus", "dianthus", "primula", "saxifraga", "eritrichium", "wulfenia",
        "dracocephalum", "typha", "menyanthes",
    )

    // Quelques binômes emblématiques (au cas où le genre n'est pas listé globalement).
    private val protectedSpecies = setOf(
        "leontopodium alpinum", "eryngium alpinum", "ilex aquifolium", "daphne mezereum",
        "daphne cneorum", "lilium martagon", "lilium bulbiferum", "paradisea liliastrum",
        "trollius europaeus", "dianthus superbus", "primula auricula", "aquilegia alpina",
    )

    fun isProtected(scientificName: String): Boolean {
        val normalized = scientificName.trim().lowercase()
        val genus = normalized.substringBefore(' ')
        if (genus.isEmpty()) return false
        val binomial = normalized.split(Regex("\\s+")).take(2).joinToString(" ")
        return genus in protectedGenera || binomial in protectedSpecies
    }
}

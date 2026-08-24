package ch.electromel.plantinfo.domain

/**
 * Vérification indicative de la toxicité d'une espèce pour l'humain (§2.4).
 *
 * ATTENTION : liste **indicative et non exhaustive**, centrée sur les espèces toxiques ou mortelles
 * d'Europe et sur les confusions dangereuses les plus documentées. Elle sert à **avertir** d'une
 * hypothèse toxique restée plausible, jamais à conclure qu'une espèce est sûre : une espèce absente
 * de cette liste n'est pas pour autant comestible.
 *
 * Elle complète le drapeau renvoyé par l'IA — indispensable pour les candidats **Pl@ntNet**, qui
 * n'en portent jamais — et le renforce sans jamais le désactiver : si l'IA déclare inoffensive une
 * espèce listée ici, c'est cette liste qui l'emporte.
 *
 * Les genres retenus sont ceux dont la toxicité vaut pour l'ensemble des espèces indigènes. Un genre
 * mêlant espèces comestibles et toxiques (Solanum, qui contient la tomate ; Sambucus, dont le sureau
 * noir est consommé cuit) est volontairement traité au binôme, pour ne pas noyer l'avertissement.
 */
object ToxicSpeciesChecker {

    // Plantes dont toutes les espèces indigènes sont toxiques pour l'humain.
    private val toxicPlantGenera = setOf(
        // Mortelles ou gravement toxiques
        "aconitum", "colchicum", "digitalis", "atropa", "hyoscyamus", "datura", "brugmansia",
        "conium", "cicuta", "oenanthe", "veratrum", "convallaria", "nerium", "taxus", "ricinus",
        "gloriosa", "aristolochia", "laburnum", "bryonia", "actaea", "paris", "phytolacca",
        // Toxiques par ingestion (baies, bulbes, feuillage)
        "arum", "helleborus", "delphinium", "consolida", "daphne", "ligustrum", "ilex", "viscum",
        "hedera", "wisteria", "robinia", "rhododendron", "kalmia", "euphorbia", "mercurialis",
        "chelidonium", "ranunculus", "anemone", "senecio", "jacobaea", "narcissus", "galanthus",
        "leucojum", "hyacinthus", "scilla", "iris", "lupinus", "physalis", "toxicodendron",
        // Plantes d'intérieur fréquemment photographiées (oxalates de calcium)
        "dieffenbachia", "philodendron", "monstera", "spathiphyllum", "epipremnum", "alocasia",
        "caladium", "zantedeschia", "anthurium", "syngonium",
        // Phototoxiques (brûlures au contact combiné au soleil)
        "heracleum", "ruta", "pastinaca", "dictamnus",
    )

    // Champignons : genres contenant des espèces mortelles ou gravement toxiques. Certains abritent
    // aussi de bons comestibles (Amanita caesarea, Clitocybe nuda) — l'avertissement reste justifié,
    // ce sont précisément les genres où la confusion tue.
    private val toxicFungusGenera = setOf(
        "amanita", "galerina", "lepiota", "cortinarius", "gyromitra", "inocybe", "clitocybe",
        "entoloma", "omphalotus", "hypholoma", "paxillus", "conocybe", "pholiotina", "scleroderma",
        "chlorophyllum", "rubroboletus",
    )

    // Binômes toxiques appartenant à un genre par ailleurs comestible ou inoffensif.
    private val toxicSpecies = setOf(
        "solanum nigrum", "solanum dulcamara", "solanum tuberosum", // parties vertes de la pomme de terre
        "sambucus ebulus", "aethusa cynapium", "prunus laurocerasus", "cytisus laburnum",
        "tricholoma equestre", "boletus satanas", "russula emetica", "agaricus xanthodermus",
        "lyophyllum connatum", "armillaria mellea", // toxique crue ou mal cuite
    )

    /** true si l'espèce figure dans la liste indicative des espèces toxiques pour l'humain. */
    fun isToxic(scientificName: String): Boolean {
        val normalized = scientificName.trim().lowercase()
        val genus = normalized.substringBefore(' ')
        if (genus.isEmpty()) return false
        val binomial = normalized.split(Regex("\\s+")).take(2).joinToString(" ")
        return genus in toxicPlantGenera || genus in toxicFungusGenera || binomial in toxicSpecies
    }
}

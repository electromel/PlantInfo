package com.plantinfo.domain

/**
 * Reconnaissance indicative d'un nom de champignon à partir de son genre (§2.4).
 *
 * ATTENTION : liste **indicative et non exhaustive**, centrée sur les genres de macromycètes
 * européens les plus photographiés (comestibles réputés compris — c'est justement là que la
 * confusion tue). Elle ne sert qu'à **activer** l'avertissement mycologique systématique quand
 * l'information manque : un nom absent de cette liste n'est pas pour autant une plante.
 *
 * Utilisation prévue : la validation manuelle d'une hypothèse (`HistoryRepository.selectSpecies`)
 * change d'espèce sans repasser par l'IA, donc sans nouveau drapeau `isFungus`. Le drapeau existant
 * est conservé (jamais désactivé) et cette liste permet de le **lever** lors d'une bascule
 * plante → champignon, cas où l'avertissement de sécurité est le plus nécessaire.
 *
 * Même esprit que [ToxicSpeciesChecker] et [ProtectedSpeciesChecker] : renforcer, jamais rassurer.
 */
object FungusChecker {

    // Genres de macromycètes fréquemment identifiés, comestibles réputés inclus. Volontairement
    // limité aux genres sans homonymie avec un genre végétal.
    private val fungusGenera = setOf(
        // Genres toxiques ou mortels (repris de ToxicSpeciesChecker, qui ne couvre que la toxicité)
        "amanita", "galerina", "lepiota", "cortinarius", "gyromitra", "inocybe", "clitocybe",
        "entoloma", "omphalotus", "hypholoma", "paxillus", "conocybe", "pholiotina", "scleroderma",
        "chlorophyllum", "rubroboletus",
        // Comestibles réputés et genres courants
        "agaricus", "boletus", "cantharellus", "craterellus", "lactarius", "russula", "suillus",
        "leccinum", "xerocomus", "imleria", "tricholoma", "macrolepiota", "coprinus", "coprinopsis",
        "pleurotus", "armillaria", "hydnum", "sparassis", "morchella", "marasmius", "mycena",
        "psathyrella", "lycoperdon", "calvatia", "bovista", "phallus", "clathrus", "ramaria",
        "clavaria", "hericium", "fomes", "fomitopsis", "ganoderma", "trametes", "laetiporus",
        "polyporus", "grifola", "auricularia", "tremella", "helvella", "peziza", "tuber",
        "rhizopogon", "hygrophorus", "hygrocybe", "laccaria", "lyophyllum", "pluteus", "volvariella",
        "stropharia", "panaeolus", "psilocybe", "gymnopus", "rhodocollybia", "flammulina",
        "kuehneromyces", "leucoagaricus", "agrocybe", "pholiota", "crepidotus", "schizophyllum",
    )

    /** true si le binôme appartient à un genre fongique connu de la liste indicative. */
    fun isFungus(scientificName: String): Boolean {
        val genus = scientificName.trim().lowercase().substringBefore(' ')
        return genus.isNotEmpty() && genus in fungusGenera
    }
}

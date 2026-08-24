package ch.electromel.plantinfo.domain.model

import ch.electromel.plantinfo.domain.ToxicSpeciesChecker
import kotlinx.serialization.Serializable

/**
 * Fournisseurs d'IA générative multimodale supportés, plus NONE (aucune clé configurée →
 * résultat Pl@ntNet brut). L'ordre des constantes n'a pas de sens métier : l'ordre de repli
 * réel est stocké dans les paramètres (voir data.keys.ApiKeyStore).
 */
enum class AiProviderType(val label: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    GPT("GPT"),
    NONE("Aucun");

    companion object {
        /** Ordre de priorité de repli par défaut (§3.1) : Claude → Gemini → GPT. */
        val DEFAULT_FALLBACK_ORDER = listOf(CLAUDE, GEMINI, GPT)
    }
}

/**
 * Type d'organe photographié, transmis à Pl@ntNet comme indice et utilisé pour formuler
 * les demandes de photos complémentaires (§2.1).
 */
enum class PhotoOrgan(val plantnetValue: String, val label: String) {
    LEAF("leaf", "Feuille"),
    FLOWER("flower", "Fleur"),
    FRUIT("fruit", "Fruit"),
    BARK("bark", "Écorce"),
    HABIT("habit", "Port / silhouette"),
    CAP("auto", "Chapeau (champignon)"),
    GILLS("auto", "Lamelles / dessous du chapeau"),
    OTHER("auto", "Autre");
}

/** Coordonnées de la prise de vue, avec précision rapportée par le capteur (§2.2). */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,        // mètres, si disponible
    val accuracyMeters: Float?,   // rayon d'incertitude horizontal rapporté par le capteur
) {
    /** Précision jugée faible (couvert forestier, montagne) → afficher un rayon plutôt qu'un point. */
    val isLowAccuracy: Boolean get() = (accuracyMeters ?: Float.MAX_VALUE) > 50f
}

/**
 * Un candidat d'identification (Pl@ntNet ou IA), avec son score propre sur 100.
 *
 * [gbifKey] et [iucnCategory] ne sont renseignés que par Pl@ntNet, qui les rattache au taxon qu'il a
 * lui-même reconnu. Ils restent donc solidaires de *ce* candidat : ils ne doivent jamais être
 * reportés sur une espèce retenue différente (l'IA peut corriger Pl@ntNet).
 *
 * Valeurs par défaut obligatoires : d'anciennes alternatives sérialisées (colonne alternativesJson)
 * ne contiennent pas ces champs.
 */
@Serializable
data class SpeciesCandidate(
    val scientificName: String,
    val commonName: String?,
    val score: Int, // 0..100
    val gbifKey: Long? = null,        // clé taxonomique GBIF, évite une résolution par nom
    val iucnCategory: String? = null, // code UICN brut (LC, NT, VU, EN, CR…)
    // Toxicité pour l'humain, renseignée par l'IA seule : les candidats Pl@ntNet arrivent toujours
    // à null. Ne jamais lire ce champ directement pour avertir l'utilisateur — voir [isToxic], qui
    // complète par la liste locale.
    val toxic: Boolean? = null,
)

/**
 * Statut de conservation UICN, tel que rapporté par Pl@ntNet (§2.4).
 *
 * [threatened] marque les catégories qui justifient de déconseiller la cueillette. Attention : il
 * s'agit d'un statut de conservation **mondial**, pas d'une protection juridique locale — les deux
 * sont affichés séparément.
 */
enum class IucnStatus(val code: String, val label: String, val threatened: Boolean) {
    EX("EX", "Éteinte", true),
    EW("EW", "Éteinte à l'état sauvage", true),
    CR("CR", "En danger critique d'extinction", true),
    EN("EN", "En danger", true),
    VU("VU", "Vulnérable", true),
    NT("NT", "Quasi menacée", false),
    LC("LC", "Préoccupation mineure", false),
    DD("DD", "Données insuffisantes", false);

    companion object {
        fun fromCode(code: String?): IucnStatus? {
            val normalized = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

/** Diagnostic de santé issu de l'analyse visuelle par l'IA (§2.4). */
data class HealthAssessment(
    val status: String,               // ex. « Bon », « Signes de stress hydrique »
    val isHealthy: Boolean,
    val recommendations: List<String>, // vide si plante saine
)

/**
 * Une opération saisonnière sur l'espèce : semis/plantation, taille, arrosage, récolte…
 *
 * [period] est du texte libre (« mars à avril », « toute l'année », « après la floraison ») plutôt
 * qu'un intervalle de mois : les recommandations réelles sont souvent conditionnelles (« dès que le
 * sol est hors gel ») et un modèle de mois figé les trahirait. Pour un champignon, le calendrier
 * décrit la période de pousse et de cueillette, faute de culture possible.
 */
@Serializable
data class CareTask(
    val label: String,        // ex. « Plantation », « Taille », « Récolte »
    val period: String,       // ex. « Mars à avril », « Après la floraison »
    val note: String? = null, // précision courte : méthode, précaution
)

/**
 * Domaine d'usage d'une espèce, utilisé pour regrouper et titrer les usages connus (§2.4).
 * Le code sérialisé est le nom de la constante ; [fromCode] accepte aussi les libellés anglais
 * renvoyés par l'IA et retombe sur [OTHER] plutôt que d'échouer.
 */
@Serializable
enum class UseDomain(val label: String) {
    MEDICINAL("Santé et médecine"),
    FOOD("Alimentation"),
    COSMETIC("Cosmétique et parfumerie"),
    CHEMICAL("Chimie et industrie"),
    CRAFT("Artisanat et matériaux"),
    ORNAMENTAL("Ornement et paysage"),
    ECOLOGICAL("Écologie et jardin"),
    OTHER("Autres usages");

    companion object {
        fun fromCode(code: String?): UseDomain {
            val normalized = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return OTHER
            return entries.firstOrNull { it.name == normalized } ?: OTHER
        }
    }
}

/** Un usage documenté de l'espèce, rattaché à son domaine (§2.4). */
@Serializable
data class SpeciesUse(
    val domain: UseDomain,
    val detail: String,
)

/**
 * Demande de photo complémentaire quand la confiance est insuffisante (§2.1 / §2.4) :
 * précise quel cliché est utile et pourquoi.
 */
data class ComplementaryPhotoRequest(
    val organ: PhotoOrgan,
    val reason: String,
)

/**
 * Résultat complet d'une identification, prêt à être affiché et persisté.
 * Regroupe les sorties Pl@ntNet + IA et le score combiné (§2.3 / §4).
 */
data class IdentificationResult(
    val commonName: String,
    val scientificName: String,
    val scorePlantNet: Int?,          // null si Pl@ntNet non appelé/sans résultat (ex. champignon)
    val scoreAi: Int?,                // null si aucune IA disponible
    val scoreFinal: Int,              // 0..100, score d'exactitude affiché
    val aiProvider: AiProviderType,   // fournisseur ayant réellement traité (ou NONE)
    val alternatives: List<SpeciesCandidate>, // 2-3 hypothèses alternatives si score non maximal
    val isFungus: Boolean,            // déclenche l'avertissement de sécurité systématique
    val isProtected: Boolean,         // espèce protégée dans la région → avertissement cueillette
    val health: HealthAssessment?,    // null si pas d'analyse IA
    val habitat: String?,             // habitat / répartition typique
    val description: String?,         // infos générales (saisonnalité, usages…)
    val matureHeight: String?,        // hauteur à maturité, ex. « 15–25 m » (null = non évalué)
    val matureDiameter: String?,      // diamètre / étalement à maturité, ex. « 10–15 m »
    val timeToMaturity: String?,      // temps pour atteindre la maturité, ex. « 20–30 ans »
    val edible: Boolean?,             // true = comestible, false = non comestible, null = inconnu/non évalué
    val toxic: Boolean?,              // true = toxique/vénéneux, false = non toxique, null = inconnu
    val edibilityNote: String?,       // précisions sur comestibilité/toxicité (parties, préparation, dangers)
    val careCalendar: List<CareTask>, // calendrier de plantation et d'entretien ; vide si non évalué
    val uses: List<SpeciesUse>,       // usages documentés (santé, chimie, parfumerie…) ; vide si aucun
    val symbolism: String?,           // signification symbolique / culturelle, null si aucune connue
    val gbifKey: Long?,               // clé GBIF de l'espèce retenue (Pl@ntNet), null si non confirmée
    val iucnCategory: String?,        // code UICN de l'espèce retenue (Pl@ntNet), null si inconnu
    val sourcesDisagree: Boolean,     // Pl@ntNet et IA divergent significativement → à signaler
    val complementaryPhotoRequest: ComplementaryPhotoRequest?, // si score sous le seuil
    // Jetons consommés par l'appel IA de cette identification (null si aucune IA n'a répondu, ou si
    // le fournisseur ne rapporte pas d'usage). Sert à afficher le coût de l'analyse (§3.x).
    val usage: TokenUsage? = null,
) {
    companion object {
        /** Seuil sous lequel on invite à fournir une photo complémentaire (§2.4). */
        const val LOW_CONFIDENCE_THRESHOLD = 60

        /**
         * Sous ce score final, l'identification n'est pas assez sûre pour écarter les hypothèses
         * concurrentes : une hypothèse toxique encore plausible doit être signalée. Volontairement
         * bien plus haut que [LOW_CONFIDENCE_THRESHOLD] — un score même bon de 75 reste insuffisant
         * quand se tromper d'espèce peut empoisonner.
         */
        const val TOXIC_ALERT_MAX_SCORE = 80

        /** Au-dessus de ce score, une hypothèse alternative reste plausible et compte dans l'alerte. */
        const val PLAUSIBLE_ALTERNATIVE_MIN_SCORE = 10
    }
}

/**
 * Seuils de l'avertissement de confusion toxique, réglables dans les Paramètres (§3.x).
 *
 * Les valeurs par défaut sont celles de [IdentificationResult] : un utilisateur qui ne touche à rien
 * garde le comportement livré. Régler plus haut = avertir plus souvent (plus prudent, plus bavard).
 */
data class ToxicAlertThresholds(
    /** Score final **strictement** sous lequel l'avertissement peut se déclencher. */
    val maxScore: Int = IdentificationResult.TOXIC_ALERT_MAX_SCORE,
    /** Score **strictement** au-dessus duquel une hypothèse alternative est jugée plausible. */
    val minAlternativeScore: Int = IdentificationResult.PLAUSIBLE_ALTERNATIVE_MIN_SCORE,
) {
    companion object {
        /** Plage réglable du score final : 100 = avertir dès qu'une hypothèse toxique existe. */
        val MAX_SCORE_RANGE = 50..100

        /** Plage réglable de la plausibilité : 0 = retenir toute hypothèse, même très faible. */
        val MIN_ALTERNATIVE_RANGE = 0..40

        /** Pas des deux réglages, pour des valeurs rondes et une manipulation facile au doigt. */
        const val STEP = 5
    }
}

/** Verdict de comestibilité synthétique dérivé des drapeaux edible/toxic (§2.4). */
enum class EdibilityVerdict { EDIBLE, TOXIC, INEDIBLE, UNKNOWN }

/** La toxicité prime sur la comestibilité (sécurité) ; UNKNOWN si aucune info fiable. */
val IdentificationResult.edibilityVerdict: EdibilityVerdict
    get() = when {
        toxic == true -> EdibilityVerdict.TOXIC
        edible == true -> EdibilityVerdict.EDIBLE
        edible == false -> EdibilityVerdict.INEDIBLE
        else -> EdibilityVerdict.UNKNOWN
    }

/** Libellé court du verdict, ou null si inconnu (rien à afficher comme verdict). */
val EdibilityVerdict.label: String?
    get() = when (this) {
        EdibilityVerdict.EDIBLE -> "Comestible"
        EdibilityVerdict.TOXIC -> "Toxique"
        EdibilityVerdict.INEDIBLE -> "Non comestible"
        EdibilityVerdict.UNKNOWN -> null
    }

/**
 * Toxicité d'une hypothèse, vue de façon prudente : le drapeau de l'IA **ou** la liste locale
 * suffisent. Un candidat Pl@ntNet arrive toujours avec `toxic == null` ; sans la liste locale,
 * l'hypothèse concurrente issue de Pl@ntNet — celle qui compte le plus en cas de désaccord — ne
 * déclencherait jamais d'alerte.
 */
val SpeciesCandidate.isToxic: Boolean
    get() = toxic == true || ToxicSpeciesChecker.isToxic(scientificName)

/**
 * Hypothèses toxiques encore plausibles alors que l'identification n'est pas assurée (§2.4) :
 * score final sous [ToxicAlertThresholds.maxScore] et alternative crédible au-dessus de
 * [ToxicAlertThresholds.minAlternativeScore]. Vide dès que l'une des deux conditions manque.
 *
 * [thresholds] a une valeur par défaut : les appelants qui n'ont pas accès aux préférences (tests,
 * code de domaine) gardent le comportement livré.
 */
fun IdentificationResult.plausibleToxicAlternatives(
    thresholds: ToxicAlertThresholds = ToxicAlertThresholds(),
): List<SpeciesCandidate> {
    if (scoreFinal >= thresholds.maxScore) return emptyList()
    return alternatives.filter { it.score > thresholds.minAlternativeScore && it.isToxic }
}

/** Nom lisible d'une hypothèse : « Colchique d'automne (Colchicum autumnale) ». */
fun SpeciesCandidate.displayName(): String =
    commonName?.takeIf { it.isNotBlank() }?.let { "$it ($scientificName)" } ?: scientificName

/**
 * Avertissement de confusion toxique pour l'affichage, le PDF et le partage. null si aucune
 * hypothèse toxique n'est plausible — le texte doit être identique partout, une fiche partagée ne
 * doit pas être plus rassurante que la fiche à l'écran.
 */
fun IdentificationResult.toxicConfusionWarningText(
    thresholds: ToxicAlertThresholds = ToxicAlertThresholds(),
): String? {
    val toxic = plausibleToxicAlternatives(thresholds).takeIf { it.isNotEmpty() } ?: return null
    val names = toxic.joinToString(" · ") { it.displayName() }
    val intro = if (toxic.size == 1) {
        "une espèce toxique reste plausible"
    } else {
        "des espèces toxiques restent plausibles"
    }
    return "Identification incertaine ($scoreFinal/100) : $intro parmi les autres hypothèses — " +
        "$names. Il pourrait donc s'agir d'une espèce toxique : ne consommez rien, ne portez rien " +
        "à la bouche et manipulez avec précaution sans confirmation par un expert."
}

/** Statut de conservation UICN de l'espèce retenue, ou null si Pl@ntNet n'en rapporte aucun. */
val IdentificationResult.iucnStatus: IucnStatus?
    get() = IucnStatus.fromCode(iucnCategory)

/** Les trois mesures de maturité, libellées, dans l'ordre d'affichage ; vide si aucune renseignée. */
fun IdentificationResult.maturityLines(): List<Pair<String, String>> = listOfNotNull(
    matureHeight?.takeIf { it.isNotBlank() }?.let { "Hauteur" to it },
    matureDiameter?.takeIf { it.isNotBlank() }?.let { "Diamètre" to it },
    timeToMaturity?.takeIf { it.isNotBlank() }?.let { "Temps jusqu'à maturité" to it },
)

/** Résumé textuel des dimensions à maturité pour PDF/partage. null si aucune information. */
fun IdentificationResult.maturitySummaryText(): String? =
    maturityLines().takeIf { it.isNotEmpty() }?.joinToString(" · ") { (label, value) -> "$label : $value" }

/** Usages regroupés par domaine, dans l'ordre de déclaration de [UseDomain] ; vide si aucun usage. */
fun IdentificationResult.usesByDomain(): List<Pair<UseDomain, List<String>>> =
    uses.filter { it.detail.isNotBlank() }
        .groupBy { it.domain }
        .toList()
        .sortedBy { (domain, _) -> domain.ordinal }
        .map { (domain, list) -> domain to list.map { it.detail } }

/** Le calendrier, débarrassé des entrées sans libellé ni période exploitables. */
fun IdentificationResult.careCalendarLines(): List<CareTask> =
    careCalendar.filter { it.label.isNotBlank() && it.period.isNotBlank() }

/** Résumé textuel du calendrier pour PDF/partage, une opération par ligne. null si vide. */
fun IdentificationResult.careCalendarSummaryText(): String? =
    careCalendarLines().takeIf { it.isNotEmpty() }?.joinToString("\n") { task ->
        "• ${task.label} : ${task.period}" + (task.note?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
    }

/** Résumé textuel des usages pour PDF/partage, un domaine par ligne. null si aucun usage. */
fun IdentificationResult.usesSummaryText(): String? =
    usesByDomain().takeIf { it.isNotEmpty() }?.joinToString("\n") { (domain, details) ->
        "• ${domain.label} : ${details.joinToString(" ; ")}"
    }

/** Résumé textuel (verdict + précisions) pour PDF/partage. null si aucune information. */
fun IdentificationResult.edibilitySummaryText(): String? {
    val verdict = edibilityVerdict.label
    val note = edibilityNote?.takeIf { it.isNotBlank() }
    return when {
        verdict != null && note != null -> "$verdict — $note"
        verdict != null -> verdict
        else -> note
    }
}

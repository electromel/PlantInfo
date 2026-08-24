package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.CareTask
import ch.electromel.plantinfo.domain.model.GpsLocation
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.SpeciesUse
import ch.electromel.plantinfo.domain.model.TokenUsage
import ch.electromel.plantinfo.domain.model.label

/** Une image à analyser : octets JPEG/WebP + type MIME. */
data class AiImage(
    val bytes: ByteArray,
    val mimeType: String, // "image/jpeg" ou "image/webp"
)

/** Entrée du modèle IA : photo(s) + contexte Pl@ntNet + géolocalisation (§2.3). */
data class AiAnalysisInput(
    val images: List<AiImage>,
    val plantNetCandidates: List<SpeciesCandidate>,
    val gps: GpsLocation?,
)

/** Demande de photo complémentaire telle que renvoyée par l'IA (organe + raison). */
data class AiComplementaryRequest(
    val organ: PhotoOrgan,
    val reason: String,
)

/**
 * Sortie structurée de l'IA après parsing du JSON. Ne contient pas encore le score final combiné :
 * la fusion avec Pl@ntNet est faite par le moteur de score (domain.ConfidenceEngine).
 */
data class AiAnalysis(
    val commonName: String,
    val scientificName: String,
    val confidence: Int,               // 0..100, confiance propre de l'IA
    val isFungus: Boolean,
    val isProtected: Boolean,
    val alternatives: List<SpeciesCandidate>,
    val healthStatus: String,
    val isHealthy: Boolean,
    val recommendations: List<String>,
    val habitat: String?,
    val description: String?,
    val matureHeight: String?,    // hauteur à maturité (texte libre avec unité), null si non évaluable
    val matureDiameter: String?,  // diamètre / étalement à maturité
    val timeToMaturity: String?,  // temps pour atteindre la maturité
    val edible: Boolean?,
    val toxic: Boolean?,
    val edibilityNote: String?,
    val careCalendar: List<CareTask>, // plantation, taille, arrosage, récolte… ; vide si non évaluable
    val uses: List<SpeciesUse>,       // usages documentés par domaine ; vide si aucun connu
    val symbolism: String?,           // signification symbolique/culturelle, null si aucune
    val complementary: AiComplementaryRequest?,
    // Jetons consommés par l'appel, rapportés par le fournisseur. Renseigné par le client HTTP
    // après le parsing du JSON métier (AiPrompt.parse n'en sait rien) ; null si le fournisseur
    // ne rapporte rien d'exploitable.
    val usage: TokenUsage? = null,
)

/**
 * Réponse à une question libre : le texte et, quand le fournisseur le rapporte, les jetons
 * consommés. Un simple String ne suffisait plus dès lors que le coût de chaque question doit être
 * affiché à l'utilisateur.
 */
data class AiAnswer(
    val text: String,
    val usage: TokenUsage?,
)

/** Motif d'échec d'un fournisseur IA, utilisé par l'orchestrateur de repli et les messages (§3.1). */
enum class AiFailureReason {
    MISSING_KEY,   // aucune clé configurée
    INVALID_KEY,   // 401/403
    QUOTA,         // 429 / limite de débit ou quota gratuit dépassé
    BILLING,       // crédit/solde épuisé sur le compte du fournisseur (429 insufficient_quota, 400 credit balance)
    NETWORK,       // pas de réseau / timeout → mise en file possible
    SERVER,        // 5xx
    PARSE,         // réponse illisible
    UNKNOWN,
}

/** Libellé court en français d'un motif d'échec, partagé par tous les messages utilisateur. */
fun AiFailureReason.label(): String = when (this) {
    AiFailureReason.MISSING_KEY -> "aucune clé"
    AiFailureReason.INVALID_KEY -> "clé invalide"
    AiFailureReason.QUOTA -> "quota atteint"
    AiFailureReason.BILLING -> "crédit épuisé sur le compte"
    AiFailureReason.NETWORK -> "réseau indisponible"
    AiFailureReason.SERVER -> "erreur serveur"
    AiFailureReason.PARSE -> "réponse illisible"
    AiFailureReason.UNKNOWN -> "erreur inconnue"
}

/** Échec d'un fournisseur précis lors du repli, pour des messages d'erreur honnêtes par fournisseur. */
data class ProviderFailure(
    val provider: AiProviderType,
    val reason: AiFailureReason,
)

/** Résumé lisible des échecs par fournisseur, ex. « Claude : crédit épuisé · Gemini : quota atteint ». */
fun List<ProviderFailure>.summary(): String =
    joinToString(" · ") { "${it.provider.label} : ${it.reason.label()}" }

/** Exception typée levée par un client IA, portant le motif pour la logique de repli. */
class AiException(
    val reason: AiFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

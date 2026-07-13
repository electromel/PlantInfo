package com.plantinfo.domain.model

/** Requête d'identification adressée au repository (§2.3). */
data class IdentificationRequest(
    val photoPaths: List<String>,
    val organs: List<PhotoOrgan>,
    val gps: GpsLocation?,
)

/** Issue du pipeline d'identification. */
sealed interface IdentificationOutcome {

    /**
     * Identification réussie et enregistrée. [infoMessage] porte un avertissement non bloquant
     * (ex. « aucune clé IA : résultat Pl@ntNet brut » ou « IA indisponible, résultat partiel »).
     */
    data class Success(
        val id: Long,
        val result: IdentificationResult,
        val infoMessage: String?,
    ) : IdentificationOutcome

    /** Échec sans résultat exploitable. [queuedForRetry] = mis en file pour réessai réseau (Phase 3). */
    data class Failure(
        val kind: FailureKind,
        val message: String,
        val queuedForRetry: Boolean = false,
    ) : IdentificationOutcome
}

enum class FailureKind {
    NO_KEYS,        // ni clé Pl@ntNet ni clé IA
    INVALID_KEY,    // clés présentes mais rejetées
    QUOTA,          // quota atteint sur les fournisseurs
    NETWORK,        // hors-ligne / timeout
    SERVER,         // erreurs serveur
    NO_RESULT,      // aucun candidat, aucune analyse
}

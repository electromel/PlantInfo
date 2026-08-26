package ch.electromel.plantinfo.domain.model

import kotlinx.serialization.Serializable

/**
 * Consommation de jetons d'un appel à une IA générative, telle que rapportée par le fournisseur
 * lui-même (bloc `usage` d'Anthropic, `usageMetadata` de Gemini, `usage` d'OpenAI).
 *
 * Le **modèle** est conservé plutôt que le seul fournisseur : les tarifs sont par modèle, et un
 * changement de modèle dans le code ne doit pas re-tarifer les anciennes fiches.
 *
 * Le coût n'est **pas** stocké : il est recalculé à l'affichage par [AiPricing] à partir du modèle
 * et des jetons. Une mise à jour des tarifs corrige ainsi l'historique au lieu de le figer sur une
 * valeur devenue fausse.
 */
@Serializable
data class TokenUsage(
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
) {
    val totalTokens: Int get() = inputTokens + outputTokens

    /** Un rapport sans aucun jeton n'apporte rien à afficher (fournisseur muet sur l'usage). */
    val isEmpty: Boolean get() = totalTokens <= 0

    /** Somme de deux consommations du **même** modèle (identification + questions successives). */
    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        model = if (model == other.model) model else "$model + ${other.model}",
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
    )
}

/**
 * Tarifs publics des modèles utilisés par l'application, en **dollars US par million de jetons**.
 *
 * À tenir à jour à la main : il n'existe pas d'API de tarification chez ces fournisseurs. Les
 * valeurs et leur date de relevé sont documentées dans [Rate] ; un modèle inconnu renvoie `null`
 * plutôt qu'un coût inventé — l'écran affiche alors les jetons sans montant.
 *
 * Le coût affiché reste une **estimation** : les paliers gratuits (Gemini), les remises de cache et
 * les tarifs d'introduction ne sont pas modélisés.
 */
object AiPricing {

    /** Tarif d'un modèle, en USD par million de jetons d'entrée / de sortie. */
    data class Rate(val inputPerMillion: Double, val outputPerMillion: Double)

    /**
     * Relevé du 2026-08-24 sur les pages tarifaires officielles.
     *
     * - `claude-sonnet-5` : 3,00 / 15,00 $ (tarif standard ; un tarif d'introduction plus bas court
     *   jusqu'au 31.08.2026 — on retient le tarif plein pour ne jamais sous-estimer).
     * - `gemini-flash-latest` : alias suivant le dernier Gemini Flash, facturé 0,75 / 3,75 $.
     *   **Gratuit** tant que le compte reste sur le palier gratuit — d'où la mention « estimation ».
     * - `gpt-4o` : 2,50 / 10,00 $.
     */
    private val rates: Map<String, Rate> = mapOf(
        "claude-sonnet-5" to Rate(3.00, 15.00),
        "gemini-flash-latest" to Rate(0.75, 3.75),
        "gpt-4o" to Rate(2.50, 10.00),
    )

    /**
     * Ordre de grandeur du coût d'une identification avec Gemini, pour l'accompagnement de
     * l'utilisateur (assistant de configuration). Il est calculé à la main sur le tarif ci-dessus et
     * une identification typique — environ 3 000 jetons d'entrée (deux photos + le prompt) et 2 500
     * de sortie en comptant les jetons de raisonnement, facturés comme de la sortie — soit ~0,011 $.
     *
     * À revoir **en même temps** que [rates] : le tarif de Gemini Flash double au 01.01.2027, ce
     * qui portera l'ordre de grandeur à deux centimes.
     */
    const val GEMINI_COST_HINT: String =
        "environ 1 centime par identification (tarif Google relevé mi-2026, susceptible d'évoluer)"

    /** Coût estimé en USD, ou null si le modèle n'a pas de tarif connu. */
    fun costUsd(usage: TokenUsage): Double? {
        val rate = rates[usage.model] ?: return null
        return usage.inputTokens / 1_000_000.0 * rate.inputPerMillion +
            usage.outputTokens / 1_000_000.0 * rate.outputPerMillion
    }
}

/**
 * Groupe les milliers par une espace **insécable fine** (U+202F) : un nombre de jetons ne doit
 * jamais être coupé en fin de ligne dans les cartes étroites de la fiche.
 */
private fun Int.grouped(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(" ")
    .reversed()

/** « 1 234 jetons (982 entrée + 252 sortie) ». */
fun TokenUsage.tokensText(): String =
    "${totalTokens.grouped()} jetons (${inputTokens.grouped()} entrée + ${outputTokens.grouped()} sortie)"

/**
 * Coût formaté en dollars, ou null si le modèle n'a pas de tarif connu. Les montants sont minuscules
 * (fractions de centime) : on descend à 4 décimales, et sous le dixième de centime on affiche
 * « < 0,0001 $ » plutôt qu'un « 0,0000 $ » qui se lirait comme la gratuité.
 */
fun TokenUsage.costText(): String? {
    val cost = AiPricing.costUsd(this) ?: return null
    if (cost <= 0.0) return "0 \$"
    if (cost < 0.0001) return "< 0,0001 \$"
    return "%.4f \$".format(cost).replace('.', ',')
}

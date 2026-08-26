package ch.electromel.plantinfo.ui.result

import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeysSnapshot
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.ui.setup.SetupFocus

/**
 * Ce qui manque à une fiche, et ce qu'on peut y faire (§3.1).
 *
 * Le conseil est **déduit** de la fiche et de l'état courant des clés plutôt que transporté depuis
 * l'identification : il reste donc juste des mois plus tard, dans l'historique, et disparaît de
 * lui-même dès que la clé manquante est renseignée — au lieu de figer un message devenu faux.
 */
enum class FicheAdvice(val message: String, val actionLabel: String?, val focus: String?) {

    /** Aucune IA disponible : la fiche se limite au nom trouvé par Pl@ntNet. */
    NO_AI_KEY(
        message = "Aucune clé d'IA n'est configurée : cette fiche se limite à l'identification " +
            "Pl@ntNet, sans description, sans état de santé et sans questions possibles.",
        actionLabel = "Ajouter une clé d'IA",
        focus = SetupFocus.LLM,
    ),

    /** Les clés IA existent mais aucune n'a répondu : la relance est déjà offerte par la fiche. */
    AI_UNAVAILABLE(
        message = "L'analyse par l'IA n'a pas abouti lors de cette identification. Relancez " +
            "l'analyse pour compléter la fiche.",
        actionLabel = null,
        focus = null,
    ),

    /** IA seule : l'espèce n'a pas été confrontée à la base botanique de Pl@ntNet. */
    NO_PLANTNET_KEY(
        message = "Cette identification vient de l'IA seule : sans clé Pl@ntNet, le nom de l'espèce " +
            "n'a pas été confronté à la base d'observations botaniques.",
        actionLabel = "Ajouter la clé Pl@ntNet",
        focus = SetupFocus.provider(ApiProvider.PLANTNET),
    ),
    ;

    companion object {

        /**
         * Le conseil pertinent pour cette fiche, ou null si elle est complète. L'ordre compte :
         * l'absence d'IA prive de bien plus d'informations que l'absence de Pl@ntNet, et un seul
         * message à la fois vaut mieux que deux qu'on ne lira pas.
         */
        fun of(entity: IdentificationEntity, keys: KeysSnapshot): FicheAdvice? {
            val aiContributed = entity.aiProvider != AiProviderType.NONE.name
            val plantNetContributed = entity.scorePlantNet != null
            return when {
                aiContributed && plantNetContributed -> null
                !aiContributed && !keys.hasAi -> NO_AI_KEY
                !aiContributed -> AI_UNAVAILABLE
                !keys.hasPlantNet -> NO_PLANTNET_KEY
                // Pl@ntNet est configuré mais n'a rien rapporté (quota, réseau, aucun candidat) :
                // ce n'est pas un défaut de configuration, il n'y a rien à conseiller.
                else -> null
            }
        }
    }
}

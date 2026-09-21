package ch.electromel.plantinfo.ui.result

import androidx.annotation.StringRes
import ch.electromel.plantinfo.R
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
enum class FicheAdvice(
    @StringRes val messageRes: Int,
    @StringRes val actionLabelRes: Int?,
    val focus: String?,
) {

    /** Aucune IA disponible : la fiche se limite au nom trouvé par Pl@ntNet. */
    NO_AI_KEY(
        messageRes = R.string.advice_no_ai_key,
        actionLabelRes = R.string.advice_no_ai_key_action,
        focus = SetupFocus.LLM,
    ),

    /** Les clés IA existent mais aucune n'a répondu : la relance est déjà offerte par la fiche. */
    AI_UNAVAILABLE(
        messageRes = R.string.advice_ai_unavailable,
        actionLabelRes = null,
        focus = null,
    ),

    /** IA seule : l'espèce n'a pas été confrontée à la base botanique de Pl@ntNet. */
    NO_PLANTNET_KEY(
        messageRes = R.string.advice_no_plantnet_key,
        actionLabelRes = R.string.advice_no_plantnet_key_action,
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

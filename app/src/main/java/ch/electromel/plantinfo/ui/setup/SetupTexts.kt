package ch.electromel.plantinfo.ui.setup

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import ch.electromel.plantinfo.R

/**
 * Textes de l'assistant de configuration (§3.1).
 *
 * C'est du **contenu**, pas du code : ces phrases décrivent ce que fait l'application et ce qu'elle
 * envoie à l'extérieur. Elles sont à relire dès qu'un service externe est ajouté ou retiré du
 * pipeline — une promesse de confidentialité fausse est pire que pas de promesse du tout — et à
 * corriger dans **toutes** les langues traduites (les fichiers `strings.xml`).
 *
 * Ce qui concerne les clés API elles-mêmes vit dans `data/keys/ApiKeyGuide.kt`, source unique
 * partagée avec l'écran Paramètres.
 */
object SetupTexts {

    @StringRes
    val WHAT_APP_DOES: Int = R.string.setup_what_app_does

    /** Où vivent les données. Formulé au présent et sans conditionnel : c'est un engagement. */
    @ArrayRes
    val WHERE_DATA_LIVES: Int = R.array.setup_where_data_lives

    /**
     * Ce qui sort réellement de l'appareil, service par service : deux tableaux parallèles (le
     * service, puis ce qui lui est envoyé), à garder de même longueur et dans le même ordre. La
     * liste doit rester exhaustive : GBIF et les tuiles de carte sont faciles à oublier alors
     * qu'ils reçoivent une localisation.
     */
    @ArrayRes
    val EXTERNAL_SERVICES: Int = R.array.setup_external_services

    @ArrayRes
    val EXTERNAL_DATA_SENT: Int = R.array.setup_external_data_sent

    @StringRes
    val PRIVACY_CAVEAT: Int = R.string.setup_privacy_caveat

    @StringRes
    val WHY_KEYS_INTRO: Int = R.string.setup_why_keys_intro

    @StringRes
    val CAN_CHANGE_LATER: Int = R.string.setup_can_change_later

    @StringRes
    val SKIP_HINT: Int = R.string.setup_skip_hint

    /** Récapitulatif : ce que l'app sait faire selon les clés effectivement enregistrées. */
    @StringRes
    val READY_FULL: Int = R.string.setup_ready_full

    @StringRes
    val READY_PLANTNET_ONLY: Int = R.string.setup_ready_plantnet_only

    @StringRes
    val READY_AI_ONLY: Int = R.string.setup_ready_ai_only

    @StringRes
    val READY_NOTHING: Int = R.string.setup_ready_nothing
}

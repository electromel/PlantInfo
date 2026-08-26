package ch.electromel.plantinfo.ui.setup

/**
 * Textes de l'assistant de configuration (§3.1).
 *
 * C'est du **contenu**, pas du code : ces phrases décrivent ce que fait l'application et ce qu'elle
 * envoie à l'extérieur. Elles sont à relire dès qu'un service externe est ajouté ou retiré du
 * pipeline — une promesse de confidentialité fausse est pire que pas de promesse du tout.
 *
 * Ce qui concerne les clés API elles-mêmes vit dans `data/keys/ApiKeyGuide.kt`, source unique
 * partagée avec l'écran Paramètres.
 */
object SetupTexts {

    const val WHAT_APP_DOES: String =
        "PlantInfo identifie une plante, un arbre ou un champignon à partir de vos photos, puis " +
            "réunit sur une fiche ce qu'il faut en savoir : description, état de santé, habitat, " +
            "aire de répartition, comestibilité et toxicité. Vous pouvez ensuite poser vos propres " +
            "questions sur l'espèce identifiée."

    /** Où vivent les données. Formulé au présent et sans conditionnel : c'est un engagement. */
    val WHERE_DATA_LIVES: List<String> = listOf(
        "Vos photos, vos fiches et vos notes restent sur ce téléphone, dans le stockage privé de " +
            "l'application. Rien n'est déposé sur un serveur PlantInfo — il n'en existe pas.",
        "Il n'y a aucun compte à créer, aucun mot de passe, aucune adresse e-mail demandée.",
        "Vos clés API sont chiffrées sur l'appareil et ne sont envoyées qu'au service auquel elles " +
            "appartiennent.",
        "Désinstaller l'application efface l'ensemble : il n'y a pas de copie ailleurs.",
    )

    /**
     * Ce qui sort réellement de l'appareil, service par service. La liste doit rester exhaustive :
     * GBIF et les tuiles de carte sont faciles à oublier alors qu'ils reçoivent une localisation.
     */
    val WHAT_LEAVES_THE_DEVICE: List<Pair<String, String>> = listOf(
        "Pl@ntNet" to
            "les photos que vous soumettez, pour obtenir des propositions d'espèces.",
        "Le fournisseur d'IA que vous choisissez" to
            "les photos, les propositions de Pl@ntNet et, lorsqu'elles sont connues, les " +
                "coordonnées GPS de la prise de vue — ainsi que le texte de vos questions.",
        "GBIF" to
            "le nom scientifique de l'espèce, pour récupérer son aire de répartition (aucune photo, " +
                "aucune de vos positions).",
        "OpenStreetMap" to
            "la zone de carte affichée, uniquement quand vous ouvrez la carte d'une fiche.",
    )

    const val PRIVACY_CAVEAT: String =
        "Ces appels partent de votre téléphone : chaque service voit donc votre adresse IP, comme " +
            "n'importe quelle visite d'un site web. Ce qu'ils en conservent relève de leurs propres " +
            "conditions d'utilisation."

    const val WHY_KEYS_INTRO: String =
        "PlantInfo n'héberge aucun service d'identification : il interroge ceux qui existent déjà, " +
            "avec vos accès à vous. C'est ce qui permet à l'application de ne rien vous facturer et " +
            "de ne rien conserver de son côté."

    const val CAN_CHANGE_LATER: String =
        "Rien n'est définitif : vous pourrez ajouter, remplacer ou effacer une clé à tout moment " +
            "dans les Paramètres, et relancer cet assistant depuis le même écran."

    const val SKIP_HINT: String =
        "Vous pouvez passer cette étape et y revenir plus tard."

    /** Récapitulatif : ce que l'app sait faire selon les clés effectivement enregistrées. */
    const val READY_FULL: String =
        "Tout est en place. Photographiez une plante et lancez l'identification."

    const val READY_PLANTNET_ONLY: String =
        "Vous pouvez identifier des plantes avec Pl@ntNet. Sans clé d'IA, la fiche se limite au nom " +
            "de l'espèce : ni description, ni état de santé, ni champignons, ni questions."

    const val READY_AI_ONLY: String =
        "Vous pouvez identifier avec l'IA seule, champignons compris. Sans Pl@ntNet, il manque la " +
            "vérification botanique qui fiabilise le nom de l'espèce."

    const val READY_NOTHING: String =
        "Aucune clé n'est enregistrée : l'identification ne pourra pas démarrer. Relancez cet " +
            "assistant depuis les Paramètres lorsque vous serez prêt."
}

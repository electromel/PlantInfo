package ch.electromel.plantinfo.data.keys

import ch.electromel.plantinfo.domain.model.AiPricing

/**
 * Mode d'emploi d'une clé API, destiné à un utilisateur qui n'en a jamais créé (§3.1).
 *
 * Le contenu est volontairement séparé de [ApiProvider] : l'énumération décrit *ce qu'est* un
 * fournisseur (préférence de stockage, URL, type IA), ce guide décrit *comment s'en servir*. Le
 * texte évolue avec les interfaces web des fournisseurs, l'énumération non.
 */
data class ApiKeyGuide(
    /** À quoi sert cette clé dans PlantInfo, en une phrase. */
    val role: String,
    /** L'app est-elle inutilisable sans cette clé ? */
    val required: Boolean,
    /** Ce que la clé coûte réellement à l'usage, sans jargon. */
    val cost: String,
    /** Marche à suivre, une étape par élément, dans l'ordre. */
    val steps: List<String>,
    /** Le piège classique sur lequel butent les débutants, ou null. */
    val pitfall: String?,
)

/** Guides par fournisseur. */
object ApiKeyGuides {

    /**
     * Explication commune, affichée avant toute création de clé : sans elle, « clé API » reste un
     * mot creux et l'utilisateur ne sait pas *pourquoi* on lui demande d'aller sur un autre site.
     */
    const val WHAT_IS_A_KEY: String =
        "Une clé API est un mot de passe que vous créez chez un service en ligne (Pl@ntNet, Google, " +
            "Anthropic, OpenAI) et que vous collez ici. Elle autorise PlantInfo à interroger ce " +
            "service en votre nom, depuis votre téléphone. Il n'y a pas de compte PlantInfo : chaque " +
            "clé reste chiffrée sur cet appareil et n'est envoyée qu'au service concerné."

    /** Le minimum vital pour démarrer, quand on ne veut lire qu'une seule phrase. */
    const val MINIMUM_SETUP: String =
        "Pour commencer, deux clés suffisent : Pl@ntNet (identification, gratuite) et Gemini " +
            "(description, santé, champignons). Le palier gratuit de Gemini fonctionne, mais il est " +
            "lent et limité à quelques dizaines de requêtes par jour ; avec la facturation activée, " +
            "comptez ${AiPricing.GEMINI_COST_HINT}. Claude et GPT sont des alternatives " +
            "facultatives, payantes elles aussi."

    /**
     * Ce que Pl@ntNet et une IA font chacun, et pourquoi les deux clés ne se remplacent pas.
     * C'est la question que pose systématiquement quelqu'un à qui l'on demande deux inscriptions.
     */
    const val PLANTNET_VS_AI: String =
        "Pl@ntNet est un service spécialisé : il compare la photo à une base d'observations " +
            "botaniques et propose des espèces, sans rien en dire d'autre. L'IA (Gemini) reprend " +
            "cette proposition, la confronte à la photo, rédige la description, évalue l'état de " +
            "santé, traite les champignons — que Pl@ntNet ne couvre pas — et répond à vos " +
            "questions. Avec Pl@ntNet seul, vous obtenez un nom ; avec l'IA seule, vous perdez la " +
            "vérification botanique."

    fun forProvider(provider: ApiProvider): ApiKeyGuide = when (provider) {
        ApiProvider.PLANTNET -> ApiKeyGuide(
            role = "Identifie l'espèce à partir de la photo. C'est le socle de l'application.",
            required = true,
            cost = "Gratuit : l'inscription donne un quota quotidien d'identifications, suffisant " +
                "pour un usage personnel.",
            steps = listOf(
                "Ouvrez my.plantnet.org et créez un compte (adresse e-mail + mot de passe).",
                "Validez l'e-mail de confirmation reçu dans votre boîte.",
                "Reconnectez-vous, puis ouvrez « Paramètres » (ou « Settings ») de votre compte.",
                "Repérez la ligne « Clé API » / « API key » et copiez la valeur affichée.",
                "Revenez ici et collez-la avec le bouton « coller » du champ ci-dessous.",
            ),
            pitfall = "La clé Pl@ntNet est déjà créée pour vous : il n'y a rien à générer, seulement " +
                "à copier la valeur affichée dans votre compte.",
        )

        ApiProvider.GEMINI -> ApiKeyGuide(
            role = "Décrit la plante, évalue son état de santé, identifie les champignons et répond " +
                "à vos questions.",
            required = false,
            cost = "Le palier gratuit de Google AI Studio suffit pour essayer, mais il est lent aux " +
                "heures chargées et plafonne à quelques dizaines de requêtes par jour ; au-delà, la " +
                "clé cesse de répondre jusqu'au lendemain. En activant la facturation sur le projet " +
                "Google, l'attente disparaît et vous payez ${AiPricing.GEMINI_COST_HINT}.",
            steps = listOf(
                "Ouvrez aistudio.google.com/apikey et connectez-vous avec un compte Google.",
                "Acceptez les conditions d'utilisation de Google AI Studio si elles s'affichent.",
                "Cliquez sur « Créer une clé API » / « Create API key ».",
                "Choisissez un projet Google Cloud quand on vous le demande — le projet proposé par " +
                    "défaut convient.",
                "Copiez la clé affichée (elle commence par « AIza ») et collez-la ci-dessous.",
            ),
            pitfall = "La clé n'est affichée en entier qu'à sa création : copiez-la tout de suite. " +
                "Si vous l'avez perdue, créez-en simplement une nouvelle.",
        )

        ApiProvider.CLAUDE -> ApiKeyGuide(
            role = "Alternative à Gemini pour la description, la santé et les questions. Souvent la " +
                "plus fine sur les espèces délicates.",
            required = false,
            cost = "Payant à l'usage, facturé au nombre de jetons (quelques centimes par " +
                "identification). Il faut créditer le compte avant la première utilisation.",
            steps = listOf(
                "Ouvrez console.anthropic.com et créez un compte.",
                "Dans « Billing » / « Plans », ajoutez du crédit (un montant minimal suffit pour " +
                    "des centaines d'identifications).",
                "Ouvrez « Settings » puis « API keys ».",
                "Cliquez sur « Create key », donnez-lui un nom (par exemple « PlantInfo »).",
                "Copiez la clé affichée (elle commence par « sk-ant- ») et collez-la ci-dessous.",
            ),
            pitfall = "Un abonnement Claude Pro ou Max n'inclut aucun crédit API : sans crédit sur le " +
                "compte développeur, la clé est acceptée mais chaque appel échoue.",
        )

        ApiProvider.OPENAI -> ApiKeyGuide(
            role = "Seconde alternative pour la description, la santé et les questions.",
            required = false,
            cost = "Payant à l'usage, facturé au nombre de jetons. Il faut créditer le compte avant " +
                "la première utilisation.",
            steps = listOf(
                "Ouvrez platform.openai.com et créez un compte.",
                "Dans « Billing », ajoutez du crédit sur le compte.",
                "Ouvrez « API keys » depuis le menu du compte.",
                "Cliquez sur « Create new secret key » et validez.",
                "Copiez la clé affichée (elle commence par « sk- ») et collez-la ci-dessous.",
            ),
            pitfall = "Un abonnement ChatGPT Plus n'inclut aucun crédit API : le compte développeur " +
                "se crédite séparément.",
        )
    }
}

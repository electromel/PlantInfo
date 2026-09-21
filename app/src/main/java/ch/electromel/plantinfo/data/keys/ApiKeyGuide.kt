package ch.electromel.plantinfo.data.keys

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import ch.electromel.plantinfo.R

/**
 * Mode d'emploi d'une clé API, destiné à un utilisateur qui n'en a jamais créé (§3.1).
 *
 * Le contenu est volontairement séparé de [ApiProvider] : l'énumération décrit *ce qu'est* un
 * fournisseur (préférence de stockage, URL, type IA), ce guide décrit *comment s'en servir*. Le
 * texte évolue avec les interfaces web des fournisseurs, l'énumération non.
 *
 * Ce ne sont que des **références** de ressources : les phrases vivent dans les fichiers `strings.xml`
 * et existent dans chaque langue traduite. C'est toujours du contenu, à relire quand l'interface
 * web d'un fournisseur change — mais il faut alors le corriger dans toutes les langues.
 */
data class ApiKeyGuide(
    /** À quoi sert cette clé dans PlantInfo, en une phrase. */
    @StringRes val roleRes: Int,
    /** L'app est-elle inutilisable sans cette clé ? */
    val required: Boolean,
    /**
     * Ce que la clé coûte réellement à l'usage, sans jargon. Certaines de ces phrases attendent le
     * coût annoncé de Gemini en `%1$s` ; les autres ignorent l'argument.
     */
    @StringRes val costRes: Int,
    /** Marche à suivre, une étape par élément du tableau, dans l'ordre. */
    @ArrayRes val stepsRes: Int,
    /** Le piège classique sur lequel butent les débutants, ou null. */
    @StringRes val pitfallRes: Int?,
)

/** Guides par fournisseur. */
object ApiKeyGuides {

    /**
     * Explication commune, affichée avant toute création de clé : sans elle, « clé API » reste un
     * mot creux et l'utilisateur ne sait pas *pourquoi* on lui demande d'aller sur un autre site.
     */
    @StringRes
    val WHAT_IS_A_KEY: Int = R.string.guide_what_is_a_key

    /** Le minimum vital pour démarrer, quand on ne veut lire qu'une seule phrase. */
    @StringRes
    val MINIMUM_SETUP: Int = R.string.guide_minimum_setup

    /**
     * Ce que Pl@ntNet et une IA font chacun, et pourquoi les deux clés ne se remplacent pas.
     * C'est la question que pose systématiquement quelqu'un à qui l'on demande deux inscriptions.
     */
    @StringRes
    val PLANTNET_VS_AI: Int = R.string.guide_plantnet_vs_ai

    fun forProvider(provider: ApiProvider): ApiKeyGuide = when (provider) {
        ApiProvider.PLANTNET -> ApiKeyGuide(
            roleRes = R.string.guide_plantnet_role,
            required = true,
            costRes = R.string.guide_plantnet_cost,
            stepsRes = R.array.guide_plantnet_steps,
            pitfallRes = R.string.guide_plantnet_pitfall,
        )

        ApiProvider.GEMINI -> ApiKeyGuide(
            roleRes = R.string.guide_gemini_role,
            required = false,
            costRes = R.string.guide_gemini_cost,
            stepsRes = R.array.guide_gemini_steps,
            pitfallRes = R.string.guide_gemini_pitfall,
        )

        ApiProvider.CLAUDE -> ApiKeyGuide(
            roleRes = R.string.guide_claude_role,
            required = false,
            costRes = R.string.guide_claude_cost,
            stepsRes = R.array.guide_claude_steps,
            pitfallRes = R.string.guide_claude_pitfall,
        )

        ApiProvider.OPENAI -> ApiKeyGuide(
            roleRes = R.string.guide_openai_role,
            required = false,
            costRes = R.string.guide_openai_cost,
            stepsRes = R.array.guide_openai_steps,
            pitfallRes = R.string.guide_openai_pitfall,
        )
    }
}

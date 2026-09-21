package ch.electromel.plantinfo.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Accès aux chaînes traduites, vu par le code de domaine.
 *
 * Le domaine met en forme des textes destinés à l'utilisateur — avertissement de confusion toxique,
 * résumés pour le PDF et le partage — sans pouvoir dépendre d'Android : ces fonctions sont couvertes
 * par des tests JVM. L'implémentation réelle est [AppStrings] ; les tests en fournissent une
 * factice.
 */
interface StringProvider {
    fun get(@StringRes id: Int): String

    fun get(@StringRes id: Int, vararg args: Any?): String
}

/**
 * [StringProvider] adossé au contexte de l'interface, pour les composables qui appellent du code de
 * domaine attendant un fournisseur de chaînes (avertissement toxique, résumés). Le contexte d'une
 * activité est déjà dans la langue de l'application — inutile de repasser par [AppStrings].
 */
@Composable
fun rememberStringProvider(): StringProvider {
    val context = LocalContext.current
    return remember(context) {
        object : StringProvider {
            override fun get(id: Int): String = context.getString(id)

            override fun get(id: Int, vararg args: Any?): String = context.getString(id, *args)
        }
    }
}

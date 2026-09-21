package ch.electromel.plantinfo.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès aux chaînes traduites depuis les couches sans interface : dépôts, workers, export PDF,
 * partage, notifications.
 *
 * Pourquoi ne pas utiliser directement le contexte applicatif : sur Android 12 et moins, la langue
 * choisie dans les Paramètres n'est appliquée qu'au contexte de l'activité
 * (`MainActivity.attachBaseContext`) ; le contexte applicatif, lui, reste sur la langue du
 * téléphone. Un message de notification lu depuis ce contexte sortirait donc dans la mauvaise
 * langue. [AppLocales.wrap] corrige cela, et le contexte obtenu est gardé tant que la langue ne
 * change pas.
 */
@Singleton
class AppStrings @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : StringProvider {
    private var cachedLanguage: AppLanguage? = null
    private var cachedContext: Context? = null

    /** Langue dans laquelle l'application s'affiche actuellement. */
    val language: AppLanguage
        get() = AppLocales.current(appContext)

    override fun get(@StringRes id: Int): String = context().getString(id)

    override fun get(@StringRes id: Int, vararg args: Any?): String = context().getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
        context().resources.getQuantityString(id, count, *args)

    @Synchronized
    private fun context(): Context {
        val current = language
        if (cachedLanguage != current || cachedContext == null) {
            cachedContext = AppLocales.wrap(appContext)
            cachedLanguage = current
        }
        return cachedContext!!
    }
}

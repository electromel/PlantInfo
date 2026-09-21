package ch.electromel.plantinfo.data.prefs

import android.content.Context
import android.content.SharedPreferences
import ch.electromel.plantinfo.util.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémorise la langue choisie dans les Paramètres, **sur Android 12 et moins seulement** : à partir
 * d'Android 13 c'est le système qui persiste le choix (`LocaleManager`), et ce magasin n'est plus
 * consulté — voir `util/AppLocales`.
 *
 * `SharedPreferences` et non DataStore : la valeur est lue dans `attachBaseContext`, avant que
 * quoi que ce soit d'asynchrone ne puisse tourner, et une lecture bloquante y est la seule option
 * raisonnable.
 *
 * Absence de valeur = suivre la langue du téléphone. C'est l'état par défaut, et le seul moyen de
 * revenir en arrière après un choix explicite.
 */
@Singleton
class LanguageStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("plantinfo_language", Context.MODE_PRIVATE)

    fun selected(): AppLanguage? = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))

    fun save(language: AppLanguage?) {
        prefs.edit().apply {
            if (language == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language.tag)
        }.apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language_tag"
    }
}

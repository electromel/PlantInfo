package ch.electromel.plantinfo.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import ch.electromel.plantinfo.data.prefs.LanguageStore
import java.util.Locale

/**
 * Langues dans lesquelles l'application est traduite (§ multilangue).
 *
 * [tag] doit correspondre au suffixe du dossier de ressources (`values-fr`, `values-de`, …) et à
 * une entrée de `res/xml/locales_config.xml` : les trois listes se corrigent ensemble.
 *
 * [endonym] est le nom de la langue **dans cette langue** — c'est ce qu'attend quelqu'un qui ne
 * comprend pas la langue actuellement affichée et cherche la sienne dans la liste.
 *
 * [aiName] est le nom en anglais, envoyé aux modèles d'IA dans le prompt : c'est la forme qu'ils
 * interprètent le plus sûrement (voir `AiPrompt`).
 */
enum class AppLanguage(val tag: String, val endonym: String, val aiName: String) {
    ENGLISH("en", "English", "English"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    ITALIAN("it", "Italiano", "Italian"),
    SPANISH("es", "Español", "Spanish");

    companion object {
        /** Langue de `values/`, servie quand le téléphone est dans une langue non traduite. */
        val FALLBACK = ENGLISH

        fun fromTag(tag: String?): AppLanguage? {
            val code = tag?.takeIf { it.isNotBlank() }?.substringBefore('-')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == code }
        }
    }
}

/**
 * Choix de langue de l'application : par défaut celle du téléphone, sinon celle choisie dans les
 * Paramètres.
 *
 * Deux mécanismes selon la version d'Android, pour une même promesse :
 * - **Android 13+** : `LocaleManager`. Le système persiste le choix, recrée l'activité et affiche
 *   l'application dans « Paramètres > Langues de l'app » — d'où `android:localeConfig` dans le
 *   manifeste.
 * - **Android 12 et moins** : le choix est mémorisé par [LanguageStore] et appliqué à la main dans
 *   `MainActivity.attachBaseContext` ; c'est à l'appelant de recréer l'activité.
 *
 * Volontairement sans `AppCompatDelegate` : celui-ci n'applique les locales qu'aux
 * `AppCompatActivity`, or l'application n'a qu'une `ComponentActivity` et un thème Material non
 * AppCompat. Passer par AppCompat imposerait de changer le thème pour un bénéfice nul.
 */
object AppLocales {

    /** Langue choisie explicitement, ou `null` si l'application suit le téléphone. */
    fun selected(context: Context): AppLanguage? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            AppLanguage.fromTag(locales?.takeIf { !it.isEmpty }?.get(0)?.language)
        } else {
            LanguageStore(context).selected()
        }

    /**
     * Langue réellement affichée : le choix explicite, sinon la langue du téléphone ramenée aux
     * langues traduites, sinon [AppLanguage.FALLBACK].
     */
    fun current(context: Context): AppLanguage =
        selected(context)
            ?: AppLanguage.fromTag(systemLanguageTag())
            ?: AppLanguage.FALLBACK

    /**
     * Enregistre le choix. `null` = suivre le téléphone.
     *
     * Sur Android 12 et moins, l'activité en cours doit être recréée ensuite pour que les
     * ressources soient rechargées (voir `LanguageSetting` dans l'écran Paramètres).
     */
    fun apply(context: Context, language: AppLanguage?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language.tag)
        } else {
            LanguageStore(context).save(language)
        }
    }

    /**
     * Contexte dont les ressources sont dans la langue de l'application.
     *
     * Appelé depuis `MainActivity.attachBaseContext` (Android 12 et moins), et partout où une
     * couche hors interface lit des chaînes à partir du contexte applicatif — notifications, export
     * PDF, partage : ce contexte-là garde la langue du téléphone et ignorerait le choix interne.
     */
    fun wrap(base: Context): Context {
        val language = current(base)
        if (base.resources.configuration.locales[0].language == language.tag) return base
        val config = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Langue du téléphone, indépendamment de ce que l'application affiche. */
    private fun systemLanguageTag(): String? =
        Resources.getSystem().configuration.locales.takeIf { !it.isEmpty }?.get(0)?.language
}

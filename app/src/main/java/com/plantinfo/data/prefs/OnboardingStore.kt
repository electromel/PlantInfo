package com.plantinfo.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémorise que l'écran d'accueil du premier lancement a été vu (§3.1).
 *
 * Le drapeau est distinct de « une clé est enregistrée » : l'utilisateur qui a lu l'accueil et
 * décidé de configurer plus tard ne doit pas le revoir à chaque ouverture — l'invitation à
 * renseigner les paramètres reste alors portée par les messages d'erreur du pipeline, qui sont
 * contextuels plutôt que modaux.
 */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("plantinfo_onboarding", Context.MODE_PRIVATE)

    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_WELCOME_SEEN, false)

    fun markWelcomeSeen() {
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    private companion object {
        const val KEY_WELCOME_SEEN = "welcome_seen"
    }
}

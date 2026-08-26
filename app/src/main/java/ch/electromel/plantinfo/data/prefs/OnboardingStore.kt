package ch.electromel.plantinfo.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémorise que l'assistant de configuration du premier lancement a été mené à son terme (§3.1).
 *
 * Le drapeau n'est posé qu'en validant le récapitulatif final, pas en ouvrant l'assistant : quitter
 * en cours de route le laisse à faux et l'assistant revient au lancement suivant. Ce n'est pas du
 * harcèlement — toutes les étapes sont passables, et « Passer » depuis la dernière mène directement
 * au récapitulatif : en terminer coûte un appui.
 *
 * Le drapeau est distinct de « une clé est enregistrée » : quelqu'un peut avoir tout lu, tout
 * compris et décidé de ne configurer qu'une partie des clés.
 */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("plantinfo_onboarding", Context.MODE_PRIVATE)

    fun hasCompletedSetup(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETED, false)

    fun markSetupCompleted() {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
    }

    private companion object {
        // Clé volontairement distincte de l'ancien « welcome_seen » (simple fenêtre d'accueil) :
        // l'assistant est un parcours nouveau, il mérite d'être proposé une fois à qui avait déjà
        // fermé l'ancienne fenêtre.
        const val KEY_SETUP_COMPLETED = "setup_completed"
    }
}

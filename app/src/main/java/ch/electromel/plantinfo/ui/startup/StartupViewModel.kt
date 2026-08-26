package ch.electromel.plantinfo.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.keys.KeyHealthMonitor
import ch.electromel.plantinfo.data.keys.KeyProblem
import ch.electromel.plantinfo.data.prefs.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce que l'application doit dire à l'utilisateur au démarrage : l'assistant de configuration tant
 * qu'il n'a pas été mené à son terme, puis — les fois suivantes — les clés enregistrées qui ne
 * fonctionnent plus.
 */
data class StartupUiState(
    val showSetup: Boolean = false,
    val keyProblems: List<KeyProblem> = emptyList(),
)

/**
 * Pilote les deux interventions d'ouverture (§3.1) :
 *
 * 1. **Assistant de configuration** tant qu'il n'a pas été terminé. Aucune vérification de clé n'est
 *    lancée dans ce cas : l'assistant teste lui-même celles qu'on y saisit.
 * 2. **Lancements suivants** : signaler les clés devenues inutilisables (révoquées, sans crédit,
 *    quota épuisé). Le contrôle réel est limité à une fois par jour par [KeyHealthMonitor] ; ici on
 *    se contente de le déclencher et d'observer son verdict.
 *
 * L'alerte se ferme pour la session en cours et revient au lancement suivant tant que le problème
 * persiste : c'est bien « au lancement » que l'utilisateur doit l'apprendre, pas une fois pour
 * toutes.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val onboarding: OnboardingStore,
    private val keyHealth: KeyHealthMonitor,
) : ViewModel() {

    private val showSetup = MutableStateFlow(!onboarding.hasCompletedSetup())
    private val problemsDismissed = MutableStateFlow(false)

    val state: StateFlow<StartupUiState> =
        combine(showSetup, problemsDismissed, keyHealth.problems) { setup, dismissed, problems ->
            StartupUiState(
                showSetup = setup,
                // Une seule intervention à la fois : l'assistant passe devant.
                keyProblems = if (setup || dismissed) emptyList() else problems,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupUiState())

    init {
        if (onboarding.hasCompletedSetup()) {
            viewModelScope.launch { keyHealth.refresh() }
        }
    }

    /**
     * L'assistant vient d'être ouvert : ne pas y renvoyer une seconde fois dans cette session. Le
     * drapeau « terminé » n'est **pas** posé ici — il appartient à l'assistant, qui ne le pose qu'au
     * récapitulatif. Quitter l'assistant en cours de route le fera donc revenir au prochain
     * lancement, comme voulu.
     */
    fun onSetupOpened() {
        showSetup.value = false
        viewModelScope.launch { keyHealth.refresh() }
    }

    fun dismissKeyProblems() {
        problemsDismissed.value = true
    }
}

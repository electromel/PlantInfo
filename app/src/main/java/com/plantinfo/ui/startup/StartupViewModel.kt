package com.plantinfo.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantinfo.data.keys.KeyHealthMonitor
import com.plantinfo.data.keys.KeyProblem
import com.plantinfo.data.prefs.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ce que l'application doit dire à l'utilisateur au démarrage : l'accueil du tout premier
 * lancement, puis — les fois suivantes — les clés enregistrées qui ne fonctionnent plus.
 */
data class StartupUiState(
    val showWelcome: Boolean = false,
    val keyProblems: List<KeyProblem> = emptyList(),
)

/**
 * Pilote les deux messages d'ouverture (§3.1) :
 *
 * 1. **Premier lancement** : inviter à renseigner les paramètres, personne n'ayant de clé à ce
 *    stade. Aucune vérification de clé n'est lancée — il n'y a rien à vérifier.
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

    private val showWelcome = MutableStateFlow(!onboarding.hasSeenWelcome())
    private val problemsDismissed = MutableStateFlow(false)

    val state: StateFlow<StartupUiState> =
        combine(showWelcome, problemsDismissed, keyHealth.problems) { welcome, dismissed, problems ->
            StartupUiState(
                showWelcome = welcome,
                // Une seule fenêtre à la fois : l'accueil du premier lancement passe devant.
                keyProblems = if (welcome || dismissed) emptyList() else problems,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupUiState())

    init {
        if (onboarding.hasSeenWelcome()) {
            viewModelScope.launch { keyHealth.refresh() }
        }
    }

    /** L'utilisateur a lu l'accueil : ne plus le montrer, et vérifier les clés dès maintenant. */
    fun dismissWelcome() {
        onboarding.markWelcomeSeen()
        showWelcome.value = false
        viewModelScope.launch { keyHealth.refresh() }
    }

    fun dismissKeyProblems() {
        problemsDismissed.value = true
    }
}

package ch.electromel.plantinfo.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiKeyTester
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeyTestState
import ch.electromel.plantinfo.data.keys.KeysSnapshot
import ch.electromel.plantinfo.data.prefs.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Une étape de l'assistant. [Key] est la même étape, paramétrée par le fournisseur à configurer. */
sealed interface SetupStep {
    data object Welcome : SetupStep
    data object WhyKeys : SetupStep
    data class Key(val provider: ApiProvider) : SetupStep
    data object Recap : SetupStep
}

/**
 * Sur quoi l'assistant est ouvert. Le parcours complet sert au premier lancement ; les parcours
 * réduits sont appelés depuis un message contextuel (« il manque une clé IA ») ou depuis les
 * Paramètres (« ajouter une clé »), et n'ont aucune raison de refaire l'exposé du début.
 */
object SetupFocus {
    const val ARG = "focus"

    /** Parcours complet du premier lancement. */
    const val ALL = "all"

    /** Uniquement la clé d'IA — Gemini, le fournisseur recommandé. */
    const val LLM = "llm"

    /** Parcours ciblé sur un fournisseur précis (valeur = [ApiProvider.name]). */
    fun provider(provider: ApiProvider): String = provider.name

    /** Fournisseur d'IA proposé par défaut. Voir [SetupTexts] : on n'en propose qu'un. */
    val RECOMMENDED_AI: ApiProvider = ApiProvider.GEMINI

    fun stepsFor(focus: String?): List<SetupStep> = when (focus) {
        null, ALL -> listOf(
            SetupStep.Welcome,
            SetupStep.WhyKeys,
            SetupStep.Key(ApiProvider.PLANTNET),
            SetupStep.Key(RECOMMENDED_AI),
            SetupStep.Recap,
        )
        LLM -> listOf(SetupStep.Key(RECOMMENDED_AI), SetupStep.Recap)
        else -> ApiProvider.entries.firstOrNull { it.name == focus }
            ?.let { listOf(SetupStep.Key(it), SetupStep.Recap) }
            ?: stepsFor(ALL)
    }
}

data class SetupUiState(
    val step: SetupStep = SetupStep.Welcome,
    val index: Int = 0,
    val total: Int = 1,
    val input: String = "",
    val test: KeyTestState = KeyTestState.Idle,
    val keys: KeysSnapshot,
    /** L'assistant est terminé : l'écran peut se refermer. */
    val finished: Boolean = false,
) {
    val isLast: Boolean get() = index >= total - 1
    val canGoBack: Boolean get() = index > 0
}

/**
 * Pilote l'assistant de configuration (§3.1) : suite d'étapes passables, saisie et test des clés,
 * puis récapitulatif.
 *
 * L'indice d'étape passe par le [SavedStateHandle] car créer une clé oblige à quitter l'application
 * pour un navigateur : au retour, le système a pu détruire le processus, et reprendre l'assistant au
 * début serait insupportable. La **clé saisie**, elle, n'y est délibérément pas conservée — un
 * secret n'a rien à faire dans un Bundle susceptible d'être écrit sur le disque, et le presse-papier
 * la contient encore.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val keyTester: ApiKeyTester,
    private val onboarding: OnboardingStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val steps: List<SetupStep> = SetupFocus.stepsFor(savedState[SetupFocus.ARG])

    private val index: StateFlow<Int> = savedState.getStateFlow(KEY_INDEX, 0)
    private val entry = MutableStateFlow(Entry())
    private val finished = MutableStateFlow(false)

    val state: StateFlow<SetupUiState> =
        combine(index, entry, finished, keyStore.state) { i, e, done, keys ->
            val bounded = i.coerceIn(0, steps.lastIndex)
            SetupUiState(
                step = steps[bounded],
                index = bounded,
                total = steps.size,
                input = e.input,
                test = e.test,
                keys = keys,
                finished = done,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SetupUiState(step = steps.first(), total = steps.size, keys = keyStore.state.value),
        )

    /** Étape suivante (bouton « Continuer » comme bouton « Passer » : la même chose côté état). */
    fun next() = goTo(index.value + 1)

    fun back() = goTo(index.value - 1)

    private fun goTo(target: Int) {
        if (target !in steps.indices) return
        savedState[KEY_INDEX] = target
        // Changer d'étape remet la saisie à zéro : chaque étape a son propre fournisseur.
        entry.value = Entry()
    }

    fun onInputChange(text: String) {
        entry.value = Entry(input = text, test = KeyTestState.Idle)
    }

    /** Enregistre puis teste la clé de l'étape courante. Le verdict est reversé au moniteur. */
    fun saveAndTest(provider: ApiProvider) {
        val key = entry.value.input.trim()
        if (key.isBlank()) return
        entry.value = entry.value.copy(test = KeyTestState.Testing)
        viewModelScope.launch {
            val verdict = keyTester.saveAndTest(provider, key)
            // Clé acceptée : on vide le champ pour ne pas laisser un secret affiché à l'écran.
            entry.value = Entry(
                input = if (verdict is KeyTestState.Valid) "" else key,
                test = verdict,
            )
        }
    }

    /** Efface une clé enregistrée pour ce fournisseur (saisie erronée qu'on veut reprendre). */
    fun clearKey(provider: ApiProvider) {
        keyStore.setKey(provider, "")
        entry.value = Entry()
    }

    /**
     * Validation du récapitulatif : c'est **ici seulement** que l'assistant compte pour terminé.
     * Quitter avant ce point le fait revenir au prochain lancement.
     */
    fun finish() {
        onboarding.markSetupCompleted()
        finished.value = true
    }

    private data class Entry(
        val input: String = "",
        val test: KeyTestState = KeyTestState.Idle,
    )

    private companion object {
        const val KEY_INDEX = "setup_step_index"
    }
}

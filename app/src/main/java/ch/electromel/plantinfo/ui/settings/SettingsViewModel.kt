package ch.electromel.plantinfo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiKeyTester
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeyHealthMonitor
import ch.electromel.plantinfo.data.keys.KeyIssue
import ch.electromel.plantinfo.data.keys.KeyTestState
import ch.electromel.plantinfo.data.prefs.SafetySettingsStore
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUiState(
    val provider: ApiProvider,
    val hasStoredKey: Boolean,
    val input: String = "",
    val test: KeyTestState = KeyTestState.Idle,
    /** Motif du dernier verdict « inutilisable », ou null si la clé est saine ou non vérifiée. */
    val problem: KeyIssue? = null,
)

data class SettingsUiState(
    /**
     * Uniquement les fournisseurs dont une clé est enregistrée. Cet écran gère l'**état** des clés ;
     * apprendre à en créer une relève de l'assistant de configuration, seul détenteur du mode
     * d'emploi.
     */
    val providers: List<ProviderUiState> = emptyList(),
    /** Fournisseurs sans clé, proposés derrière le bouton « + » — qui ouvre l'assistant. */
    val addable: List<ApiProvider> = emptyList(),
    val fallbackOrder: List<AiProviderType> = AiProviderType.DEFAULT_FALLBACK_ORDER,
    val freeGeminiOnly: Boolean = true,
    val toxicAlert: ToxicAlertThresholds = ToxicAlertThresholds(),
    val rechecking: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val keyTester: ApiKeyTester,
    private val safetySettings: SafetySettingsStore,
    private val keyHealth: KeyHealthMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.value = buildState()
        // Le verdict d'une clé peut changer hors de cet écran (vérification au lancement, saisie
        // dans l'assistant) : les cartes doivent le refléter sans qu'on ait à retester ici.
        viewModelScope.launch {
            keyHealth.problems.collect { refreshProviders() }
        }
        // Une clé ajoutée par l'assistant doit apparaître ici au retour, sans recréer le ViewModel.
        viewModelScope.launch {
            keyStore.state.collect { refreshProviders() }
        }
    }

    private fun buildState(): SettingsUiState = SettingsUiState(
        providers = storedProviders().map { p -> ProviderUiState(p, true) },
        addable = addableProviders(),
        fallbackOrder = keyStore.fallbackOrder(),
        freeGeminiOnly = keyStore.freeGeminiOnly(),
        toxicAlert = safetySettings.current(),
    ).withProblems()

    private fun storedProviders(): List<ApiProvider> =
        ApiProvider.entries.filter { keyStore.getKey(it) != null }

    private fun addableProviders(): List<ApiProvider> =
        ApiProvider.entries.filter { keyStore.getKey(it) == null }

    /** Recompose la liste des cartes en conservant la saisie et le test en cours de chacune. */
    private fun refreshProviders() {
        _state.update { s ->
            val previous = s.providers.associateBy { it.provider }
            s.copy(
                providers = storedProviders().map { p ->
                    previous[p]?.copy(hasStoredKey = true) ?: ProviderUiState(p, true)
                },
                addable = addableProviders(),
                fallbackOrder = keyStore.fallbackOrder(),
                freeGeminiOnly = keyStore.freeGeminiOnly(),
            ).withProblems()
        }
    }

    /** Reporte sur chaque carte le motif d'inutilisabilité connu du moniteur de santé. */
    private fun SettingsUiState.withProblems(): SettingsUiState {
        val byProvider = keyHealth.problems.value.associate { it.provider to it.issue }
        return copy(providers = providers.map { it.copy(problem = byProvider[it.provider]) })
    }

    /** Retente immédiatement toutes les clés enregistrées, sans attendre le contrôle quotidien. */
    fun recheckKeys() {
        if (_state.value.rechecking) return
        _state.update { it.copy(rechecking = true) }
        viewModelScope.launch {
            keyHealth.refresh(force = true)
            _state.update { it.copy(rechecking = false) }
            refreshProviders()
        }
    }

    /** Score final sous lequel l'avertissement de confusion toxique peut se déclencher. */
    fun setToxicAlertMaxScore(value: Int) {
        safetySettings.setMaxScore(value)
        _state.update { it.copy(toxicAlert = safetySettings.current()) }
    }

    /** Score au-dessus duquel une hypothèse alternative est jugée plausible. */
    fun setToxicAlertMinAlternativeScore(value: Int) {
        safetySettings.setMinAlternativeScore(value)
        _state.update { it.copy(toxicAlert = safetySettings.current()) }
    }

    fun resetToxicAlertThresholds() {
        safetySettings.resetToxicAlertThresholds()
        _state.update { it.copy(toxicAlert = safetySettings.current()) }
    }

    /** Active/désactive le mode « Gemini gratuit seul » (ignore Claude et GPT payants). */
    fun setFreeGeminiOnly(enabled: Boolean) {
        keyStore.setFreeGeminiOnly(enabled)
        _state.update { it.copy(freeGeminiOnly = enabled) }
    }

    fun onInputChange(provider: ApiProvider, text: String) {
        updateProvider(provider) { it.copy(input = text, test = KeyTestState.Idle) }
    }

    /** Remplace une clé existante puis lance immédiatement l'appel de test (§3.1). */
    fun saveAndTest(provider: ApiProvider) {
        val input = _state.value.providers.firstOrNull { it.provider == provider }?.input.orEmpty()
        if (input.isBlank()) return
        updateProvider(provider) { it.copy(test = KeyTestState.Testing) }
        viewModelScope.launch {
            val verdict = keyTester.saveAndTest(provider, input)
            updateProvider(provider) {
                it.copy(
                    test = verdict,
                    hasStoredKey = true,
                    input = if (verdict is KeyTestState.Valid) "" else it.input,
                )
            }
            // L'ordre de repli disponible peut changer quand une clé IA devient valide.
            _state.update { s -> s.copy(fallbackOrder = keyStore.fallbackOrder()) }
            refreshProviders()
        }
    }

    fun clearKey(provider: ApiProvider) {
        keyStore.setKey(provider, "")
        keyHealth.forget(provider)
        refreshProviders()
    }

    fun moveFallback(type: AiProviderType, up: Boolean) {
        val order = _state.value.fallbackOrder.toMutableList()
        val idx = order.indexOf(type)
        if (idx < 0) return
        val target = if (up) idx - 1 else idx + 1
        if (target !in order.indices) return
        order[idx] = order[target].also { order[target] = order[idx] }
        keyStore.setFallbackOrder(order)
        _state.update { it.copy(fallbackOrder = order) }
    }

    private fun updateProvider(provider: ApiProvider, transform: (ProviderUiState) -> ProviderUiState) {
        _state.update { s ->
            s.copy(providers = s.providers.map { if (it.provider == provider) transform(it) else it })
        }
    }
}

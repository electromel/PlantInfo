package ch.electromel.plantinfo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeyHealth
import ch.electromel.plantinfo.data.keys.KeyHealthMonitor
import ch.electromel.plantinfo.data.prefs.SafetySettingsStore
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de test d'une clé (appel de validation immédiat, §3.1). */
sealed interface KeyTestState {
    data object Idle : KeyTestState
    data object Testing : KeyTestState
    data object Valid : KeyTestState
    data class Invalid(val message: String) : KeyTestState
}

data class ProviderUiState(
    val provider: ApiProvider,
    val hasStoredKey: Boolean,
    val input: String = "",
    val test: KeyTestState = KeyTestState.Idle,
    /** Motif du dernier verdict « inutilisable », ou null si la clé est saine ou non vérifiée. */
    val problem: String? = null,
)

data class SettingsUiState(
    /**
     * Uniquement les fournisseurs à montrer : ceux dont une clé est enregistrée, plus celui que
     * l'utilisateur vient d'ajouter via le « + ». Les autres n'encombrent pas l'écran.
     */
    val providers: List<ProviderUiState> = emptyList(),
    /** Fournisseurs sans clé, proposés derrière le bouton « + ». */
    val addable: List<ApiProvider> = emptyList(),
    val fallbackOrder: List<AiProviderType> = AiProviderType.DEFAULT_FALLBACK_ORDER,
    val freeGeminiOnly: Boolean = true,
    val toxicAlert: ToxicAlertThresholds = ToxicAlertThresholds(),
    val rechecking: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val safetySettings: SafetySettingsStore,
    private val aiOrchestrator: AiOrchestrator,
    private val plantNetClient: PlantNetClient,
    private val keyHealth: KeyHealthMonitor,
) : ViewModel() {

    /**
     * Fournisseurs ouverts à la saisie sans clé enregistrée. Vit dans le ViewModel et non dans le
     * stockage : c'est un état d'écran, qui doit disparaître si l'utilisateur quitte sans saisir.
     */
    private var beingAdded: Set<ApiProvider> = emptySet()

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.value = buildState()
        // Le verdict d'une clé peut changer hors de cet écran (vérification au lancement) : la
        // carte doit le refléter sans qu'on ait à retester ici.
        viewModelScope.launch {
            keyHealth.problems.collect { refreshProviders() }
        }
    }

    private fun buildState(): SettingsUiState = SettingsUiState(
        providers = visibleProviders().map { p -> ProviderUiState(p, keyStore.getKey(p) != null) },
        addable = addableProviders(),
        fallbackOrder = keyStore.fallbackOrder(),
        freeGeminiOnly = keyStore.freeGeminiOnly(),
        toxicAlert = safetySettings.current(),
    ).withProblems()

    private fun visibleProviders(): List<ApiProvider> =
        ApiProvider.entries.filter { keyStore.getKey(it) != null || it in beingAdded }

    private fun addableProviders(): List<ApiProvider> =
        ApiProvider.entries.filter { keyStore.getKey(it) == null && it !in beingAdded }

    /** Recompose la liste des cartes en conservant la saisie et le test en cours de chacune. */
    private fun refreshProviders() {
        _state.update { s ->
            val previous = s.providers.associateBy { it.provider }
            s.copy(
                providers = visibleProviders().map { p ->
                    previous[p]?.copy(hasStoredKey = keyStore.getKey(p) != null)
                        ?: ProviderUiState(p, keyStore.getKey(p) != null)
                },
                addable = addableProviders(),
            ).withProblems()
        }
    }

    /** Reporte sur chaque carte le motif d'inutilisabilité connu du moniteur de santé. */
    private fun SettingsUiState.withProblems(): SettingsUiState {
        val byProvider = keyHealth.problems.value.associate { it.provider to it.reason }
        return copy(providers = providers.map { it.copy(problem = byProvider[it.provider]) })
    }

    /** Ouvre une carte de saisie pour un fournisseur encore sans clé (bouton « + »). */
    fun beginAdd(provider: ApiProvider) {
        beingAdded = beingAdded + provider
        refreshProviders()
    }

    /** Referme une carte ouverte par erreur, tant qu'aucune clé n'y a été enregistrée. */
    fun cancelAdd(provider: ApiProvider) {
        beingAdded = beingAdded - provider
        refreshProviders()
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

    /** Enregistre la clé saisie puis lance immédiatement l'appel de test (§3.1). */
    fun saveAndTest(provider: ApiProvider) {
        val input = _state.value.providers.firstOrNull { it.provider == provider }?.input.orEmpty()
        if (input.isBlank()) return
        keyStore.setKey(provider, input)
        beingAdded = beingAdded - provider // la carte tient désormais debout par sa clé enregistrée
        updateProvider(provider) { it.copy(test = KeyTestState.Testing, hasStoredKey = true) }
        viewModelScope.launch {
            val verdict = runTest(provider, input)
            // Le test vient d'être payé : on en fait profiter le moniteur plutôt que de le refaire
            // au prochain lancement — et l'alerte de démarrage disparaît dès la correction.
            keyHealth.record(provider, verdict)
            updateProvider(provider) {
                it.copy(
                    test = verdict.toTestState(),
                    input = if (verdict.health == KeyHealth.VALID) "" else it.input,
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
        beingAdded = beingAdded - provider
        updateProvider(provider) {
            it.copy(hasStoredKey = false, input = "", test = KeyTestState.Idle, problem = null)
        }
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

    private suspend fun runTest(provider: ApiProvider, key: String): KeyHealthMonitor.Verdict =
        if (provider == ApiProvider.PLANTNET) {
            KeyHealthMonitor.verdictFor(plantNetClient.testKey(key))
        } else {
            val type = provider.aiType
            if (type == null) {
                KeyHealthMonitor.Verdict(KeyHealth.UNVERIFIABLE, null)
            } else {
                KeyHealthMonitor.verdictFor(aiOrchestrator.testKey(type, key))
            }
        }

    /**
     * Traduit le verdict en retour d'écran. [KeyHealth.UNVERIFIABLE] n'est pas un échec de la clé :
     * le message doit désigner le réseau ou le service, pas accuser la clé qu'on vient de saisir.
     */
    private fun KeyHealthMonitor.Verdict.toTestState(): KeyTestState = when (health) {
        KeyHealth.VALID -> KeyTestState.Valid
        KeyHealth.INVALID -> KeyTestState.Invalid(reason?.replaceFirstChar { it.uppercase() } ?: "Clé refusée.")
        KeyHealth.UNVERIFIABLE, KeyHealth.UNKNOWN -> KeyTestState.Invalid(
            "Impossible de vérifier la clé pour l'instant (réseau ou service indisponible). " +
                "Elle est enregistrée : réessayez plus tard.",
        )
    }

    private fun updateProvider(provider: ApiProvider, transform: (ProviderUiState) -> ProviderUiState) {
        _state.update { s ->
            s.copy(providers = s.providers.map { if (it.provider == provider) transform(it) else it })
        }
    }
}

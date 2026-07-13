package com.plantinfo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantinfo.data.keys.ApiKeyStore
import com.plantinfo.data.keys.ApiProvider
import com.plantinfo.data.remote.ai.AiFailureReason
import com.plantinfo.data.remote.ai.AiOrchestrator
import com.plantinfo.data.remote.plantnet.PlantNetClient
import com.plantinfo.data.remote.plantnet.PlantNetError
import com.plantinfo.domain.model.AiProviderType
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
)

data class SettingsUiState(
    val providers: List<ProviderUiState> = emptyList(),
    val fallbackOrder: List<AiProviderType> = AiProviderType.DEFAULT_FALLBACK_ORDER,
    val freeGeminiOnly: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val aiOrchestrator: AiOrchestrator,
    private val plantNetClient: PlantNetClient,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private fun buildState(): SettingsUiState = SettingsUiState(
        providers = ApiProvider.entries.map { p ->
            ProviderUiState(provider = p, hasStoredKey = keyStore.getKey(p) != null)
        },
        fallbackOrder = keyStore.fallbackOrder(),
        freeGeminiOnly = keyStore.freeGeminiOnly(),
    )

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
        updateProvider(provider) { it.copy(test = KeyTestState.Testing, hasStoredKey = true) }
        viewModelScope.launch {
            val result = runTest(provider, input)
            updateProvider(provider) {
                it.copy(test = result, input = if (result is KeyTestState.Valid) "" else it.input)
            }
            // L'ordre de repli disponible peut changer quand une clé IA devient valide.
            _state.update { s -> s.copy(fallbackOrder = keyStore.fallbackOrder()) }
        }
    }

    fun clearKey(provider: ApiProvider) {
        keyStore.setKey(provider, "")
        updateProvider(provider) {
            it.copy(hasStoredKey = false, input = "", test = KeyTestState.Idle)
        }
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

    private suspend fun runTest(provider: ApiProvider, key: String): KeyTestState {
        return if (provider == ApiProvider.PLANTNET) {
            when (val e = plantNetClient.testKey(key)) {
                null -> KeyTestState.Valid
                else -> KeyTestState.Invalid(describePlantNet(e))
            }
        } else {
            val type = provider.aiType ?: return KeyTestState.Invalid("Fournisseur inconnu")
            when (val r = aiOrchestrator.testKey(type, key)) {
                null -> KeyTestState.Valid
                else -> KeyTestState.Invalid(describeAi(r))
            }
        }
    }

    private fun describePlantNet(e: PlantNetError): String = when (e) {
        PlantNetError.INVALID_KEY -> "Clé refusée (401/403)."
        PlantNetError.QUOTA -> "Quota atteint."
        PlantNetError.NETWORK -> "Pas de connexion."
        PlantNetError.SERVER -> "Service indisponible."
        else -> "Échec de validation."
    }

    private fun describeAi(r: AiFailureReason): String = when (r) {
        AiFailureReason.INVALID_KEY -> "Clé refusée (401/403)."
        AiFailureReason.QUOTA -> "Quota atteint."
        AiFailureReason.BILLING -> "Clé acceptée mais crédit épuisé : rechargez le compte du fournisseur."
        AiFailureReason.NETWORK -> "Pas de connexion."
        AiFailureReason.SERVER -> "Service indisponible."
        else -> "Échec de validation."
    }

    private fun updateProvider(provider: ApiProvider, transform: (ProviderUiState) -> ProviderUiState) {
        _state.update { s ->
            s.copy(providers = s.providers.map { if (it.provider == provider) transform(it) else it })
        }
    }
}

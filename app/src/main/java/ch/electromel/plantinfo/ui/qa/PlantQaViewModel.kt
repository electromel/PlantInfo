package ch.electromel.plantinfo.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.remote.ai.AiAnswerOutcome
import ch.electromel.plantinfo.data.remote.ai.summary
import ch.electromel.plantinfo.data.repo.PlantQaRepository
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Un échange question/réponse affiché sous le champ de saisie, avec ce que l'appel a coûté.
 * [usage] est null quand le fournisseur ne rapporte pas sa consommation — l'échange s'affiche alors
 * sans ligne de coût plutôt qu'avec un zéro trompeur.
 */
data class QaExchange(
    val question: String,
    val answer: String,
    val provider: AiProviderType,
    val usage: TokenUsage?,
)

/**
 * État de la zone « Questions à l'IA » : historique des échanges de la session, indicateur de
 * chargement et message d'erreur éventuel.
 */
data class PlantQaUiState(
    val exchanges: List<QaExchange> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlantQaViewModel @Inject constructor(
    private val repository: PlantQaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlantQaUiState())
    val state: StateFlow<PlantQaUiState> = _state.asStateFlow()

    fun ask(entity: IdentificationEntity, question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val message = when (val outcome = repository.ask(entity, trimmed)) {
                is AiAnswerOutcome.Success ->
                    _state.value.copy(
                        exchanges = _state.value.exchanges +
                            QaExchange(trimmed, outcome.answer, outcome.provider, outcome.usage),
                        loading = false,
                        error = null,
                    )
                AiAnswerOutcome.NoProvidersConfigured ->
                    _state.value.copy(
                        loading = false,
                        error = "Aucune clé IA configurée : impossible de répondre. Ajoutez une clé " +
                            "dans les paramètres.",
                    )
                is AiAnswerOutcome.AllFailed ->
                    _state.value.copy(
                        loading = false,
                        error = "La réponse a échoué — ${outcome.failures.summary()}.",
                    )
            }
            _state.value = message
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

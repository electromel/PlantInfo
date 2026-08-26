package ch.electromel.plantinfo.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HistoryRepository,
    private val identificationRepository: IdentificationRepository,
    private val keyStore: ApiKeyStore,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    val entity: StateFlow<IdentificationEntity?> =
        repository.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Ce qui manque à cette fiche, recalculé à partir de l'état courant des clés : le conseil
     * disparaît de lui-même dès que la clé manquante est renseignée.
     */
    val advice: StateFlow<FicheAdvice?> =
        combine(entity, keyStore.state) { current, keys ->
            current?.let { FicheAdvice.of(it, keys) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _reanalyzing = MutableStateFlow(false)
    val reanalyzing: StateFlow<Boolean> = _reanalyzing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Valide une hypothèse alternative comme identification principale (§2.4). */
    fun selectAlternative(chosen: SpeciesCandidate) {
        val current = entity.value ?: return
        viewModelScope.launch { repository.selectSpecies(current, chosen) }
    }

    /** Relance Pl@ntNet + IA sur la fiche (ex. IA inaccessible au 1er essai ou clé ajoutée depuis). */
    fun reanalyze() {
        val current = entity.value ?: return
        if (_reanalyzing.value) return
        _reanalyzing.value = true
        viewModelScope.launch {
            when (val outcome = identificationRepository.reanalyzeAndUpdate(current)) {
                is IdentificationOutcome.Success -> _message.value = outcome.infoMessage
                    ?: "Analyse mise à jour (${outcome.result.aiProvider.takeIf { it != AiProviderType.NONE }?.label ?: "Pl@ntNet"})."
                is IdentificationOutcome.Failure -> _message.value = outcome.message
            }
            _reanalyzing.value = false
        }
    }

    fun clearMessage() {
        _message.update { null }
    }
}

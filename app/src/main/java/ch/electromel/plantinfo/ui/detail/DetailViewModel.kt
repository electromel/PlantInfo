package ch.electromel.plantinfo.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.ui.result.FicheAdvice
import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.PdfExporter
import ch.electromel.plantinfo.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HistoryRepository,
    private val identificationRepository: IdentificationRepository,
    private val keyStore: ApiKeyStore,
    private val pdfExporter: PdfExporter,
    private val shareHelper: ShareHelper,
    private val strings: AppStrings,
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

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _reanalyzing = MutableStateFlow(false)
    val reanalyzing: StateFlow<Boolean> = _reanalyzing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Relance Pl@ntNet + IA sur la fiche (ex. IA inaccessible au 1er essai ou clé ajoutée depuis). */
    fun reanalyze() {
        val current = entity.value ?: return
        if (_reanalyzing.value) return
        _reanalyzing.value = true
        viewModelScope.launch {
            when (val outcome = identificationRepository.reanalyzeAndUpdate(current)) {
                is IdentificationOutcome.Success -> _message.value = outcome.infoMessage
                    ?: strings.get(
                        R.string.history_reanalyzed,
                        outcome.result.aiProvider.takeIf { it != AiProviderType.NONE }?.label ?: "Pl@ntNet",
                    )
                is IdentificationOutcome.Failure -> _message.value = outcome.message
            }
            _reanalyzing.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleFavorite() {
        val current = entity.value ?: return
        viewModelScope.launch { repository.setFavorite(current, !current.isFavorite) }
    }

    fun setNotes(notes: String) {
        val current = entity.value ?: return
        viewModelScope.launch { repository.setNotes(current, notes.ifBlank { null }) }
    }

    /** Valide une hypothèse alternative comme identification principale (§2.4). */
    fun selectAlternative(chosen: SpeciesCandidate) {
        val current = entity.value ?: return
        viewModelScope.launch { repository.selectSpecies(current, chosen) }
    }

    /** Partage un résumé texte + la photo via le sélecteur natif Android. */
    fun shareSummary() {
        entity.value?.let { shareHelper.shareSummary(it) }
    }

    /** Génère un PDF de la fiche puis ouvre le sélecteur de partage. */
    fun exportPdf() {
        val current = entity.value ?: return
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            runCatching { pdfExporter.export(current) }
                .onSuccess { shareHelper.sharePdf(it) }
            _exporting.value = false
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = entity.value ?: return
        viewModelScope.launch {
            repository.delete(current)
            onDeleted()
        }
    }
}

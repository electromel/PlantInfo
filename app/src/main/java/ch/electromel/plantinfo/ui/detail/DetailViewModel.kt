package ch.electromel.plantinfo.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.ui.result.FicheViewModel
import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.ImageStorage
import ch.electromel.plantinfo.util.PdfExporter
import ch.electromel.plantinfo.util.ShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Écran Détail (fiche ouverte depuis l'historique) : le comportement commun d'une fiche
 * ([FicheViewModel]), plus ce qui ne concerne que l'historique.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: HistoryRepository,
    identificationRepository: IdentificationRepository,
    imageStorage: ImageStorage,
    keyStore: ApiKeyStore,
    private val pdfExporter: PdfExporter,
    private val shareHelper: ShareHelper,
    strings: AppStrings,
) : FicheViewModel(savedStateHandle, repository, identificationRepository, imageStorage, keyStore, strings) {

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    fun toggleFavorite() {
        val current = entity.value ?: return
        viewModelScope.launch { repository.setFavorite(current, !current.isFavorite) }
    }

    fun setNotes(notes: String) {
        val current = entity.value ?: return
        viewModelScope.launch { repository.setNotes(current, notes.ifBlank { null }) }
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
                .onFailure { showMessage(strings.get(R.string.pdf_export_failed)) }
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

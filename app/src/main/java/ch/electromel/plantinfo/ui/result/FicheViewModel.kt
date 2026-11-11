package ch.electromel.plantinfo.ui.result

import android.net.Uri
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
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ce que les écrans Résultat et Détail font tous deux d'une fiche : l'observer, dire ce qui lui
 * manque, la réanalyser, lui ajouter une photo, valider une autre hypothèse.
 *
 * Ces deux écrans affichent la même fiche avec le même composable (`IdentificationContent`) ; ils
 * partagent donc aussi son comportement, écrit une seule fois ici. Le Détail y ajoute ce qui ne
 * concerne que l'historique (notes, favori, partage, PDF, suppression).
 */
abstract class FicheViewModel(
    savedStateHandle: SavedStateHandle,
    protected val repository: HistoryRepository,
    private val identificationRepository: IdentificationRepository,
    private val imageStorage: ImageStorage,
    keyStore: ApiKeyStore,
    protected val strings: AppStrings,
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

    protected fun showMessage(text: String?) {
        _message.value = text
    }

    fun clearMessage() = showMessage(null)

    /** Valide une hypothèse alternative comme identification principale (§2.4). */
    fun selectAlternative(chosen: SpeciesCandidate) {
        val current = entity.value ?: return
        viewModelScope.launch { repository.selectSpecies(current, chosen) }
    }

    /** Relance Pl@ntNet + IA sur la fiche (ex. IA inaccessible au 1er essai ou clé ajoutée depuis). */
    fun reanalyze() {
        val current = entity.value ?: return
        runReanalysis { identificationRepository.reanalyzeAndUpdate(current) }
    }

    /**
     * Photo complémentaire prise depuis la fiche (identification incertaine) : ajoutée aux photos
     * existantes, puis toute la fiche est réanalysée.
     */
    fun addPhoto(uri: Uri, organ: PhotoOrgan) {
        val current = entity.value ?: return
        runReanalysis {
            // null : photo illisible, l'analyse n'est pas lancée et un message le dit.
            val path = runCatching { imageStorage.saveFromUri(uri) }.getOrNull()
                ?: return@runReanalysis null
            identificationRepository.addPhotoAndReanalyze(current, path, organ)
        }
    }

    private fun runReanalysis(block: suspend () -> IdentificationOutcome?) {
        if (_reanalyzing.value) return
        _reanalyzing.value = true
        viewModelScope.launch {
            when (val outcome = block()) {
                null -> showMessage(strings.get(R.string.fiche_photo_save_failed))
                is IdentificationOutcome.Success -> showMessage(
                    outcome.infoMessage ?: strings.get(
                        R.string.history_reanalyzed,
                        outcome.result.aiProvider.takeIf { it != AiProviderType.NONE }?.label ?: "Pl@ntNet",
                    ),
                )
                is IdentificationOutcome.Failure -> showMessage(outcome.message)
            }
            _reanalyzing.value = false
        }
    }
}

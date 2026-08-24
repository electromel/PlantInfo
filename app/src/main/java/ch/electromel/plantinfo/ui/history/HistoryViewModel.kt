package ch.electromel.plantinfo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Périodes prédéfinies pour le filtre temporel de l'historique. */
enum class DateRange(val label: String) {
    ALL("Toutes dates"),
    LAST_7_DAYS("7 jours"),
    LAST_30_DAYS("30 jours"),
    THIS_YEAR("Cette année");

    /** Borne inférieure en millis epoch (0 = pas de borne). */
    fun fromMillis(now: Long): Long = when (this) {
        ALL -> 0L
        LAST_7_DAYS -> now - 7L * 24 * 3600 * 1000
        LAST_30_DAYS -> now - 30L * 24 * 3600 * 1000
        THIS_YEAR -> {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.MONTH, 0)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            cal.timeInMillis
        }
    }
}

data class HistoryFilters(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val withLocationOnly: Boolean = false,
    val dateRange: DateRange = DateRange.ALL,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val identificationRepository: IdentificationRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    /** Ids des fiches dont l'analyse est en cours de relance (spinner sur la carte). */
    private val _reanalyzingIds = MutableStateFlow<Set<Long>>(emptySet())
    val reanalyzingIds: StateFlow<Set<Long>> = _reanalyzingIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val items: StateFlow<List<IdentificationEntity>> =
        _filters.flatMapLatest { f ->
            repository.observeFiltered(
                query = f.query.trim(),
                favoritesOnly = f.favoritesOnly,
                withLocationOnly = f.withLocationOnly,
                fromMillis = f.dateRange.fromMillis(System.currentTimeMillis()),
                toMillis = Long.MAX_VALUE,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }

    fun toggleFavoritesOnly() = _filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleWithLocationOnly() = _filters.update { it.copy(withLocationOnly = !it.withLocationOnly) }

    fun setDateRange(range: DateRange) = _filters.update { it.copy(dateRange = range) }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll(items.value) }
    }

    fun delete(entity: IdentificationEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    /** Relance Pl@ntNet + IA sur une fiche de l'historique (ex. IA inaccessible au 1er essai). */
    fun reanalyze(entity: IdentificationEntity) {
        if (entity.id in _reanalyzingIds.value) return
        _reanalyzingIds.update { it + entity.id }
        viewModelScope.launch {
            when (val outcome = identificationRepository.reanalyzeAndUpdate(entity)) {
                is IdentificationOutcome.Success -> _message.value = outcome.infoMessage
                    ?: "Analyse mise à jour (${outcome.result.aiProvider.takeIf { it != AiProviderType.NONE }?.label ?: "Pl@ntNet"})."
                is IdentificationOutcome.Failure -> _message.value = outcome.message
            }
            _reanalyzingIds.update { it - entity.id }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

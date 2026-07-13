package com.plantinfo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantinfo.data.repo.RangeRepository
import com.plantinfo.domain.model.SpeciesRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RangeState {
    data object Loading : RangeState
    data class Loaded(val range: SpeciesRange) : RangeState
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val rangeRepository: RangeRepository,
) : ViewModel() {

    private val _range = MutableStateFlow<RangeState>(RangeState.Loading)
    val range: StateFlow<RangeState> = _range.asStateFlow()

    private var loadedFor: String? = null

    /** Charge l'aire de répartition (idempotent pour un même nom scientifique). */
    fun load(scientificName: String) {
        if (loadedFor == scientificName) return
        loadedFor = scientificName
        _range.value = RangeState.Loading
        viewModelScope.launch {
            _range.value = RangeState.Loaded(rangeRepository.getRange(scientificName))
        }
    }
}

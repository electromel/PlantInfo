package ch.electromel.plantinfo.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.KeysSnapshot
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.domain.model.GpsLocation
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.ImageStorage
import ch.electromel.plantinfo.util.LocationProvider
import ch.electromel.plantinfo.work.IdentificationQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CapturePhoto(val path: String, val organ: PhotoOrgan)

data class CaptureUiState(
    val photos: List<CapturePhoto> = emptyList(),
    val gps: GpsLocation? = null,
    val gpsFromPhoto: Boolean = false, // vrai si la position provient de l'EXIF d'une photo importée
    val locating: Boolean = false,
    val identifying: Boolean = false,
    val error: String? = null,
    val info: String? = null,        // message non bloquant (ex. mise en file hors-ligne)
    val navigateToId: Long? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val imageStorage: ImageStorage,
    private val locationProvider: LocationProvider,
    private val repository: IdentificationRepository,
    private val queue: IdentificationQueue,
    private val strings: AppStrings,
    keyStore: ApiKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /**
     * État des clés, exposé à part de [state] : celui-ci est remis à neuf après chaque
     * identification, alors que la configuration, elle, ne change pas parce qu'on a pris une photo.
     */
    val keys: StateFlow<KeysSnapshot> = keyStore.state

    /**
     * Import galerie : la position pertinente est celle inscrite dans la photo (lieu réel de la
     * prise de vue), lue depuis l'EXIF AVANT compression. En l'absence de géotag, on n'utilise PAS
     * la position courante de l'appareil (la photo a pu être prise ailleurs) → aucune localisation.
     */
    fun addGalleryPhoto(uri: Uri) {
        viewModelScope.launch {
            val exifLocation = imageStorage.readExifLocation(uri)
            val path = runCatching { imageStorage.saveFromUri(uri) }.getOrNull() ?: return@launch
            addPhoto(path)
            if (exifLocation != null) {
                _state.update { it.copy(gps = exifLocation, gpsFromPhoto = true) }
            }
        }
    }

    /**
     * Capture caméra : la photo est prise maintenant, ici → la position courante de l'appareil est
     * pertinente. On ne l'écrase que si aucune localisation issue d'une photo n'est déjà présente.
     */
    fun addCameraPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = runCatching { imageStorage.saveFromUri(uri) }.getOrNull() ?: return@launch
            addPhoto(path)
            if (!_state.value.gpsFromPhoto && _state.value.gps == null && !_state.value.locating) {
                _state.update { it.copy(locating = true) }
                val loc = locationProvider.currentLocation()
                _state.update { s ->
                    if (s.gpsFromPhoto) s.copy(locating = false) else s.copy(gps = loc, locating = false)
                }
            }
        }
    }

    private fun addPhoto(path: String) {
        // La première photo prend le port général ; les suivantes ciblent des organes précis.
        val default = if (_state.value.photos.isEmpty()) PhotoOrgan.HABIT else PhotoOrgan.LEAF
        _state.update { it.copy(photos = it.photos + CapturePhoto(path, default)) }
    }

    fun setOrgan(index: Int, organ: PhotoOrgan) {
        _state.update { s ->
            s.copy(photos = s.photos.mapIndexed { i, p -> if (i == index) p.copy(organ = organ) else p })
        }
    }

    fun removePhoto(index: Int) {
        _state.update { s ->
            s.photos.getOrNull(index)?.let { imageStorage.delete(it.path) }
            s.copy(photos = s.photos.filterIndexed { i, _ -> i != index })
        }
    }

    fun identify() {
        val photos = _state.value.photos
        if (photos.isEmpty() || _state.value.identifying) return
        _state.update { it.copy(identifying = true, error = null, info = null) }
        val request = IdentificationRequest(
            photoPaths = photos.map { it.path },
            organs = photos.map { it.organ },
            gps = _state.value.gps,
        )
        viewModelScope.launch {
            when (val outcome = repository.identifyAndSave(request)) {
                is IdentificationOutcome.Success ->
                    _state.update { it.copy(identifying = false, navigateToId = outcome.id) }
                is IdentificationOutcome.Failure -> {
                    if (outcome.queuedForRetry) {
                        // Hors-ligne : on met en file pour traitement différé et on repart à neuf.
                        // Les photos restent dans le stockage interne pour le worker.
                        queue.enqueue(request)
                        _state.value = CaptureUiState(
                            info = strings.get(R.string.capture_queued_offline),
                        )
                    } else {
                        _state.update { it.copy(identifying = false, error = outcome.message) }
                    }
                }
            }
        }
    }

    /** Réinitialise après navigation vers le résultat, pour une prochaine identification. */
    fun onNavigated() {
        _state.value = CaptureUiState()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _state.update { it.copy(info = null) }
    }
}

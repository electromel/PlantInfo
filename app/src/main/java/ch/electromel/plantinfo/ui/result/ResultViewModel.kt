package ch.electromel.plantinfo.ui.result

import androidx.lifecycle.SavedStateHandle
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.repo.HistoryRepository
import ch.electromel.plantinfo.data.repo.IdentificationRepository
import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.ImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Écran Résultat (juste après l'identification) : rien de plus que le comportement commun d'une fiche. */
@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: HistoryRepository,
    identificationRepository: IdentificationRepository,
    imageStorage: ImageStorage,
    keyStore: ApiKeyStore,
    strings: AppStrings,
) : FicheViewModel(savedStateHandle, repository, identificationRepository, imageStorage, keyStore, strings)

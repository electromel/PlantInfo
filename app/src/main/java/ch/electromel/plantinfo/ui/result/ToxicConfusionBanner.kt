package ch.electromel.plantinfo.ui.result

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.electromel.plantinfo.data.prefs.SafetySettingsStore
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import ch.electromel.plantinfo.domain.model.toxicConfusionWarningText
import ch.electromel.plantinfo.ui.components.BannerSeverity
import ch.electromel.plantinfo.ui.components.WarningBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Expose les seuils réglables à la fiche, sans obliger les écrans à les faire descendre. */
@HiltViewModel
class ToxicAlertViewModel @Inject constructor(
    store: SafetySettingsStore,
) : ViewModel() {
    val thresholds: StateFlow<ToxicAlertThresholds> = store.thresholds
}

/**
 * Avertissement permanent de confusion avec une espèce toxique (§2.4). Les seuils viennent des
 * Paramètres : le flux étant observé, un réglage modifié se répercute sur la fiche déjà ouverte.
 *
 * Volontairement non conditionné à `userConfirmed` ni au dépliage du détail du score : une
 * validation manuelle sur un score faible ne rend pas l'hypothèse toxique moins plausible.
 */
@Composable
fun ToxicConfusionBanner(
    result: IdentificationResult,
    modifier: Modifier = Modifier,
    viewModel: ToxicAlertViewModel = hiltViewModel(),
) {
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val warning = result.toxicConfusionWarningText(thresholds) ?: return

    WarningBanner(
        text = warning,
        icon = Icons.Filled.Warning,
        severity = BannerSeverity.DANGER,
        modifier = modifier,
    )
}

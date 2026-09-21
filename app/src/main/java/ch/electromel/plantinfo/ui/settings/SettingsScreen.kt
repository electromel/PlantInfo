package ch.electromel.plantinfo.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeyTestState
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import ch.electromel.plantinfo.ui.components.BannerSeverity
import ch.electromel.plantinfo.ui.components.SectionCard
import ch.electromel.plantinfo.ui.components.WarningBanner
import ch.electromel.plantinfo.ui.setup.SetupFocus
import ch.electromel.plantinfo.util.AppLanguage
import ch.electromel.plantinfo.util.AppLocales
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSetup: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguageCard()

            Text(
                stringResource(R.string.settings_api_keys),
                style = MaterialTheme.typography.titleLarge,
            )

            FreeGeminiCard(
                enabled = state.freeGeminiOnly,
                onToggle = viewModel::setFreeGeminiOnly,
            )

            // Seules les clés renseignées occupent l'écran.
            state.providers.forEach { p ->
                ProviderCard(
                    state = p,
                    onInputChange = { viewModel.onInputChange(p.provider, it) },
                    onSaveAndTest = { viewModel.saveAndTest(p.provider) },
                    onClear = { viewModel.clearKey(p.provider) },
                )
            }

            // Ajouter une clé, c'est repartir dans l'assistant : lui seul porte la marche à suivre.
            AddKeyRow(
                addable = state.addable,
                onAdd = { provider -> onOpenSetup(SetupFocus.provider(provider)) },
            )

            if (state.providers.isNotEmpty()) {
                RecheckKeysButton(rechecking = state.rechecking, onRecheck = viewModel::recheckKeys)
            }

            RelaunchSetupButton { onOpenSetup(SetupFocus.ALL) }

            FallbackOrderCard(
                order = state.fallbackOrder,
                onMove = viewModel::moveFallback,
                enabled = !state.freeGeminiOnly,
            )

            Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleLarge)

            ToxicAlertCard(
                thresholds = state.toxicAlert,
                onMaxScoreChange = viewModel::setToxicAlertMaxScore,
                onMinAlternativeChange = viewModel::setToxicAlertMinAlternativeScore,
                onReset = viewModel::resetToxicAlertThresholds,
            )
        }
    }
}

/**
 * Choix de la langue de l'interface.
 *
 * « Langue du téléphone » est l'état par défaut et reste toujours proposé : c'est le seul moyen de
 * revenir en arrière après un choix explicite. Sur Android 13 et plus, le système enregistre le
 * choix (il apparaît aussi dans « Paramètres > Langues de l'app ») et recrée l'activité lui-même ;
 * en deçà, l'application le mémorise et recrée l'écran pour recharger les ressources.
 */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLocales.selected(context)) }
    var menuOpen by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_language_title)) {
        Text(
            stringResource(R.string.settings_language_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Box {
            OutlinedButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(selected?.endonym ?: stringResource(R.string.settings_language_system))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_language_system)) },
                    onClick = {
                        menuOpen = false
                        selected = null
                        applyLanguage(context, null)
                    },
                )
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        // Chaque langue est écrite dans sa propre langue : celui qui cherche la
                        // sienne ne comprend pas forcément celle affichée à l'écran.
                        text = { Text(language.endonym) },
                        onClick = {
                            menuOpen = false
                            selected = language
                            applyLanguage(context, language)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

private fun applyLanguage(context: Context, language: AppLanguage?) {
    AppLocales.apply(context, language)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Avant Android 13, rien ne recharge les ressources tout seul.
        (context as? Activity)?.recreate()
    }
}

/** Bouton « + » listant les fournisseurs encore sans clé ; masqué quand elles sont toutes saisies. */
@Composable
private fun AddKeyRow(addable: List<ApiProvider>, onAdd: (ApiProvider) -> Unit) {
    if (addable.isEmpty()) return
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_add_key))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            addable.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.label) },
                    onClick = {
                        menuOpen = false
                        onAdd(provider)
                    },
                )
            }
        }
    }
}

/** Relance immédiate du contrôle de validité, qui n'a lieu qu'une fois par jour sans cela. */
@Composable
private fun RecheckKeysButton(rechecking: Boolean, onRecheck: () -> Unit) {
    Column {
        OutlinedButton(onClick = onRecheck, enabled = !rechecking) {
            if (rechecking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (rechecking) R.string.settings_recheck_running else R.string.settings_recheck,
                ),
            )
        }
        Text(
            stringResource(R.string.settings_recheck_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Réglage des deux seuils de l'avertissement « confusion possible avec une espèce toxique ».
 *
 * Le texte de synthèse reformule la règle avec les valeurs courantes plutôt que de laisser
 * l'utilisateur relier deux curseurs abstraits : c'est la phrase qui rend le réglage compréhensible.
 */
@Composable
private fun ToxicAlertCard(
    thresholds: ToxicAlertThresholds,
    onMaxScoreChange: (Int) -> Unit,
    onMinAlternativeChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val defaults = ToxicAlertThresholds()
    SectionCard(title = stringResource(R.string.settings_toxic_alert_title)) {
        Text(
            stringResource(R.string.settings_toxic_alert_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        ThresholdSlider(
            label = stringResource(R.string.settings_toxic_max_score),
            value = thresholds.maxScore,
            range = ToxicAlertThresholds.MAX_SCORE_RANGE,
            onChange = onMaxScoreChange,
            hint = stringResource(R.string.settings_toxic_max_score_hint),
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_toxic_min_alt),
            value = thresholds.minAlternativeScore,
            range = ToxicAlertThresholds.MIN_ALTERNATIVE_RANGE,
            onChange = onMinAlternativeChange,
            hint = stringResource(R.string.settings_toxic_min_alt_hint),
        )

        Text(
            stringResource(
                R.string.settings_toxic_rule,
                thresholds.maxScore,
                thresholds.minAlternativeScore,
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.settings_toxic_caveat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        if (thresholds != defaults) {
            TextButton(onClick = onReset) {
                Text(
                    stringResource(
                        R.string.settings_toxic_reset,
                        defaults.maxScore,
                        defaults.minAlternativeScore,
                    ),
                )
            }
        }
    }
}

/** Curseur entier à pas fixe, avec sa valeur courante affichée en regard du libellé. */
@Composable
private fun ThresholdSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    hint: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // steps = nombre de crans INTERMÉDIAIRES : (bornes / pas) - 1.
        val stepCount = (range.last - range.first) / ToxicAlertThresholds.STEP - 1
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = stepCount,
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** Reprise de l'assistant : la seule porte d'entrée vers les explications, depuis cet écran. */
@Composable
private fun RelaunchSetupButton(onRelaunch: () -> Unit) {
    OutlinedButton(onClick = onRelaunch) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_relaunch_setup))
    }
}

/** Carte du mode « Gemini gratuit seul » : n'utilise que Gemini, ignore Claude et GPT (payants). */
@Composable
private fun FreeGeminiCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_free_gemini_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Les deux paragraphes ont besoin d'un interligne propre : en allemand comme en italien
            // ils font plusieurs lignes chacun et se touchaient.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_free_gemini_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.settings_free_gemini_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * État d'une clé enregistrée : ce qu'elle vaut, comment la remplacer, comment l'effacer. La marche
 * à suivre pour en obtenir une n'est **pas** ici : elle vit dans l'assistant de configuration, seul
 * endroit à la porter, pour que les deux ne divergent jamais.
 */
@Composable
private fun ProviderCard(
    state: ProviderUiState,
    onInputChange: (String) -> Unit,
    onSaveAndTest: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    SectionCard(title = state.provider.label) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasStoredKey) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_key_stored), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.settings_key_absent), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        // Verdict connu du dernier contrôle : la clé est enregistrée mais ne fonctionne plus.
        state.problem?.let { issue ->
            WarningBanner(
                text = stringResource(R.string.settings_key_broken, stringResource(issue.labelRes)),
                icon = Icons.Filled.Error,
                severity = BannerSeverity.DANGER,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = {
                Text(
                    stringResource(
                        if (state.hasStoredKey) R.string.settings_key_replace
                        else R.string.settings_key_paste_label,
                    ),
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { onInputChange(it.trim()) }
                }) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = stringResource(R.string.settings_key_paste_action),
                    )
                }
            },
        )

        // Retour du test de clé
        when (val t = state.test) {
            KeyTestState.Testing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.settings_key_testing), style = MaterialTheme.typography.bodySmall)
            }
            KeyTestState.Valid -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_key_valid), color = MaterialTheme.colorScheme.primary)
            }
            is KeyTestState.Invalid -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                Text(t.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            KeyTestState.Idle -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveAndTest, enabled = state.input.isNotBlank()) {
                Text(stringResource(R.string.settings_key_save_and_test))
            }
            if (state.hasStoredKey) {
                OutlinedButton(onClick = onClear) { Text(stringResource(R.string.settings_key_clear)) }
            }
        }
    }
}

@Composable
private fun FallbackOrderCard(
    order: List<AiProviderType>,
    onMove: (AiProviderType, Boolean) -> Unit,
    enabled: Boolean,
) {
    SectionCard(title = stringResource(R.string.settings_fallback_title)) {
        Text(
            stringResource(
                if (enabled) R.string.settings_fallback_body else R.string.settings_fallback_disabled,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.5f),
        )
        order.forEachIndexed { index, type ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_fallback_position, index + 1, type.label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
                IconButton(onClick = { onMove(type, true) }, enabled = enabled && index > 0) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_move_up),
                    )
                }
                IconButton(onClick = { onMove(type, false) }, enabled = enabled && index < order.lastIndex) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_move_down),
                    )
                }
            }
        }
    }
}

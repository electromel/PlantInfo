package ch.electromel.plantinfo.ui.settings

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.electromel.plantinfo.data.keys.ApiKeyGuide
import ch.electromel.plantinfo.data.keys.ApiKeyGuides
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.ToxicAlertThresholds
import ch.electromel.plantinfo.ui.components.BannerSeverity
import ch.electromel.plantinfo.ui.components.SectionCard
import ch.electromel.plantinfo.ui.components.WarningBanner
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Paramètres") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Clés API",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Vos clés sont chiffrées et stockées uniquement sur cet appareil. Seule la clé Pl@ntNet " +
                    "est indispensable ; les clés IA activent le diagnostic et l'enrichissement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            // Aide générale, dépliée d'office tant qu'aucune clé n'est enregistrée : c'est
            // exactement le moment où l'utilisateur ne sait pas ce qu'on lui demande.
            NoviceHelpCard(expandedByDefault = state.providers.none { it.hasStoredKey })

            FreeGeminiCard(
                enabled = state.freeGeminiOnly,
                onToggle = viewModel::setFreeGeminiOnly,
            )

            // Seules les clés renseignées (et celle en cours d'ajout) occupent l'écran.
            state.providers.forEach { p ->
                ProviderCard(
                    state = p,
                    onInputChange = { viewModel.onInputChange(p.provider, it) },
                    onSaveAndTest = { viewModel.saveAndTest(p.provider) },
                    onClear = { viewModel.clearKey(p.provider) },
                    onCancelAdd = { viewModel.cancelAdd(p.provider) },
                )
            }

            AddKeyRow(addable = state.addable, onAdd = viewModel::beginAdd)

            if (state.providers.any { it.hasStoredKey }) {
                RecheckKeysButton(rechecking = state.rechecking, onRecheck = viewModel::recheckKeys)
            }

            FallbackOrderCard(
                order = state.fallbackOrder,
                onMove = viewModel::moveFallback,
                enabled = !state.freeGeminiOnly,
            )

            Text("Sécurité", style = MaterialTheme.typography.titleLarge)

            ToxicAlertCard(
                thresholds = state.toxicAlert,
                onMaxScoreChange = viewModel::setToxicAlertMaxScore,
                onMinAlternativeChange = viewModel::setToxicAlertMinAlternativeScore,
                onReset = viewModel::resetToxicAlertThresholds,
            )
        }
    }
}

/** Explication de ce qu'est une clé API et du strict minimum à configurer, pour un débutant. */
@Composable
private fun NoviceHelpCard(expandedByDefault: Boolean) {
    var expanded by remember(expandedByDefault) { mutableStateOf(expandedByDefault) }
    SectionCard(title = "Je n'ai jamais créé de clé API") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Explications pas à pas",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExpandToggle(expanded) { expanded = !expanded }
        }
        if (expanded) {
            Text(ApiKeyGuides.WHAT_IS_A_KEY, style = MaterialTheme.typography.bodyMedium)
            Text(
                ApiKeyGuides.MINIMUM_SETUP,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Chaque clé ajoutée ci-dessous propose sa propre marche à suivre, écran par écran.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
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
            Text("Ajouter une clé")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            addable.forEach { provider ->
                val guide = ApiKeyGuides.forProvider(provider)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(provider.label)
                            Text(
                                if (guide.required) "Indispensable" else guide.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    },
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
            Text(if (rechecking) "Vérification…" else "Revérifier mes clés")
        }
        Text(
            "Les clés sont contrôlées automatiquement une fois par jour au lancement. Chaque " +
                "vérification consomme une requête chez le fournisseur.",
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
    SectionCard(title = "Avertissement d'espèce toxique") {
        Text(
            "Avertit lorsqu'une identification n'est pas assurée alors qu'une espèce toxique reste " +
                "une hypothèse possible.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ThresholdSlider(
            label = "Avertir si le score est inférieur à",
            value = thresholds.maxScore,
            range = ToxicAlertThresholds.MAX_SCORE_RANGE,
            onChange = onMaxScoreChange,
            hint = "Plus haut = avertir plus souvent. À 100, l'avertissement apparaît dès qu'une " +
                "hypothèse toxique est présente, même sur une identification très sûre.",
        )
        ThresholdSlider(
            label = "Ne retenir que les hypothèses au-dessus de",
            value = thresholds.minAlternativeScore,
            range = ToxicAlertThresholds.MIN_ALTERNATIVE_RANGE,
            onChange = onMinAlternativeChange,
            hint = "Plus bas = avertir plus souvent. À 0, même une hypothèse très peu probable " +
                "déclenche l'avertissement.",
        )

        Text(
            "Règle actuelle : avertir quand le score final est inférieur à ${thresholds.maxScore} " +
                "et qu'une hypothèse toxique dépasse ${thresholds.minAlternativeScore}.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Ce réglage ne change que le moment où l'avertissement s'affiche : il ne rend jamais une " +
                "espèce plus sûre. Ne consommez jamais une plante ou un champignon sauvage sans " +
                "confirmation par un expert.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        if (thresholds != defaults) {
            TextButton(onClick = onReset) {
                Text("Revenir aux valeurs par défaut (${defaults.maxScore} et ${defaults.minAlternativeScore})")
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

/** Carte du mode « Gemini gratuit seul » : n'utilise que Gemini, ignore Claude et GPT (payants). */
@Composable
private fun FreeGeminiCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SectionCard(title = "IA gratuite (Gemini seul)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "N'utilise que Gemini pour l'identification et les questions.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Claude et GPT sont ignorés : leurs API sont payantes (l'abonnement Claude Pro " +
                        "n'inclut pas de crédits API). Désactivez cette option seulement après avoir " +
                        "rechargé un compte Anthropic ou OpenAI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ProviderCard(
    state: ProviderUiState,
    onInputChange: (String) -> Unit,
    onSaveAndTest: () -> Unit,
    onClear: () -> Unit,
    onCancelAdd: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val guide = ApiKeyGuides.forProvider(state.provider)
    // L'aide est ouverte d'office pour une clé qu'on vient d'ajouter : c'est là qu'on en a besoin.
    var helpExpanded by remember(state.provider) { mutableStateOf(!state.hasStoredKey) }

    SectionCard(title = state.provider.label) {
        Text(
            guide.role,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasStoredKey) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Clé enregistrée", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Aucune clé enregistrée", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        // Verdict connu du dernier contrôle : la clé est enregistrée mais ne fonctionne plus.
        state.problem?.let { reason ->
            WarningBanner(
                text = "Cette clé ne fonctionne plus : $reason.",
                icon = Icons.Filled.Error,
                severity = BannerSeverity.DANGER,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text(if (state.hasStoredKey) "Remplacer la clé API" else "Coller la clé API") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { onInputChange(it.trim()) }
                }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Coller depuis le presse-papier")
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
                Text("Test de la clé…", style = MaterialTheme.typography.bodySmall)
            }
            KeyTestState.Valid -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Clé valide", color = MaterialTheme.colorScheme.primary)
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
                Text("Enregistrer et tester")
            }
            if (state.hasStoredKey) {
                OutlinedButton(onClick = onClear) { Text("Effacer") }
            } else {
                // Carte ouverte par le « + » : on doit pouvoir la refermer sans rien saisir.
                OutlinedButton(onClick = onCancelAdd) { Text("Annuler") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Comment obtenir cette clé ?",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            ExpandToggle(helpExpanded) { helpExpanded = !helpExpanded }
        }
        if (helpExpanded) {
            KeyGuideBlock(guide = guide, createKeyUrl = state.provider.createKeyUrl, label = state.provider.label)
        }
    }
}

/** Marche à suivre numérotée, coût réel et piège classique d'un fournisseur donné. */
@Composable
private fun KeyGuideBlock(guide: ApiKeyGuide, createKeyUrl: String, label: String) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            guide.cost,
            style = MaterialTheme.typography.bodyMedium,
            color = if (guide.required) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            },
        )
        guide.steps.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        guide.pitfall?.let {
            WarningBanner(
                text = it,
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = { uriHandler.openUri(createKeyUrl) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ouvrir la page $label", fontWeight = FontWeight.Medium)
        }
    }
}

/** Chevron d'ouverture/fermeture, avec une description accessible qui dit l'action, pas l'état. */
@Composable
private fun ExpandToggle(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Masquer l'aide" else "Afficher l'aide",
        )
    }
}

@Composable
private fun FallbackOrderCard(
    order: List<AiProviderType>,
    onMove: (AiProviderType, Boolean) -> Unit,
    enabled: Boolean,
) {
    SectionCard(title = "Ordre de repli IA") {
        Text(
            if (enabled) {
                "En cas d'échec (clé invalide, quota, réseau), l'app essaie le fournisseur suivant."
            } else {
                "Sans effet tant que « IA gratuite (Gemini seul) » est activé : seul Gemini est essayé."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.5f),
        )
        order.forEachIndexed { index, type ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}. ${type.label}", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
                IconButton(onClick = { onMove(type, true) }, enabled = enabled && index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Monter")
                }
                IconButton(onClick = { onMove(type, false) }, enabled = enabled && index < order.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descendre")
                }
            }
        }
    }
}

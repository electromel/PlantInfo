package ch.electromel.plantinfo.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import ch.electromel.plantinfo.data.keys.KeyTestState
import ch.electromel.plantinfo.data.keys.KeysSnapshot
import ch.electromel.plantinfo.ui.components.BannerSeverity
import ch.electromel.plantinfo.ui.components.SectionCard
import ch.electromel.plantinfo.ui.components.WarningBanner

/**
 * Assistant de configuration plein écran (§3.1) : ce que fait l'application et ce qu'elle envoie,
 * à quoi servent les clés API, puis leur création pas à pas, et un récapitulatif.
 *
 * Plein écran et non modal : la création d'une clé oblige à sortir vers un navigateur, et une
 * fenêtre de dialogue supporterait mal cet aller-retour. Chaque étape est passable — l'assistant
 * informe, il ne retient pas en otage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onExit: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onExit()
    }

    // Le retour système recule d'une étape plutôt que de tout quitter, tant qu'il en reste une.
    BackHandler(enabled = state.canGoBack) { viewModel.back() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.step.title()) },
                    navigationIcon = {
                        IconButton(onClick = { if (state.canGoBack) viewModel.back() else onExit() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (state.canGoBack) "Étape précédente" else "Fermer",
                            )
                        }
                    },
                    actions = {
                        Text(
                            "${state.index + 1} / ${state.total}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                )
                LinearProgressIndicator(
                    progress = { (state.index + 1).toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            SetupBottomBar(
                state = state,
                onSkip = viewModel::next,
                onContinue = viewModel::next,
                onFinish = viewModel::finish,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val step = state.step) {
                SetupStep.Welcome -> WelcomeStep()
                SetupStep.WhyKeys -> WhyKeysStep()
                is SetupStep.Key -> KeyStep(
                    provider = step.provider,
                    hasStoredKey = state.keys.present[step.provider] == true,
                    input = state.input,
                    test = state.test,
                    onInputChange = viewModel::onInputChange,
                    onSaveAndTest = { viewModel.saveAndTest(step.provider) },
                    onClear = { viewModel.clearKey(step.provider) },
                )
                SetupStep.Recap -> RecapStep(state.keys)
            }
        }
    }
}

private fun SetupStep.title(): String = when (this) {
    SetupStep.Welcome -> "Bienvenue"
    SetupStep.WhyKeys -> "Pourquoi une clé API"
    is SetupStep.Key -> "Clé ${provider.label}"
    SetupStep.Recap -> "Vous êtes prêt"
}

/** Barre d'action du bas. Le bouton « Passer » n'apparaît que là où il y a quelque chose à faire. */
@Composable
private fun SetupBottomBar(
    state: SetupUiState,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step is SetupStep.Key) {
                TextButton(onClick = onSkip) { Text("Passer") }
                Spacer(Modifier.width(8.dp))
            }
            if (state.isLast) {
                Button(onClick = onFinish) { Text("Terminer") }
            } else {
                Button(onClick = onContinue) { Text("Continuer") }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(SetupTexts.WHAT_APP_DOES, style = MaterialTheme.typography.bodyLarge)

    SectionCard("Vos données restent sur cet appareil", Modifier.fillMaxWidth()) {
        SetupTexts.WHERE_DATA_LIVES.forEach { Bullet(it) }
    }

    SectionCard("Ce qui sort de l'appareil, et pour qui", Modifier.fillMaxWidth()) {
        SetupTexts.WHAT_LEAVES_THE_DEVICE.forEach { (service, sent) ->
            Column {
                Text(
                    service,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(sent, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            SetupTexts.PRIVACY_CAVEAT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun WhyKeysStep() {
    Text(SetupTexts.WHY_KEYS_INTRO, style = MaterialTheme.typography.bodyLarge)

    SectionCard("Qu'est-ce qu'une clé API ?", Modifier.fillMaxWidth()) {
        Text(ApiKeyGuides.WHAT_IS_A_KEY, style = MaterialTheme.typography.bodyMedium)
    }

    SectionCard("Pl@ntNet et l'IA ne font pas la même chose", Modifier.fillMaxWidth()) {
        Text(ApiKeyGuides.PLANTNET_VS_AI, style = MaterialTheme.typography.bodyMedium)
    }

    SectionCard("Ce que cela coûte", Modifier.fillMaxWidth()) {
        Text(
            ApiKeyGuides.MINIMUM_SETUP,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            SetupTexts.CAN_CHANGE_LATER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/**
 * Étape de saisie, paramétrée par le fournisseur : c'est la même pour Pl@ntNet, Gemini, Claude ou
 * GPT. C'est ce qui permet aux Paramètres d'ouvrir l'assistant sur un fournisseur quelconque sans
 * dupliquer un mode d'emploi.
 */
@Composable
private fun KeyStep(
    provider: ApiProvider,
    hasStoredKey: Boolean,
    input: String,
    test: KeyTestState,
    onInputChange: (String) -> Unit,
    onSaveAndTest: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val guide = ApiKeyGuides.forProvider(provider)

    Text(guide.role, style = MaterialTheme.typography.bodyLarge)

    SectionCard("Marche à suivre", Modifier.fillMaxWidth()) {
        KeyGuideBlock(guide = guide, createKeyUrl = provider.createKeyUrl, label = provider.label)
    }

    SectionCard("Coller la clé", Modifier.fillMaxWidth()) {
        if (hasStoredKey) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Une clé est déjà enregistrée.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(if (hasStoredKey) "Remplacer la clé" else "Clé ${provider.label}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { clipboard.getText()?.text?.let { onInputChange(it.trim()) } }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Coller depuis le presse-papier")
                }
            },
        )

        KeyTestFeedback(test)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveAndTest,
                enabled = input.isNotBlank() && test !is KeyTestState.Testing,
            ) {
                Text("Enregistrer et tester")
            }
            if (hasStoredKey) {
                OutlinedButton(onClick = onClear) { Text("Effacer") }
            }
        }

        Text(
            SetupTexts.SKIP_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** Retour du test de clé : le seul endroit qui dise si la clé fonctionne vraiment. */
@Composable
private fun KeyTestFeedback(test: KeyTestState) {
    when (test) {
        KeyTestState.Idle -> Unit
        KeyTestState.Testing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
            Text(
                test.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
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

/**
 * Récapitulatif : ce que l'application saura faire avec les clés effectivement enregistrées. Le
 * message décrit les capacités réelles plutôt que de féliciter — quelqu'un qui a tout passé doit
 * lire ici que l'identification ne fonctionnera pas.
 */
@Composable
private fun RecapStep(keys: KeysSnapshot) {
    val summary = when {
        keys.hasPlantNet && keys.hasAi -> SetupTexts.READY_FULL
        keys.hasPlantNet -> SetupTexts.READY_PLANTNET_ONLY
        keys.hasAi -> SetupTexts.READY_AI_ONLY
        else -> SetupTexts.READY_NOTHING
    }

    SectionCard("Vos clés", Modifier.fillMaxWidth()) {
        RecapLine("Pl@ntNet — identification des plantes", keys.hasPlantNet)
        RecapLine("IA — description, santé, champignons, questions", keys.hasAi)
    }

    Text(summary, style = MaterialTheme.typography.bodyLarge)

    Text(
        SetupTexts.CAN_CHANGE_LATER,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun RecapLine(label: String, present: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (present) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (present) "Configuré" else "Non configuré",
            tint = if (present) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

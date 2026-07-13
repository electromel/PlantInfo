package com.plantinfo.ui.settings

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantinfo.data.keys.ApiProvider
import com.plantinfo.domain.model.AiProviderType
import com.plantinfo.ui.components.SectionCard

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

            FreeGeminiCard(
                enabled = state.freeGeminiOnly,
                onToggle = viewModel::setFreeGeminiOnly,
            )

            state.providers.forEach { p ->
                ProviderCard(
                    state = p,
                    onInputChange = { viewModel.onInputChange(p.provider, it) },
                    onSaveAndTest = { viewModel.saveAndTest(p.provider) },
                    onClear = { viewModel.clearKey(p.provider) },
                )
            }

            FallbackOrderCard(
                order = state.fallbackOrder,
                onMove = viewModel::moveFallback,
                enabled = !state.freeGeminiOnly,
            )
        }
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
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    SectionCard(title = state.provider.label) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasStoredKey) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Clé enregistrée", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Aucune clé enregistrée", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text("Coller la clé API") },
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
            }
        }

        TextButton(onClick = { uriHandler.openUri(state.provider.createKeyUrl) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Obtenir une clé ${state.provider.label}", fontWeight = FontWeight.Medium)
        }
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

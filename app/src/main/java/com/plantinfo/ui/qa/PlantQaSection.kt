package com.plantinfo.ui.qa

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.domain.model.costText
import com.plantinfo.domain.model.tokensText
import com.plantinfo.ui.components.SectionCard

/**
 * Zone « Poser une question à l'IA » affichée sous la fiche d'identification (§Q&A).
 * L'utilisateur saisit une question au clavier ou par dictée vocale ; la réponse de l'IA tient
 * compte des informations de la plante et du lieu de prise de vue (construits côté repository).
 */
@Composable
fun PlantQaSection(
    entity: IdentificationEntity,
    modifier: Modifier = Modifier,
    viewModel: PlantQaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var question by rememberSaveable(entity.id) { mutableStateOf("") }

    // Dictée vocale via l'UI système de reconnaissance vocale (aucune permission micro requise ici).
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                question = if (question.isBlank()) spoken else "$question $spoken"
            }
        }
    }

    SectionCard("Poser une question à l'IA", modifier) {
        Text(
            "Posez une question sur cette plante (entretien, comestibilité, confusions possibles, " +
                "floraison…). La réponse tient compte des informations connues et du lieu de la prise de vue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        OutlinedTextField(
            value = question,
            onValueChange = {
                question = it
                if (state.error != null) viewModel.dismissError()
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("Votre question…") },
            trailingIcon = {
                IconButton(onClick = { launchSpeech(context, speechLauncher) }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Dicter la question")
                }
            },
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Button(
                onClick = {
                    viewModel.ask(entity, question)
                    question = ""
                },
                enabled = !state.loading && question.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Demander")
            }
        }

        state.error?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Historique des échanges de la session (le plus récent en bas) : la réponse est posée
        // dans un encart doux pour distinguer visuellement question et réponse.
        if (state.exchanges.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.exchanges.forEach { exchange ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            exchange.question,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            exchange.answer,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                        )
                        // Coût de *cette* question, sous la réponse : chaque échange se paie
                        // séparément, un total de section masquerait la question qui a coûté cher.
                        exchange.usage?.let { usage ->
                            Text(
                                buildString {
                                    append(exchange.provider.label)
                                    append(" · ")
                                    append(usage.tokensText())
                                    usage.costText()?.let { append(" · ~").append(it) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun launchSpeech(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Posez votre question")
    }
    try {
        launcher.launch(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Reconnaissance vocale indisponible sur cet appareil.",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

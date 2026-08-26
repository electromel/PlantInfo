package ch.electromel.plantinfo.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.keys.KeyProblem

/**
 * Signale au lancement les clés enregistrées qui ne fonctionnent plus (§3.1). Chaque clé porte sa
 * raison réelle (refus, crédit épuisé, quota) : « clé invalide » seul enverrait l'utilisateur en
 * régénérer une alors que le compte est simplement à sec.
 */
@Composable
fun KeyProblemDialog(
    problems: List<KeyProblem>,
    onFix: (ApiProvider) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = {
            Text(if (problems.size == 1) "Une clé API ne fonctionne plus" else "Des clés API ne fonctionnent plus")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                problems.forEach { problem ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                problem.provider.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(problem.reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "Corrigez la clé concernée dans les Paramètres, ou continuez : l'application se " +
                        "rabattra sur les fournisseurs encore disponibles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = {
            // Une seule clé fautive : on emmène directement dans l'assistant, sur ce fournisseur —
            // la marche à suivre y est déjà écrite. Plusieurs : les Paramètres les montrent toutes.
            val single = problems.singleOrNull()
            if (single != null) {
                TextButton(onClick = { onFix(single.provider) }) { Text("Corriger cette clé") }
            } else {
                TextButton(onClick = onOpenSettings) { Text("Ouvrir les paramètres") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Continuer") }
        },
    )
}

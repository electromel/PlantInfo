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
import androidx.compose.material.icons.filled.Key
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
import ch.electromel.plantinfo.data.keys.ApiKeyGuides
import ch.electromel.plantinfo.data.keys.KeyProblem

/**
 * Accueil du tout premier lancement (§3.1) : dit à quoi servent les clés API, ce qu'il faut au
 * minimum, et emmène directement dans les Paramètres.
 *
 * Modal et bloquant à dessein : sans clé, la première photo n'aboutirait qu'à un message d'erreur,
 * ce qui est une bien plus mauvaise entrée en matière.
 */
@Composable
fun WelcomeDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Key, contentDescription = null) },
        title = { Text("Bienvenue dans PlantInfo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Avant la première identification, il faut renseigner au moins une clé API dans " +
                        "les Paramètres.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(ApiKeyGuides.WHAT_IS_A_KEY, style = MaterialTheme.typography.bodyMedium)
                Text(
                    ApiKeyGuides.MINIMUM_SETUP,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Les Paramètres contiennent une aide pas à pas pour chaque fournisseur si vous " +
                        "n'avez jamais créé de clé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Ouvrir les paramètres") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Plus tard") }
        },
    )
}

/**
 * Signale au lancement les clés enregistrées qui ne fonctionnent plus (§3.1). Chaque clé porte sa
 * raison réelle (refus, crédit épuisé, quota) : « clé invalide » seul enverrait l'utilisateur en
 * régénérer une alors que le compte est simplement à sec.
 */
@Composable
fun KeyProblemDialog(
    problems: List<KeyProblem>,
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
            TextButton(onClick = onOpenSettings) { Text("Ouvrir les paramètres") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Continuer") }
        },
    )
}

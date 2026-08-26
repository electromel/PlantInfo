package ch.electromel.plantinfo.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.electromel.plantinfo.ui.components.SectionCard
import ch.electromel.plantinfo.ui.result.IdentificationContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    identificationId: Long,
    onBack: () -> Unit,
    onOpenSetup: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val entity by viewModel.entity.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val reanalyzing by viewModel.reanalyzing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val advice by viewModel.advice.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Détail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reanalyze() }, enabled = entity != null && !reanalyzing) {
                        if (reanalyzing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Relancer l'analyse")
                        }
                    }
                    val fav = entity?.isFavorite == true
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (fav) "Retirer des favoris" else "Ajouter aux favoris",
                        )
                    }
                    IconButton(onClick = { viewModel.shareSummary() }) {
                        Icon(Icons.Filled.Share, contentDescription = "Partager")
                    }
                    IconButton(onClick = { viewModel.exportPdf() }, enabled = !exporting) {
                        if (exporting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exporter en PDF")
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                    }
                },
            )
        },
    ) { padding ->
        val current = entity
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            IdentificationContent(
                entity = current,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                onSelectAlternative = viewModel::selectAlternative,
                onReanalyze = viewModel::reanalyze,
                reanalyzing = reanalyzing,
                advice = advice,
                onOpenSetup = onOpenSetup,
            ) {
                // En-tête spécifique au détail : notes personnelles éditables.
                var notes by remember(current.id) { mutableStateOf(current.notes.orEmpty()) }
                SectionCard("Notes personnelles", Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ajouter une note…") },
                    )
                    TextButton(onClick = { viewModel.setNotes(notes) }) { Text("Enregistrer la note") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette identification ?") },
            text = { Text("La fiche et ses photos seront supprimées définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

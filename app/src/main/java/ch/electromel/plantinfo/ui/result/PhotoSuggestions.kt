package ch.electromel.plantinfo.ui.result

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.domain.model.ComplementaryPhotoRequest
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.ui.theme.WarningContainer
import ch.electromel.plantinfo.ui.theme.WarningContainerDark
import ch.electromel.plantinfo.ui.theme.WarningOnContainer
import ch.electromel.plantinfo.ui.theme.WarningOnContainerDark

/**
 * Photos qui permettraient de trancher une identification incertaine, telles que proposées par
 * l'IA (organe + ce qu'il faut cadrer et pourquoi). Posée juste sous les avertissements
 * d'incertitude, dans la même teinte : c'est la réponse à « que faire de cette incertitude ? ».
 *
 * Chaque suggestion porte son bouton : la photo prise est ajoutée à la fiche avec l'organe demandé,
 * puis toute la fiche est réanalysée. Sans [onAddPhoto] (ou photos déjà au maximum accepté par
 * Pl@ntNet), les suggestions restent affichées comme simples conseils.
 */
@Composable
internal fun PhotoSuggestionsCard(
    suggestions: List<ComplementaryPhotoRequest>,
    canAddPhoto: Boolean,
    reanalyzing: Boolean,
    onAddPhoto: ((Uri, PhotoOrgan) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    val context = LocalContext.current
    // L'appareil photo du téléphone plutôt qu'un aperçu maison dans un Dialog : plein écran, fiable,
    // et l'utilisateur y retrouve ses réglages (mise au point, flash, zoom). L'organe demandé est
    // mémorisé le temps de l'aller-retour vers l'application photo — y compris si Android recrée
    // l'activité entre-temps, ce qui arrive souvent quand l'application photo prend la mémoire.
    var pendingOrgan by rememberSaveable { mutableStateOf<PhotoOrgan?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val organ = pendingOrgan
        val uri = pendingUri
        pendingOrgan = null
        pendingUri = null
        if (saved && organ != null && uri != null) onAddPhoto?.invoke(uri, organ)
    }

    fun launchCamera(organ: PhotoOrgan) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "complement_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingOrgan = organ
        pendingUri = uri
        takePicture.launch(uri)
    }

    // L'app déclare la permission CAMERA (aperçu de l'écran de capture) : sans elle, Android refuse
    // aussi de lancer l'application photo.
    var organAwaitingPermission by rememberSaveable { mutableStateOf<PhotoOrgan?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val organ = organAwaitingPermission
        organAwaitingPermission = null
        if (granted && organ != null) launchCamera(organ)
    }

    fun openCamera(organ: PhotoOrgan) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(organ)
        } else {
            organAwaitingPermission = organ
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val dark = isSystemInDarkTheme()
    val bg = if (dark) WarningContainerDark else WarningContainer
    val fg = if (dark) WarningOnContainerDark else WarningOnContainer
    val buttonsEnabled = onAddPhoto != null && canAddPhoto

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = fg)
            Text(
                stringResource(R.string.photo_suggestions_title),
                color = fg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(stringResource(R.string.photo_suggestions_intro), color = fg, style = MaterialTheme.typography.bodyMedium)

        val (current, seasonal) = suggestions.partition { it.period == null }

        current.forEach { suggestion ->
            val organLabel = stringResource(suggestion.organ.labelRes)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // « Autre » n'apprend rien : la raison dit alors seule quoi cadrer.
                if (suggestion.organ != PhotoOrgan.OTHER) {
                    Text(organLabel, color = fg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Text(suggestion.reason, color = fg, style = MaterialTheme.typography.bodyMedium)
                if (buttonsEnabled) {
                    OutlinedButton(onClick = { openCamera(suggestion.organ) }, enabled = !reanalyzing) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (suggestion.organ != PhotoOrgan.OTHER) {
                                stringResource(R.string.photo_suggestions_take_organ, organLabel)
                            } else {
                                stringResource(R.string.photo_suggestions_take)
                            },
                        )
                    }
                }
            }
        }

        // Organe hors saison (fleur, fruit…) : une ligne discrète avec sa période, et un simple lien
        // au cas où la plante en porterait déjà.
        seasonal.forEach { suggestion ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.photo_suggestions_seasonal,
                        stringResource(suggestion.organ.labelRes),
                        suggestion.period.orEmpty(),
                    ),
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(suggestion.reason, color = fg, style = MaterialTheme.typography.bodySmall)
                if (buttonsEnabled) {
                    TextButton(
                        onClick = { openCamera(suggestion.organ) },
                        enabled = !reanalyzing,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.photo_suggestions_seasonal_take))
                    }
                }
            }
        }

        when {
            reanalyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fiche_reanalyzing), color = fg, style = MaterialTheme.typography.bodySmall)
            }
            onAddPhoto != null && !canAddPhoto -> Text(
                stringResource(R.string.photo_suggestions_max_reached),
                color = fg,
                style = MaterialTheme.typography.bodySmall,
            )
            buttonsEnabled -> Text(
                stringResource(R.string.photo_suggestions_note),
                color = fg.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

package ch.electromel.plantinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

private const val MAX_ZOOM = 6f

/**
 * Photo en plein écran avec zoom par pincement et déplacement.
 * Double appui : revient au zoom d'origine (image ajustée) ; si déjà au zoom d'origine, ferme le
 * visualiseur et revient à l'écran précédent (comme le bouton retour système).
 */
@Composable
fun FullscreenPhotoViewer(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        // Empêche de faire sortir l'image de l'écran : la translation est bornée à la partie
        // agrandie qui déborde du conteneur.
        fun clampOffset(candidate: Offset, s: Float): Offset {
            val maxX = containerSize.width * (s - 1f) / 2f
            val maxY = containerSize.height * (s - 1f) / 2f
            return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        // Zoom centré sur le point de pincement : le contenu sous les doigts reste
                        // sous les doigts pendant le changement d'échelle.
                        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                        val d = centroid - center
                        val zoomedOffset = d - (d - offset) * (newScale / scale)
                        scale = newScale
                        offset = clampOffset(zoomedOffset + pan, newScale)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                onDismiss()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = "Photo en plein écran",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

package com.plantinfo.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plantinfo.domain.model.PhotoOrgan
import com.plantinfo.ui.components.FullscreenPhotoViewer
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onResultReady: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // La caméra est masquée par défaut : elle ne s'ouvre qu'à l'appui sur l'icône, et se referme
    // dès qu'une photo est prise — les photos occupent alors tout l'espace disponible.
    var cameraActive by rememberSaveable { mutableStateOf(false) }

    // Photo affichée en plein écran (zoom par pincement) après un appui sur une photo prise.
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
    fullscreenPhoto?.let { FullscreenPhotoViewer(path = it, onDismiss = { fullscreenPhoto = null }) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { cameraGranted = it }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* les résultats sont lus à la demande via checkSelfPermission */ }

    // Import via le sélecteur de documents (SAF) : contrairement au sélecteur de photos, il fournit
    // un Uri résoluble vers MediaStore, ce qui permet — avec ACCESS_MEDIA_LOCATION — de lire le
    // géotag EXIF non masqué de la photo.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.addGalleryPhoto(it) } }

    // Au premier affichage : demander caméra + localisation + accès aux médias (READ_MEDIA_IMAGES /
    // READ_EXTERNAL_STORAGE) + géolocalisation des médias (ACCESS_MEDIA_LOCATION, nécessaire pour lire
    // le géotag EXIF des photos importées ; elle n'est accordable qu'avec un accès média).
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermLauncher.launch(Manifest.permission.CAMERA)
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Notifier l'utilisateur quand une identification différée (file hors-ligne) aboutit.
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Navigation vers le résultat quand l'identification aboutit.
    LaunchedEffect(state.navigateToId) {
        state.navigateToId?.let {
            onResultReady(it)
            viewModel.onNavigated()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.info) {
        state.info?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    cameraActive && cameraGranted -> CameraPreview(
                        onPhotoCaptured = { uri ->
                            viewModel.addCameraPhoto(uri)
                            cameraActive = false
                        },
                        onClose = { cameraActive = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                    cameraActive -> CameraPermissionPlaceholder {
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                    else -> PhotoBoard(
                        photos = state.photos,
                        onOpenCamera = {
                            cameraActive = true
                            if (!cameraGranted) cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOrganChange = viewModel::setOrgan,
                        onRemove = viewModel::removePhoto,
                        onPhotoClick = { fullscreenPhoto = it },
                    )
                }

                // Bandeau d'état GPS en surimpression.
                LocationChip(
                    text = when {
                        state.locating -> "Localisation…"
                        state.gps != null -> "GPS ✓"
                        else -> "GPS indisponible"
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }

            CaptureControls(
                state = state,
                showThumbnails = cameraActive,
                onGallery = { galleryLauncher.launch(arrayOf("image/*")) },
                onOrganChange = viewModel::setOrgan,
                onRemove = viewModel::removePhoto,
                onIdentify = viewModel::identify,
                onPhotoClick = { fullscreenPhoto = it },
            )
        }
    }
}

/**
 * Zone principale quand la caméra est masquée : icône d'appareil photo (au centre si aucune photo,
 * en bouton flottant sinon) et photos déjà prises affichées en grand.
 */
@Composable
private fun PhotoBoard(
    photos: List<CapturePhoto>,
    onOpenCamera: () -> Unit,
    onOrganChange: (Int, PhotoOrgan) -> Unit,
    onRemove: (Int) -> Unit,
    onPhotoClick: (String) -> Unit,
) {
    if (photos.isEmpty()) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .clickable(onClickLabel = "Ouvrir la caméra") { onOpenCamera() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "Ouvrir la caméra",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Appuyez pour photographier une plante,\nou importez depuis la galerie.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            LazyRow(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                itemsIndexed(photos) { index, photo ->
                    LargePhotoCard(
                        path = photo.path,
                        organ = photo.organ,
                        onOrganChange = { onOrganChange(index, it) },
                        onRemove = { onRemove(index) },
                        onClick = { onPhotoClick(photo.path) },
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }
            }
            FloatingActionButton(
                onClick = onOpenCamera,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Ouvrir la caméra")
            }
        }
    }
}

/** Photo en grand format avec sélecteur d'organe et bouton de retrait. */
@Composable
private fun LargePhotoCard(
    path: String,
    organ: PhotoOrgan,
    onOrganChange: (PhotoOrgan) -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).width(280.dp)) {
            AsyncImage(
                model = File(path),
                contentDescription = "Photo — appuyer pour agrandir",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable { onClick() },
            )
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Retirer la photo",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(28.dp)
                    .clickable { onRemove() },
            )
        }
        OrganSelector(organ = organ, onOrganChange = onOrganChange)
    }
}

@Composable
private fun CameraPreview(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply { bindToLifecycle(lifecycleOwner) }
    }
    // L'aperçu est monté/démonté à la demande : libérer la caméra dès qu'il disparaît.
    DisposableEffect(controller) {
        onDispose { controller.unbind() }
    }
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Fermer la caméra sans prendre de photo.
        Icon(
            Icons.Filled.Close,
            contentDescription = "Fermer la caméra",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(6.dp)
                .size(24.dp)
                .clickable { onClose() },
        )
        // Obturateur : anneau blanc + disque intérieur, style appareil photo classique.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(7.dp)
                .background(Color.White, CircleShape)
                .clickable(onClickLabel = "Prendre la photo") {
                    val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                    val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(file).build()
                    controller.takePicture(
                        options,
                        executor,
                        object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(
                                output: androidx.camera.core.ImageCapture.OutputFileResults,
                            ) {
                                onPhotoCaptured(Uri.fromFile(file))
                            }

                            override fun onError(exc: androidx.camera.core.ImageCaptureException) = Unit
                        },
                    )
                },
        )
    }
}

@Composable
private fun CameraPermissionPlaceholder(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Autorisez la caméra pour photographier une plante.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant) { Text("Autoriser la caméra") }
    }
}

@Composable
private fun LocationChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CaptureControls(
    state: CaptureUiState,
    showThumbnails: Boolean,
    onGallery: () -> Unit,
    onOrganChange: (Int, PhotoOrgan) -> Unit,
    onRemove: (Int) -> Unit,
    onIdentify: () -> Unit,
    onPhotoClick: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Vignettes uniquement quand la caméra occupe la zone principale ; sinon les photos y sont
        // déjà affichées en grand.
        if (showThumbnails && state.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.photos) { index, photo ->
                    PhotoThumb(
                        path = photo.path,
                        organ = photo.organ,
                        onOrganChange = { onOrganChange(index, it) },
                        onRemove = { onRemove(index) },
                        onClick = { onPhotoClick(photo.path) },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onGallery) {
                Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Galerie")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onIdentify,
                enabled = state.photos.isNotEmpty() && !state.identifying,
            ) {
                if (state.identifying) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Analyse…")
                } else {
                    Text(if (state.photos.size > 1) "Identifier (${state.photos.size} photos)" else "Identifier")
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    path: String,
    organ: PhotoOrgan,
    onOrganChange: (PhotoOrgan) -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AsyncImage(
                model = File(path),
                contentDescription = "Photo — appuyer pour agrandir",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable { onClick() },
            )
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Retirer la photo",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(22.dp)
                    .clickable { onRemove() },
            )
        }
        OrganSelector(organ = organ, onOrganChange = onOrganChange)
    }
}

/** Menu déroulant du type d'organe photographié, partagé entre vignettes et photos en grand. */
@Composable
private fun OrganSelector(organ: PhotoOrgan, onOrganChange: (PhotoOrgan) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(4.dp)) {
            Text(organ.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PhotoOrgan.entries.forEach { o ->
                DropdownMenuItem(text = { Text(o.label) }, onClick = {
                    onOrganChange(o); menuOpen = false
                })
            }
        }
    }
}

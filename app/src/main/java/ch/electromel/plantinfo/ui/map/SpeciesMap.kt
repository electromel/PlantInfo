package ch.electromel.plantinfo.ui.map

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.domain.model.LatLng
import ch.electromel.plantinfo.domain.model.SpeciesRange
import ch.electromel.plantinfo.util.GeoUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Carte interactive (osmdroid) centrée sur le lieu de la prise de vue (§2.4).
 * - Marqueur au point de capture ; si la précision GPS est faible, un cercle de rayon d'incertitude
 *   est tracé à la place d'un point exact.
 * - Une zone semi-transparente (enveloppe des occurrences GBIF) apparaît en dézoomant et montre les
 *   régions plus larges où l'espèce est connue. Absente si GBIF ne retourne aucune donnée.
 *
 * Quand [latitude]/[longitude] sont nulles (photo importée sans coordonnées EXIF), la carte reste
 * affichée mais centrée sur l'aire de répartition GBIF de l'espèce, sans marqueur de prise de vue.
 */
@Composable
fun SpeciesMap(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    scientificName: String,
    title: String,
    modifier: Modifier = Modifier,
    gbifKey: Long? = null,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rangeState by viewModel.range.collectAsStateWithLifecycle()

    LaunchedEffect(scientificName) { viewModel.load(scientificName, gbifKey) }

    val hasCapturePoint = latitude != null && longitude != null

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            if (hasCapturePoint) {
                controller.setZoom(13.0)
                controller.setCenter(GeoPoint(latitude!!, longitude!!))
            } else {
                // Vue large par défaut (Suisse) en attendant le recentrage sur l'aire de répartition.
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(46.8, 8.23))
            }
            // Empêche le défilement vertical de la fiche de voler les gestes de déplacement de la carte.
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    // Gestion du cycle de vie osmdroid (nécessaire pour le rafraîchissement des tuiles).
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    val range = (rangeState as? RangeState.Loaded)?.range

    // Sans point de capture, recentre une seule fois la carte sur l'aire de répartition GBIF.
    var centeredOnRange by remember { mutableStateOf(false) }
    LaunchedEffect(range, hasCapturePoint) {
        if (!hasCapturePoint && !centeredOnRange && range?.hasData == true) {
            boundingBoxOf(range.points)?.let { box ->
                mapView.post { runCatching { mapView.zoomToBoundingBox(box, false, 48) } }
                centeredOnRange = true
            }
        }
    }

    // Carte masquée uniquement quand il n'y a ni point de capture ni aire de répartition connue.
    val noLocationAtAll = !hasCapturePoint && range != null && !range.hasData

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (noLocationAtAll) {
            Text(
                stringResource(R.string.map_no_location_at_all),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp)),
                update = { view ->
                    drawOverlays(view, latitude, longitude, accuracyMeters, title, range)
                },
            )
        }

        // Légende / avertissement selon l'état.
        when (rangeState) {
            RangeState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "  " + stringResource(R.string.map_loading_range),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is RangeState.Loaded -> {
                val text = stringResource(
                    when {
                        range?.hasData == true -> R.string.map_range_legend
                        hasCapturePoint -> R.string.map_no_range_with_point
                        else -> R.string.map_no_range
                    },
                )
                Text(text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

/** Boîte englobante des occurrences (avec une petite marge), ou null si aucun point. */
private fun boundingBoxOf(points: List<LatLng>): BoundingBox? {
    if (points.isEmpty()) return null
    var north = points.first().lat
    var south = points.first().lat
    var east = points.first().lng
    var west = points.first().lng
    for (p in points) {
        if (p.lat > north) north = p.lat
        if (p.lat < south) south = p.lat
        if (p.lng > east) east = p.lng
        if (p.lng < west) west = p.lng
    }
    // Marge pour éviter que les points extrêmes touchent le bord ; garde une taille minimale.
    val latPad = ((north - south) * 0.1).coerceAtLeast(0.5)
    val lngPad = ((east - west) * 0.1).coerceAtLeast(0.5)
    return BoundingBox(north + latPad, east + lngPad, south - latPad, west - lngPad)
}

private fun drawOverlays(
    map: MapView,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    title: String,
    range: SpeciesRange?,
) {
    map.overlays.clear()

    // 1. Aire de répartition (dessinée en premier pour rester sous le marqueur).
    if (range != null && range.hasData) {
        val hull = GeoUtils.convexHull(range.points)
        if (hull.size >= 3) {
            val polygon = Polygon(map).apply {
                points = hull.map { GeoPoint(it.lat, it.lng) }
                setFillColor(AndroidColor.argb(60, 46, 125, 50))       // vert semi-transparent
                outlinePaint.color = AndroidColor.argb(160, 46, 125, 50)
                outlinePaint.strokeWidth = 3f
            }
            map.overlays.add(polygon)
        }
    }

    // 2. Marqueur de prise de vue (uniquement si des coordonnées sont disponibles).
    if (latitude != null && longitude != null) {
        val center = GeoPoint(latitude, longitude)

        // Rayon d'incertitude si précision GPS faible, sinon marqueur ponctuel.
        val lowAccuracy = (accuracyMeters ?: 0f) > 50f
        if (lowAccuracy && accuracyMeters != null) {
            val circle = Polygon(map).apply {
                points = Polygon.pointsAsCircle(center, accuracyMeters.toDouble())
                setFillColor(AndroidColor.argb(50, 21, 101, 192))     // bleu semi-transparent
                outlinePaint.color = AndroidColor.argb(180, 21, 101, 192)
                outlinePaint.strokeWidth = 3f
            }
            map.overlays.add(circle)
        }

        val marker = Marker(map).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
        }
        map.overlays.add(marker)
    }

    map.invalidate()
}

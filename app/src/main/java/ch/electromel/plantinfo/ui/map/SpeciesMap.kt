package ch.electromel.plantinfo.ui.map

import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import ch.electromel.plantinfo.data.remote.gbif.GbifDensityTileSource
import ch.electromel.plantinfo.domain.model.LatLng
import ch.electromel.plantinfo.util.GeoUtils
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Carte interactive (osmdroid) du lieu de la prise de vue et de l'aire de répartition (§2.4).
 * - Marqueur au point de capture ; si la précision GPS est faible, un cercle de rayon d'incertitude
 *   est tracé à la place d'un point exact.
 * - Des hexagones verts (tuiles de densité GBIF, [GbifDensityTileSource]) ne couvrent que les
 *   endroits où l'espèce a été observée. Absents si GBIF ne retourne aucune donnée.
 *
 * Le cadrage initial englobe le point de capture **et** les occurrences GBIF voisines
 * ([GeoUtils.framingPoints]), pour voir les deux d'emblée sans dézoomer à la main. Tant que GBIF n'a
 * pas répondu (ou s'il ne connaît pas l'espèce), la vue reste zoomée sur le point de capture.
 *
 * Quand [latitude]/[longitude] sont nulles (photo importée sans coordonnées EXIF), la carte reste
 * affichée mais cadrée sur l'aire de répartition GBIF de l'espèce, sans marqueur de prise de vue.
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

    // Zones où l'espèce a été observée : tuiles de densité GBIF, superposées au fond de carte.
    val usageKey = range?.takeIf { it.hasData }?.usageKey
    val densityOverlay = remember(usageKey) {
        usageKey?.let { key ->
            val provider = MapTileProviderBasic(context, GbifDensityTileSource(key)).apply {
                // Seul le fournisseur du fond de carte redessine la vue à l'arrivée d'une tuile : sans
                // ce rappel, les hexagones restent invisibles jusqu'au prochain geste sur la carte.
                setTileRequestCompleteHandler(SimpleInvalidationHandler(mapView))
            }
            TilesOverlay(provider, context).apply {
                // Tuiles transparentes : sans cela osmdroid peint un fond gris quadrillé pendant le
                // chargement, qui masquerait la carte.
                loadingBackgroundColor = AndroidColor.TRANSPARENT
                loadingLineColor = AndroidColor.TRANSPARENT
                // Hexagones translucides : les frontières et les noms de lieux restent lisibles dessous.
                setColorFilter(ColorMatrixColorFilter(ColorMatrix().apply { setScale(1f, 1f, 1f, DENSITY_ALPHA) }))
            }
        }
    }
    DisposableEffect(densityOverlay) { onDispose { densityOverlay?.onDetach(mapView) } }

    // Une fois l'aire de répartition connue, cadre une seule fois la carte pour qu'on y voie à la fois
    // le lieu de prise de vue (s'il existe) et les occurrences GBIF voisines. Sans cela, la vue
    // resterait zoomée sur le point de capture et l'aire de répartition serait hors champ.
    var framedOnRange by remember { mutableStateOf(false) }
    LaunchedEffect(range, latitude, longitude) {
        if (!framedOnRange && range?.hasData == true) {
            val captured = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
            boundingBoxOf(GeoUtils.framingPoints(captured, range.points))?.let { box ->
                mapView.post { runCatching { mapView.zoomToBoundingBox(box, false, 48) } }
                framedOnRange = true
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
                    drawOverlays(view, latitude, longitude, accuracyMeters, title, densityOverlay)
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
                        densityOverlay != null -> R.string.map_range_legend
                        // Ancien cache sans clé GBIF et GBIF injoignable : pas de tuiles à afficher.
                        range?.hasData == true -> R.string.map_range_unavailable
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

/** Opacité des hexagones d'observation : assez pleine pour se lire, assez claire pour voir le fond. */
private const val DENSITY_ALPHA = 0.6f

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
    densityOverlay: TilesOverlay?,
) {
    map.overlays.clear()

    // 1. Zones d'observation (dessinées en premier pour rester sous le marqueur).
    densityOverlay?.let { map.overlays.add(it) }

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

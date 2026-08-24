package ch.electromel.plantinfo.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import ch.electromel.plantinfo.domain.model.GpsLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Récupère la position au moment de la prise de vue (§2.2) : latitude, longitude, altitude et
 * précision rapportée par le capteur (base du rayon d'incertitude affiché sur la carte).
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Position courante, ou null si permission absente / localisation indisponible. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): GpsLocation? {
        if (!hasPermission()) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(15_000)
            .build()
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(request, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(
                        loc?.let {
                            GpsLocation(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = if (it.hasAltitude()) it.altitude else null,
                                accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                            )
                        },
                    )
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}

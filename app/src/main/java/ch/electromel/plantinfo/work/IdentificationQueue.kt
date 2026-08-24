package ch.electromel.plantinfo.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Met une identification en file d'attente pour traitement différé dès le retour du réseau (§3).
 * Les photos étant déjà enregistrées dans le stockage interne, seuls leurs chemins (et le contexte
 * GPS) transitent par WorkManager.
 */
@Singleton
class IdentificationQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue(request: IdentificationRequest) {
        val builder = Data.Builder()
            .putStringArray(IdentificationWorker.KEY_PHOTOS, request.photoPaths.toTypedArray())
            .putStringArray(
                IdentificationWorker.KEY_ORGANS,
                request.organs.map { it.name }.toTypedArray(),
            )
            .putBoolean(IdentificationWorker.KEY_HAS_GPS, request.gps != null)
        request.gps?.let { gps ->
            builder.putDouble(IdentificationWorker.KEY_LAT, gps.latitude)
            builder.putDouble(IdentificationWorker.KEY_LNG, gps.longitude)
            builder.putDouble(IdentificationWorker.KEY_ALT, gps.altitude ?: Double.NaN)
            builder.putDouble(
                IdentificationWorker.KEY_ACC,
                gps.accuracyMeters?.toDouble() ?: Double.NaN,
            )
        }

        val work = OneTimeWorkRequestBuilder<IdentificationWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInputData(builder.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // Chaque prise de vue est une tâche distincte : nom unique horodaté, jamais remplacée.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "identification_${System.currentTimeMillis()}",
            ExistingWorkPolicy.KEEP,
            work,
        )
    }
}

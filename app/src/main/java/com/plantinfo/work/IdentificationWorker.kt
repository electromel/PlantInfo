package com.plantinfo.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plantinfo.data.repo.IdentificationRepository
import com.plantinfo.domain.model.GpsLocation
import com.plantinfo.domain.model.IdentificationOutcome
import com.plantinfo.domain.model.IdentificationRequest
import com.plantinfo.domain.model.PhotoOrgan
import com.plantinfo.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Traite une identification différée mise en file d'attente en mode hors-ligne (§3). WorkManager ne
 * lance ce worker que lorsqu'une connexion est disponible (contrainte réseau) ; à la réussite,
 * l'utilisateur est notifié. Un échec réseau renvoie retry() pour un nouvel essai ultérieur.
 */
@HiltWorker
class IdentificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IdentificationRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val request = parseRequest() ?: return Result.failure()
        return when (val outcome = repository.identifyAndSave(request)) {
            is IdentificationOutcome.Success -> {
                notifications.showResultReady(outcome.id, outcome.result.commonName)
                Result.success()
            }
            is IdentificationOutcome.Failure -> {
                if (outcome.queuedForRetry) {
                    Result.retry() // toujours hors-ligne / erreur réseau → réessai
                } else {
                    notifications.showFailed(outcome.message)
                    Result.failure()
                }
            }
        }
    }

    private fun parseRequest(): IdentificationRequest? {
        val photoPaths = inputData.getStringArray(KEY_PHOTOS)?.toList() ?: return null
        if (photoPaths.isEmpty()) return null
        val organs = inputData.getStringArray(KEY_ORGANS)?.mapNotNull {
            runCatching { PhotoOrgan.valueOf(it) }.getOrNull()
        } ?: emptyList()
        val gps = if (inputData.getBoolean(KEY_HAS_GPS, false)) {
            GpsLocation(
                latitude = inputData.getDouble(KEY_LAT, 0.0),
                longitude = inputData.getDouble(KEY_LNG, 0.0),
                altitude = inputData.getDouble(KEY_ALT, Double.NaN).takeUnless { it.isNaN() },
                accuracyMeters = inputData.getDouble(KEY_ACC, Double.NaN)
                    .takeUnless { it.isNaN() }?.toFloat(),
            )
        } else {
            null
        }
        return IdentificationRequest(photoPaths, organs, gps)
    }

    companion object {
        const val KEY_PHOTOS = "photoPaths"
        const val KEY_ORGANS = "organs"
        const val KEY_HAS_GPS = "hasGps"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        const val KEY_ALT = "alt"
        const val KEY_ACC = "acc"
    }
}

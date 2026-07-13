package com.plantinfo.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.plantinfo.MainActivity
import com.plantinfo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications de résultats différés (§3) : lorsqu'une identification mise en file d'attente
 * hors-ligne aboutit, l'utilisateur est prévenu même s'il a quitté l'app.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Identifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Résultats des identifications différées (mode hors-ligne)" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showResultReady(identificationId: Long, commonName: String) {
        notify(
            id = identificationId.toInt(),
            title = "Identification prête",
            text = commonName,
        )
    }

    fun showFailed(message: String) {
        notify(
            id = FAILURE_NOTIF_ID,
            title = "Identification échouée",
            text = message,
        )
    }

    private fun notify(id: Int, title: String, text: String) {
        if (!hasPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "identifications"
        const val FAILURE_NOTIF_ID = -1
    }
}

package ch.electromel.plantinfo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfig
import javax.inject.Inject

/**
 * Point d'entrée de l'application. Active Hilt et fournit la configuration WorkManager
 * (utilisée par la file d'attente hors-ligne — Phase 3).
 */
@HiltAndroidApp
class PlantInfoApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // osmdroid exige un user-agent explicite (politique d'usage des tuiles OpenStreetMap).
        OsmConfig.getInstance().userAgentValue = packageName
    }
}

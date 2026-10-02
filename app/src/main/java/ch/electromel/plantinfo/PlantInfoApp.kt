package ch.electromel.plantinfo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import dagger.Lazy
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

    @Inject
    lateinit var apiKeyStore: Lazy<ApiKeyStore>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // osmdroid exige un user-agent explicite (politique d'usage des tuiles OpenStreetMap).
        OsmConfig.getInstance().userAgentValue = packageName

        // Ouvrir le stockage chiffré des clés (clé maîtresse du Keystore, déchiffrement du jeu de
        // clés) coûte plusieurs dizaines de millisecondes. Sans cela c'est le premier écran qui s'en
        // charge, sur le thread principal, au tout premier affichage. Le singleton Hilt est sûr
        // entre threads : si l'interface le demande avant la fin, elle attend la même instance.
        Thread({ runCatching { apiKeyStore.get() } }, "keystore-warmup").start()
    }
}

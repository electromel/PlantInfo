package ch.electromel.plantinfo.di

import ch.electromel.plantinfo.BuildConfig
import ch.electromel.plantinfo.data.remote.LogRedactor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Chaque ligne passe par LogRedactor : l'URL de Pl@ntNet porte la clé en clair (api-key=).
        val logger = HttpLoggingInterceptor.Logger { message ->
            HttpLoggingInterceptor.Logger.DEFAULT.log(LogRedactor.redact(message))
        }
        val logging = HttpLoggingInterceptor(logger).apply {
            // Ligne requête/réponse en debug seulement ; rien du tout en release.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
            LogRedactor.SECRET_HEADERS.forEach(::redactHeader)
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // les modèles IA peuvent être lents
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }
}

package ch.electromel.plantinfo.di

import ch.electromel.plantinfo.util.AppStrings
import ch.electromel.plantinfo.util.StringProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Relie l'interface [StringProvider] à son implémentation Android [AppStrings].
 *
 * Les dépôts et le code de domaine dépendent de l'interface : c'est ce qui permet de les couvrir
 * par des tests JVM, sans contexte Android, tout en affichant à l'exécution les chaînes de la
 * langue choisie dans les Paramètres.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StringsModule {

    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AppStrings): StringProvider
}

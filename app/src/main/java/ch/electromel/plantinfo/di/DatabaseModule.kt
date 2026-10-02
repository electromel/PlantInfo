package ch.electromel.plantinfo.di

import android.content.Context
import androidx.room.Room
import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.db.PlantInfoDatabase
import ch.electromel.plantinfo.data.db.SpeciesRangeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlantInfoDatabase =
        Room.databaseBuilder(context, PlantInfoDatabase::class.java, "plantinfo.db")
            .addMigrations(*PlantInfoDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideIdentificationDao(db: PlantInfoDatabase): IdentificationDao = db.identificationDao()

    @Provides
    fun provideSpeciesRangeDao(db: PlantInfoDatabase): SpeciesRangeDao = db.speciesRangeDao()
}

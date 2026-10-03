package com.trainnearme.di

import android.content.Context
import androidx.room.Room
import com.trainnearme.core.data.db.AppDatabase
import com.trainnearme.core.data.station.STATIONS_ASSET
import com.trainnearme.core.data.station.StationDao
import com.trainnearme.core.data.station.StationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "train-near-me.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideStationDao(database: AppDatabase): StationDao = database.stationDao()

    @Provides
    @Singleton
    fun provideStationRepository(
        dao: StationDao,
        @ApplicationContext context: Context,
    ): StationRepository = StationRepository(dao) {
        withContext(Dispatchers.IO) {
            context.assets.open(STATIONS_ASSET).bufferedReader().use { it.readText() }
        }
    }
}

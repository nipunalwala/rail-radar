package com.trainnearme.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.trainnearme.BuildConfig
import com.trainnearme.core.data.DataStoreProviderHealthStore
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.GuardedProvider
import com.trainnearme.core.data.ProviderHealthStore
import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.provider.railradar.RailRadarApi
import com.trainnearme.provider.railradar.RailRadarProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .apply {
                // Release builds have no key: the proxy adds it.
                val key = BuildConfig.RAILRADAR_API_KEY
                if (key.isNotEmpty()) {
                    addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $key").build())
                    }
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRailRadarApi(client: OkHttpClient): RailRadarApi = 
        RailRadarProvider.createApi(client, BuildConfig.TRAIN_API_BASE_URL)

    @Provides
    @Singleton
    fun provideProviderHealthStore(@ApplicationContext context: Context): ProviderHealthStore =
        DataStoreProviderHealthStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("provider_health") },
        )

    // The single place that decides which train-data provider the app uses.
    // The proxy serves RailRadar's own paths and responses, so the same
    // provider is used for both; only the base URL and the key differ.
    // Every request goes through GuardedProvider, which protects the quota.
    @Provides
    @Singleton
    fun provideTrainDataProvider(
        provider: RailRadarProvider,
        health: ProviderHealthStore,
    ): TrainDataProvider = GuardedProvider(provider, health)

    @Provides
    @Singleton
    fun provideDepartureRepository(
        stations: StationRepository,
        provider: TrainDataProvider,
        timetableDao: TimetableDao,
    ): DepartureRepository = DepartureRepository(stations, provider, timetableDao)
}

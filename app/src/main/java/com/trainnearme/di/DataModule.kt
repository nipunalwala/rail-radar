package com.trainnearme.di

import com.trainnearme.BuildConfig
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.provider.railradar.RailRadarApi
import com.trainnearme.provider.railradar.RailRadarProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.RAILRADAR_API_KEY}")
                    .build()
                chain.proceed(request)
            }
            .build()

    @Provides
    @Singleton
    fun provideRailRadarApi(client: OkHttpClient): RailRadarApi =
        Retrofit.Builder()
            .baseUrl(RailRadarApi.BASE_URL)
            .client(client)
            .addConverterFactory(
                RailRadarProvider.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(RailRadarApi::class.java)

    // The single place that decides which train-data provider the app uses.
    @Provides
    @Singleton
    fun provideTrainDataProvider(provider: RailRadarProvider): TrainDataProvider = provider

    @Provides
    @Singleton
    fun provideDepartureRepository(
        stations: StationRepository,
        provider: TrainDataProvider,
        timetableDao: TimetableDao,
    ): DepartureRepository = DepartureRepository(stations, provider, timetableDao)
}

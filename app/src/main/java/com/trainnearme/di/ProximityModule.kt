package com.trainnearme.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.proximity.AlertCoordinator
import com.trainnearme.proximity.AlertScheduler
import com.trainnearme.proximity.AlertStateStore
import com.trainnearme.proximity.DataStoreAlertStateStore
import com.trainnearme.proximity.EventLog
import com.trainnearme.proximity.GeofenceManager
import com.trainnearme.proximity.GeofenceSyncer
import com.trainnearme.proximity.WorkAlertScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProximityModule {

    /** Lives as long as the app process; for work that must outlast a screen. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideGeofenceSyncer(manager: GeofenceManager): GeofenceSyncer = manager

    @Provides
    fun provideAlertScheduler(scheduler: WorkAlertScheduler): AlertScheduler = scheduler

    @Provides
    @Singleton
    fun provideAlertStateStore(@ApplicationContext context: Context): AlertStateStore =
        DataStoreAlertStateStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("alert_state") },
        )

    @Provides
    @Singleton
    fun provideAlertCoordinator(
        stations: StationRepository,
        settings: SettingsRepository,
        store: AlertStateStore,
        scheduler: AlertScheduler,
        log: EventLog,
    ): AlertCoordinator = AlertCoordinator(stations, settings, store, scheduler, log::record)
}

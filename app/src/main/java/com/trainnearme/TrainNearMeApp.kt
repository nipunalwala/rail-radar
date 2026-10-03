package com.trainnearme

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.timetable.TimetableRefreshWorker
import com.trainnearme.proximity.GeofenceSyncer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TrainNearMeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var geofences: GeofenceSyncer
    @Inject lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        TimetableRefreshWorker.schedule(this)
        // Register geofences at start-up and again whenever a setting that shapes them changes.
        appScope.launch {
            settings.settings
                .map { Triple(it.alertsEnabled, it.radiusMetres, it.lines) }
                .distinctUntilChanged()
                .collect { geofences.requestSync() }
        }
    }
}

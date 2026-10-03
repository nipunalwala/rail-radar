package com.trainnearme.proximity

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.onLines
import com.trainnearme.core.domain.proximityChange
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.permissions.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High accuracy mode: a foreground service that follows the position every
 * few seconds and works out station entries and exits itself, instead of
 * waiting for the system's geofence events, which can lag by minutes.
 * Geofences stay registered alongside; the alert state machine removes the
 * duplicates.
 */
@AndroidEntryPoint
class HighAccuracyService : Service() {

    @Inject lateinit var stations: StationRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var store: AlertStateStore
    @Inject lateinit var coordinator: AlertCoordinator
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var log: EventLog

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val mutex = Mutex()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // HighAccuracyController starts this only with permission
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifier.HIGH_ACCURACY_ID, notifier.highAccuracyNotification(), type)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(MIN_MOVE_METRES)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            log.record("high accuracy mode started")
        } catch (e: Exception) {
            log.record("high accuracy mode could not start: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        log.record("high accuracy mode stopped")
        super.onDestroy()
    }

    private fun onLocation(location: Location) {
        val here = LatLng(location.latitude, location.longitude)
        val speed = location.takeIf { it.hasSpeed() }?.speed
        appScope.launch {
            // One position at a time, so two updates cannot both see the same station as new.
            mutex.withLock {
                val current = settings.settings.first()
                val all = stations.all()
                val inside = store.all().filterValues { it.inside }.keys
                val change = proximityChange(all.onLines(current.lines), all, inside, here, current.radiusMetres)
                if (change.exited.isNotEmpty()) coordinator.onExit(change.exited)
                if (change.entered.isNotEmpty()) coordinator.onEnter(change.entered, here, speed)
            }
        }
    }

    private companion object {
        const val INTERVAL_MILLIS = 10_000L
        const val MIN_MOVE_METRES = 20f
    }
}

/** Starts or stops [HighAccuracyService] to match the settings. */
@Singleton
class HighAccuracyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionChecker,
    private val log: EventLog,
) {
    fun apply(wanted: Boolean) {
        val intent = Intent(context, HighAccuracyService::class.java)
        if (wanted && permissions.current().backgroundLocation) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android refuses to start a foreground service from the
                // background in some situations; geofences still work.
                log.record("high accuracy mode not started: ${e.message}")
            }
        } else {
            context.stopService(intent)
        }
    }
}

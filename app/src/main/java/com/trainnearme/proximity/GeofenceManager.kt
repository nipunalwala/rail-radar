package com.trainnearme.proximity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.RefreshFence
import com.trainnearme.core.domain.planGeofences
import com.trainnearme.core.location.LocationProvider
import com.trainnearme.core.model.Station
import com.trainnearme.core.permissions.PermissionChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Asks for the registered geofences to be brought in line with settings and permissions. */
interface GeofenceSyncer {
    fun requestSync()
}

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stations: StationRepository,
    private val settings: SettingsRepository,
    private val location: LocationProvider,
    private val permissions: PermissionChecker,
    private val coordinator: AlertCoordinator,
    private val log: EventLog,
) : GeofenceSyncer {

    private val client by lazy { LocationServices.getGeofencingClient(context) }
    private val mutex = Mutex()

    private val pendingIntent: PendingIntent by lazy {
        // Play services fills in the geofencing event, so the intent must be mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }

    /** Runs [sync] in a worker so it survives the caller (a broadcast, a screen) going away. */
    override fun requestSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GeofenceSyncWorker>().build(),
        )
    }

    /** Replaces every registered geofence with the ones the current settings call for. */
    @SuppressLint("MissingPermission") // checked through PermissionChecker
    suspend fun sync() = mutex.withLock {
        try {
            val current = settings.settings.first()
            client.removeGeofences(pendingIntent).await()
            if (!current.alertsEnabled) {
                log.record("geofences removed: alerts are off")
                return@withLock
            }
            if (!permissions.current().backgroundLocation) {
                log.record("geofences removed: no background location permission")
                return@withLock
            }
            val here = location.current()
            if (here != null) coordinator.reconcile(here, current.radiusMetres)
            val plan = planGeofences(stations.all(), current.lines, here, current.radiusMetres)
            val fences = plan.stations.map { stationFence(it, current.radiusMetres) } +
                listOfNotNull(plan.refresh?.let(::refreshFence))
            if (fences.isEmpty()) return@withLock
            val request = GeofencingRequest.Builder()
                // Fire at once if the user is already inside; the alert state
                // machine drops the repeat when they were already known to be there.
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            client.addGeofences(request, pendingIntent).await()
            log.record(
                "registered ${plan.stations.size} station geofences at ${current.radiusMetres} m" +
                    (plan.refresh?.let { ", refresh fence ${it.radiusMetres.toInt()} m" } ?: ""),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.record("geofence sync failed: ${e.message}")
        }
    }

    private fun stationFence(station: Station, radiusMetres: Int): Geofence =
        Geofence.Builder()
            .setRequestId(STATION_PREFIX + station.id)
            .setCircularRegion(station.lat, station.lng, radiusMetres.toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(0)
            .build()

    private fun refreshFence(fence: RefreshFence): Geofence =
        Geofence.Builder()
            .setRequestId(REFRESH_ID)
            .setCircularRegion(fence.lat, fence.lng, fence.radiusMetres)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

    companion object {
        const val STATION_PREFIX = "station:"
        const val REFRESH_ID = "refresh"
        private const val SYNC_WORK = "geofence-sync"
    }
}

@HiltWorker
class GeofenceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: GeofenceManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        manager.sync()
        return Result.success()
    }
}

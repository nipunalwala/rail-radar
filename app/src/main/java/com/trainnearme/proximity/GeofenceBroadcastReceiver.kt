package com.trainnearme.proximity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.trainnearme.core.location.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: GeofenceManager
    @Inject lateinit var coordinator: AlertCoordinator
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var log: EventLog

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            log.record("geofence error ${event.errorCode}")
            return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        val stationIds = ids
            .filter { it.startsWith(GeofenceManager.STATION_PREFIX) }
            .map { it.removePrefix(GeofenceManager.STATION_PREFIX) }
        val at = event.triggeringLocation?.let { LatLng(it.latitude, it.longitude) }
        val transition = event.geofenceTransition

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT && GeofenceManager.REFRESH_ID in ids) {
            log.record("left the refresh fence")
            manager.requestSync()
        }
        if (stationIds.isEmpty()) return

        // The coordinator only reads stored state and queues a worker, so it
        // finishes well inside the time a broadcast is allowed.
        val pending = goAsync()
        appScope.launch {
            try {
                when (transition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        log.record("enter $stationIds")
                        coordinator.onEnter(stationIds, at)
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        log.record("exit $stationIds")
                        coordinator.onExit(stationIds)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Geofences do not survive a reboot or an app update, so they are registered again. */
@AndroidEntryPoint
class GeofenceRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            manager.requestSync()
        }
    }
}

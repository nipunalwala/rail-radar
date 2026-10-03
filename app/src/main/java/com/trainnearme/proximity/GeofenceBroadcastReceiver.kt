package com.trainnearme.proximity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: GeofenceManager
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

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> log.record("enter $stationIds")
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (stationIds.isNotEmpty()) log.record("exit $stationIds")
                if (GeofenceManager.REFRESH_ID in ids) {
                    log.record("left the refresh fence")
                    manager.requestSync()
                }
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

package com.trainnearme.proximity

import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.StationAlertState
import com.trainnearme.core.domain.distanceTo
import com.trainnearme.core.domain.onEnter
import com.trainnearme.core.domain.onExit
import com.trainnearme.core.location.LatLng
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Remembers, across process restarts, which stations the user is inside. */
interface AlertStateStore {
    suspend fun get(stationId: String): StationAlertState
    suspend fun set(stationId: String, state: StationAlertState)
    suspend fun insideStationIds(): Set<String>
}

/** Starts and withdraws the alert for a station. */
interface AlertScheduler {
    fun schedule(stationId: String)
    fun cancel(stationId: String)
}

/** Decides, from geofence events, when a station alert is due. */
class AlertCoordinator(
    private val stations: StationRepository,
    private val settings: SettingsRepository,
    private val store: AlertStateStore,
    private val scheduler: AlertScheduler,
    private val log: (String) -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    /** [at] is where the user was when the geofences fired, if known. */
    suspend fun onEnter(stationIds: List<String>, at: LatLng?) {
        val current = settings.settings.first()
        val due = stationIds.mapNotNull { stations.byId(it) }.filter { station ->
            val result = store.get(station.id).onEnter(now())
            store.set(station.id, result.state)
            result.alert
        }
        if (!current.alertsEnabled) return

        // Neighbouring stations can be entered together; one alert is shown, for the closer one.
        val monitored = due.filter { station -> station.lines.any { it in current.lines } }
        val chosen = if (at != null) {
            monitored.minByOrNull { it.distanceTo(at.lat, at.lng) }
        } else {
            monitored.firstOrNull()
        } ?: return
        log("alert for ${chosen.id}")
        scheduler.schedule(chosen.id)
    }

    suspend fun onExit(stationIds: List<String>) {
        for (id in stationIds) {
            store.set(id, store.get(id).onExit(now()))
            scheduler.cancel(id)
        }
    }

    /**
     * Clears "inside" for stations the user is clearly no longer at. An exit
     * can be missed while the phone is off or geofences are unregistered, which
     * would otherwise block that station's next alert.
     */
    suspend fun reconcile(here: LatLng, radiusMetres: Int) {
        for (id in store.insideStationIds()) {
            val station = stations.byId(id)
            if (station == null || station.distanceTo(here.lat, here.lng) > radiusMetres * FAR_FACTOR) {
                store.set(id, StationAlertState())
                scheduler.cancel(id)
            }
        }
    }

    private companion object {
        // Well outside the radius, so an imprecise position does not clear a real "inside".
        const val FAR_FACTOR = 2.0
    }
}

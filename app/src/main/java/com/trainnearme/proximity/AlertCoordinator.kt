package com.trainnearme.proximity

import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.StationAlertState
import com.trainnearme.core.domain.distanceTo
import com.trainnearme.core.domain.isRidingThrough
import com.trainnearme.core.domain.onEnter
import com.trainnearme.core.domain.onExit
import com.trainnearme.core.location.LatLng
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Remembers, across process restarts, which stations the user is inside. */
interface AlertStateStore {
    suspend fun get(stationId: String): StationAlertState
    suspend fun set(stationId: String, state: StationAlertState)

    /** Every station that has a stored state. */
    suspend fun all(): Map<String, StationAlertState>
}

/** Starts and withdraws the alert for a station. */
interface AlertScheduler {
    fun schedule(stationId: String)
    fun cancel(stationId: String)
}

/** Decides, from proximity events, when a station alert is due. */
class AlertCoordinator(
    private val stations: StationRepository,
    private val settings: SettingsRepository,
    private val store: AlertStateStore,
    private val scheduler: AlertScheduler,
    private val log: (String) -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    /**
     * @param at where the user was when the event fired, if known
     * @param speedMps their speed there, if known
     */
    suspend fun onEnter(stationIds: List<String>, at: LatLng?, speedMps: Float? = null) {
        val current = settings.settings.first()
        val time = now()
        // Read before this event's own changes, so stations entered together
        // are not mistaken for one another's "previous station".
        val before = store.all()
        val due = stationIds.mapNotNull { stations.byId(it) }.filter { station ->
            val result = store.get(station.id).onEnter(time)
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

        // Decided before anything is fetched, so a ride costs no API calls.
        val others = before
            .filterKeys { it !in stationIds }
            .mapNotNull { (id, state) -> stations.byId(id)?.let { it to state } }
            .toMap()
        if (isRidingThrough(chosen, current.radiusMetres, speedMps, others, time)) {
            log("no alert for ${chosen.id}: passing through on a train")
            return
        }
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
        for ((id, state) in store.all()) {
            if (!state.inside) continue
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

package com.trainnearme.core.domain

import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.Station
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** About 20 km/h: faster than walking or a train at a platform, slower than a train between stations. */
const val RIDING_SPEED_MPS = 5.5

/** A previous station only counts as "just left" within this time. */
val RIDE_WINDOW: Duration = Duration.ofMinutes(12)

/**
 * True when the user is passing [station] on a train rather than arriving to
 * catch one, so no alert should be shown.
 *
 * Speed alone is not used: someone arriving by auto-rickshaw or bus is also
 * moving fast and does want the alert. The sign of a train ride is having just
 * come from another station on the same line faster than anyone could on foot.
 *
 * @param speedMps speed reported with the position, if any
 * @param others the alert state of every other station
 */
fun isRidingThrough(
    station: Station,
    radiusMetres: Int,
    speedMps: Float?,
    others: Map<Station, StationAlertState>,
    now: Instant,
): Boolean = others.any { (other, state) ->
    if (other.id == station.id || other.lines.none { it in station.lines }) return@any false

    // Still inside the previous station's radius and moving fast: the exit
    // event for it has simply not arrived yet.
    if (state.inside && speedMps != null && speedMps >= RIDING_SPEED_MPS) return@any true

    val exitedAt = state.lastExitAt ?: return@any false
    val elapsed = Duration.between(exitedAt, now)
    if (elapsed.isNegative || elapsed > RIDE_WINDOW) return@any false
    // Ground covered between leaving that station's radius and entering this one's.
    val gapMetres = max(0.0, other.distanceTo(station.lat, station.lng) - 2 * radiusMetres)
    gapMetres / max(1L, elapsed.seconds) >= RIDING_SPEED_MPS
}

data class ProximityChange(val entered: List<String>, val exited: List<String>)

// Leaving needs a little more distance than entering, so a position that
// wobbles on the boundary does not flip back and forth.
private const val EXIT_FACTOR = 1.2

/**
 * What changed for a new position: stations now within [radiusMetres] that
 * were not [inside] before, and [inside] ones the user has moved away from.
 * Used by high accuracy mode, which watches positions itself.
 *
 * @param monitored stations that may be entered
 * @param all every station, to measure the distance to those in [inside]
 */
fun proximityChange(
    monitored: List<Station>,
    all: List<Station>,
    inside: Set<String>,
    here: LatLng,
    radiusMetres: Int,
): ProximityChange {
    val entered = monitored
        .filter { it.id !in inside && it.distanceTo(here.lat, here.lng) <= radiusMetres }
        .map { it.id }
    val byId = all.associateBy { it.id }
    val exited = inside.filter { id ->
        val station = byId[id] ?: return@filter true
        station.distanceTo(here.lat, here.lng) > radiusMetres * EXIT_FACTOR
    }
    return ProximityChange(entered, exited)
}

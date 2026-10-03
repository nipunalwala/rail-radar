package com.trainnearme.core.domain

import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import kotlin.math.max

/** Android allows 100 geofences per app; the rest are headroom and the refresh fence. */
const val MAX_STATION_GEOFENCES = 90

private const val MIN_REFRESH_RADIUS_METRES = 1_000.0

// Used to choose stations when the phone has no position yet (Dadar).
private val CITY_CENTRE = LatLng(19.0183, 72.8429)

/** A circle around the user; leaving it means the station geofences must be chosen again. */
data class RefreshFence(val lat: Double, val lng: Double, val radiusMetres: Float)

data class GeofencePlan(val stations: List<Station>, val refresh: RefreshFence?)

/**
 * Chooses which stations get a geofence. When the monitored lines have more
 * stations than fit, the nearest [limit] are taken and a refresh fence is
 * sized so the user re-plans before reaching a station that was left out.
 */
fun planGeofences(
    stations: List<Station>,
    lines: Set<Line>,
    here: LatLng?,
    stationRadiusMetres: Int,
    limit: Int = MAX_STATION_GEOFENCES,
): GeofencePlan {
    val candidates = stations.onLines(lines)
    if (candidates.size <= limit) return GeofencePlan(candidates, null)

    val origin = here ?: CITY_CENTRE
    val byDistance = candidates.sortedBy { it.distanceTo(origin.lat, origin.lng) }
    val chosen = byDistance.take(limit)
    // Without a position the fence cannot be placed; the next sync that has one adds it.
    if (here == null) return GeofencePlan(chosen, null)

    val nearestLeftOut = byDistance[limit].distanceTo(here.lat, here.lng)
    val radius = max(MIN_REFRESH_RADIUS_METRES, (nearestLeftOut - stationRadiusMetres) / 2)
    return GeofencePlan(chosen, RefreshFence(here.lat, here.lng, radius.toFloat()))
}

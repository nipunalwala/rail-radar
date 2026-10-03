package com.trainnearme.core.domain

import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance in metres. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

fun Station.distanceTo(lat: Double, lng: Double): Double = distanceMeters(this.lat, this.lng, lat, lng)

fun List<Station>.onLines(lines: Set<Line>): List<Station> =
    filter { station -> station.lines.any { it in lines } }

fun List<Station>.nearestTo(lat: Double, lng: Double): Station? =
    minByOrNull { it.distanceTo(lat, lng) }

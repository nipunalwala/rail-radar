package com.trainnearme.core.domain

import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.TrainType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

val MUMBAI_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

// A train whose expected time has just passed may still be at the platform.
internal val DEPARTURE_GRACE: Duration = Duration.ofMinutes(2)

/** When the train is expected to leave: the live estimate if present, else the schedule. */
fun Departure.effectiveTime(now: Instant, zone: ZoneId = MUMBAI_ZONE): Instant {
    expectedTime?.let { return it }
    val today = now.atZone(zone).toLocalDate()
    val candidate = scheduledTime.atDate(today).atZone(zone).toInstant()
    // A time far in the past means the schedule entry is for tomorrow (e.g. 00:04 seen at 23:00).
    return if (Duration.between(candidate, now) > Duration.ofHours(12)) {
        scheduledTime.atDate(today.plusDays(1)).atZone(zone).toInstant()
    } else {
        candidate
    }
}

/** The next [count] trains still to leave, soonest first. */
fun upcomingDepartures(
    departures: List<Departure>,
    now: Instant,
    count: Int,
    localsOnly: Boolean = true,
    zone: ZoneId = MUMBAI_ZONE,
): List<Departure> =
    departures.asSequence()
        .filter { it.status != DepartureStatus.DEPARTED }
        .filter { !localsOnly || it.trainType == TrainType.LOCAL }
        .filter { it.effectiveTime(now, zone) >= now.minus(DEPARTURE_GRACE) }
        .sortedBy { it.effectiveTime(now, zone) }
        .take(count)
        .toList()

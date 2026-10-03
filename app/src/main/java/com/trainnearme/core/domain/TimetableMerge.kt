package com.trainnearme.core.domain

import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.ScheduledDeparture
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Timetable entries that leave between [now] and [now] + [window], as
 * departures with no live data.
 */
fun scheduledDeparturesWithin(
    timetable: List<ScheduledDeparture>,
    now: Instant,
    window: Duration,
    zone: ZoneId = MUMBAI_ZONE,
): List<Departure> {
    val today = now.atZone(zone).toLocalDate()
    val range = now.minus(DEPARTURE_GRACE)..now.plus(window)
    return timetable.mapNotNull { entry ->
        // Yesterday and tomorrow are checked so the window can span midnight.
        (-1L..1L).asSequence()
            .map { today.plusDays(it) }
            // Run days describe the day the train starts, which is the day
            // before for a train that reaches this station after midnight.
            .filter { date -> date.minusDays(entry.dayOffset.toLong()).dayOfWeek in entry.runDays }
            .map { date -> entry.departure.atDate(date).atZone(zone).toInstant() }
            .firstOrNull { it in range }
            ?.let { entry.toDeparture(it) }
    }
}

private fun ScheduledDeparture.toDeparture(at: Instant) = Departure(
    trainNumber = trainNumber,
    trainName = trainName,
    destinationCode = destinationCode,
    destinationName = destinationName,
    scheduledTime = departure,
    expectedTime = at,
    delayMinutes = null,
    platform = null,
    trainType = trainType,
    status = DepartureStatus.SCHEDULED,
    isLive = false,
    originCode = originCode,
    originName = originName,
)

/**
 * Live entries win over timetable entries for the same train. Live entries
 * with no timetable match are kept: a late train's scheduled time may already
 * be outside the timetable window.
 */
fun mergeDepartures(scheduled: List<Departure>, live: List<Departure>?): List<Departure> {
    if (live == null) return scheduled
    val liveNumbers = live.mapTo(HashSet()) { it.trainNumber }
    return live + scheduled.filter { it.trainNumber !in liveNumbers }
}

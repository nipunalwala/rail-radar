package com.trainnearme.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

enum class TrainType { LOCAL, EXPRESS }

enum class DepartureStatus { SCHEDULED, NOT_STARTED, UPCOMING, AT_STATION, DEPARTED, UNKNOWN }

/** One train leaving a station, with live fields null when the provider has no live data. */
data class Departure(
    val trainNumber: String,
    val trainName: String,
    val destinationCode: String,
    val destinationName: String,
    val scheduledTime: LocalTime,
    val expectedTime: Instant?,
    val delayMinutes: Int?,
    val platform: String?,
    val trainType: TrainType,
    val status: DepartureStatus,
    val isLive: Boolean,
)

/** A timetable entry for a station. Cacheable for days. */
data class ScheduledDeparture(
    val trainNumber: String,
    val trainName: String,
    val destinationCode: String,
    val destinationName: String,
    val departure: LocalTime,
    /** Days after the train's start day that it leaves this station (0 or 1). */
    val dayOffset: Int,
    /** Days on which the train starts its run. */
    val runDays: Set<DayOfWeek>,
    val trainType: TrainType,
)

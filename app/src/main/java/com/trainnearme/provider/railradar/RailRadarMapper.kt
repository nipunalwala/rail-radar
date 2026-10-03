package com.trainnearme.provider.railradar

import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

private val LOCAL_TYPES = setOf("EMU", "SUBURBAN")

private val DAYS = mapOf(
    "mon" to DayOfWeek.MONDAY,
    "tue" to DayOfWeek.TUESDAY,
    "wed" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY,
    "sat" to DayOfWeek.SATURDAY,
    "sun" to DayOfWeek.SUNDAY,
)

/** Null when the train does not depart from this station (it terminates here). */
internal fun LiveEntryDto.toDeparture(): Departure? {
    val scheduled = stop.departure?.toLocalTimeOrNull() ?: return null
    val status = live?.type.toStatus()
    val isLive = live != null && status != DepartureStatus.SCHEDULED
    val destinationCode = train.destination.orEmpty()
    val originCode = train.source.orEmpty()
    return Departure(
        trainNumber = train.number,
        trainName = train.name,
        destinationCode = destinationCode,
        // The live board has no station names; DepartureRepository fills this in.
        destinationName = destinationCode,
        scheduledTime = scheduled,
        expectedTime = live?.expectedDepartureTime?.toInstantOrNull(),
        delayMinutes = if (isLive) live?.delayMinutes else null,
        platform = stop.platform?.takeIf { it.isNotBlank() },
        trainType = train.type.toTrainType(),
        status = status,
        isLive = isLive,
        originCode = originCode,
        // As for the destination: only a code is given here.
        originName = originCode,
    )
}

internal fun TimetableEntryDto.toScheduledDeparture(): ScheduledDeparture? {
    val departure = stop.departure?.toLocalTimeOrNull() ?: return null
    return ScheduledDeparture(
        trainNumber = train.number,
        trainName = train.name,
        destinationCode = train.destination?.code.orEmpty(),
        destinationName = train.destination?.name.orEmpty(),
        departure = departure,
        dayOffset = ((stop.departureDay ?: 1) - 1).coerceAtLeast(0),
        runDays = train.runDays.mapNotNull { DAYS[it.lowercase()] }.toSet(),
        trainType = train.type.toTrainType(),
        originCode = train.source?.code.orEmpty(),
        originName = train.source?.name.orEmpty(),
    )
}

private fun String.toTrainType(): TrainType =
    if (uppercase() in LOCAL_TYPES) TrainType.LOCAL else TrainType.EXPRESS

private fun String?.toStatus(): DepartureStatus = when (this) {
    null, "scheduled" -> DepartureStatus.SCHEDULED
    "not-started" -> DepartureStatus.NOT_STARTED
    "upcoming" -> DepartureStatus.UPCOMING
    "at-station" -> DepartureStatus.AT_STATION
    "departed" -> DepartureStatus.DEPARTED
    else -> DepartureStatus.UNKNOWN
}

private fun String.toLocalTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this) }.getOrNull()

private fun String.toInstantOrNull(): Instant? =
    runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()

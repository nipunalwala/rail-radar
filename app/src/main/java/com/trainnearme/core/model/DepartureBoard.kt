package com.trainnearme.core.model

import java.time.Instant

enum class BoardSource {
    /** At least one provider code returned a live board. */
    LIVE,

    /** Built from the cached timetable only. */
    SCHEDULED,

    /** Neither live data nor a timetable could be obtained. */
    UNAVAILABLE,
}

data class DepartureBoard(
    val station: Station,
    val departures: List<Departure>,
    val source: BoardSource,
    val asOf: Instant,
)

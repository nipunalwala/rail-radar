package com.trainnearme.core.data

import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainPosition

/**
 * Source of train data. The rest of the app depends only on this interface, so
 * a provider can be replaced by changing one DI binding.
 */
interface TrainDataProvider {
    /** Scheduled departures for a station. */
    suspend fun timetable(stationCode: String): List<ScheduledDeparture>

    /** Live board for a station, including trains that have already departed. */
    suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure>

    /** Where one train is now, with its stops. Station names are the provider's own. */
    suspend fun trainPosition(trainNumber: String): TrainPosition
}

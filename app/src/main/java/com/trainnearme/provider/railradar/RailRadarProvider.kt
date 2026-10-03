package com.trainnearme.provider.railradar

import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.ScheduledDeparture
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

class RailRadarProvider @Inject constructor(
    private val api: RailRadarApi,
) : TrainDataProvider {

    override suspend fun timetable(stationCode: String): List<ScheduledDeparture> =
        api.timetable(stationCode).requireData().trains.mapNotNull { it.toScheduledDeparture() }

    override suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure> =
        api.liveBoard(stationCode, hoursAhead).requireData().trains.mapNotNull { it.toDeparture() }

    private fun <T> EnvelopeDto<T>.requireData(): T =
        data?.takeIf { success } ?: throw IOException("RailRadar returned no data")

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}

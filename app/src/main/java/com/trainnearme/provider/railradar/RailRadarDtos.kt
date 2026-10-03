package com.trainnearme.provider.railradar

import kotlinx.serialization.Serializable

@Serializable
data class EnvelopeDto<T>(
    val success: Boolean = false,
    val data: T? = null,
)

// GET /v1/stations/{code}/live

@Serializable
data class LiveBoardDto(
    val trains: List<LiveEntryDto> = emptyList(),
)

@Serializable
data class LiveEntryDto(
    val train: LiveTrainDto,
    val stop: LiveStopDto,
    val live: LiveStatusDto? = null,
)

@Serializable
data class LiveTrainDto(
    val number: String,
    val name: String = "",
    val type: String = "",
    val source: String? = null,
    val destination: String? = null,
)

@Serializable
data class LiveStopDto(
    val departure: String? = null,
    val platform: String? = null,
)

@Serializable
data class LiveStatusDto(
    val type: String = "scheduled",
    val expectedDepartureTime: String? = null,
    val delayMinutes: Int? = null,
)

// GET /v1/stations/{code}/trains

@Serializable
data class TimetableDto(
    val trains: List<TimetableEntryDto> = emptyList(),
)

@Serializable
data class TimetableEntryDto(
    val train: TimetableTrainDto,
    val stop: TimetableStopDto,
)

@Serializable
data class TimetableTrainDto(
    val number: String,
    val name: String = "",
    val type: String = "",
    val source: StationRefDto? = null,
    val destination: StationRefDto? = null,
    val runDays: List<String> = emptyList(),
)

@Serializable
data class StationRefDto(
    val code: String = "",
    val name: String = "",
)

@Serializable
data class TimetableStopDto(
    val departure: String? = null,
    val departureDay: Int? = null,
)

// GET /v1/trains/{number}/live

@Serializable
data class TrainLiveDto(
    val trainNumber: String = "",
    val trainName: String = "",
    val status: String = "",
    val isLive: Boolean = false,
    val lastUpdatedAt: String? = null,
    val delayMinutes: Int? = null,
    val currentLocation: TrainLocationDto? = null,
    val nextHalt: TrainHaltDto? = null,
    val route: List<RouteStopDto> = emptyList(),
)

@Serializable
data class TrainLocationDto(
    val stationCode: String = "",
    val stationName: String = "",
    val status: String = "",
)

@Serializable
data class TrainHaltDto(
    val stationCode: String = "",
    val stationName: String = "",
)

@Serializable
data class RouteStopDto(
    val stationCode: String = "",
    val stationName: String = "",
    val status: String = "",
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val actualArrival: String? = null,
    val actualDeparture: String? = null,
    val delayArrival: Int? = null,
    val delayDeparture: Int? = null,
    val platform: String? = null,
)

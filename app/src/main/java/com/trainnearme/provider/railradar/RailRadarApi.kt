package com.trainnearme.provider.railradar

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RailRadarApi {
    @GET("v1/stations/{code}/live")
    suspend fun liveBoard(
        @Path("code") code: String,
        @Query("hours") hours: Int,
    ): EnvelopeDto<LiveBoardDto>

    @GET("v1/stations/{code}/trains")
    suspend fun timetable(@Path("code") code: String): EnvelopeDto<TimetableDto>

    @GET("v1/trains/{number}/live")
    suspend fun trainLive(
        @Path("number") number: String,
        @Query("haltsOnly") haltsOnly: Boolean,
    ): EnvelopeDto<TrainLiveDto>

    companion object {
        const val BASE_URL = "https://api.railradar.in/"
    }
}

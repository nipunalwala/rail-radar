package com.trainnearme.provider.railradar

import com.trainnearme.core.data.ProviderRateLimitedException
import com.trainnearme.core.data.ProviderUnauthorizedException
import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.ScheduledDeparture
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject

class RailRadarProvider @Inject constructor(
    private val api: RailRadarApi,
) : TrainDataProvider {

    override suspend fun timetable(stationCode: String): List<ScheduledDeparture> =
        request { api.timetable(stationCode) }.trains.mapNotNull { it.toScheduledDeparture() }

    override suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure> =
        request { api.liveBoard(stationCode, hoursAhead) }.trains.mapNotNull { it.toDeparture() }

    /** Turns the HTTP statuses the app reacts to into the provider-neutral exceptions. */
    private suspend fun <T> request(call: suspend () -> EnvelopeDto<T>): T {
        val envelope = try {
            call()
        } catch (e: HttpException) {
            throw when (e.code()) {
                429 -> ProviderRateLimitedException()
                401, 403 -> ProviderUnauthorizedException()
                else -> e
            }
        }
        return envelope.data?.takeIf { envelope.success } ?: throw IOException("RailRadar returned no data")
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun createApi(client: OkHttpClient, baseUrl: String = RailRadarApi.BASE_URL): RailRadarApi =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RailRadarApi::class.java)
    }
}

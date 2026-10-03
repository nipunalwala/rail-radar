package com.trainnearme.core.data.station

import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val STATIONS_ASSET = "stations.json"

@Serializable
private data class StationAssetDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val lines: List<String>,
    val providerCodes: List<String>,
)

private val json = Json { ignoreUnknownKeys = true }

/** Parses the bundled station list produced by tools/stations/build-stations.mjs. */
fun parseStations(assetJson: String): List<Station> =
    json.decodeFromString<List<StationAssetDto>>(assetJson).map {
        Station(
            id = it.id,
            name = it.name,
            lat = it.lat,
            lng = it.lng,
            lines = it.lines.map(Line::valueOf).toSet(),
            providerCodes = it.providerCodes,
        )
    }

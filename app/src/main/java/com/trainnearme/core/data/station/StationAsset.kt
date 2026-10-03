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
    /** Present only when the station has more than one code. */
    val codeLines: Map<String, List<String>>? = null,
)

private val json = Json { ignoreUnknownKeys = true }

/** Parses the bundled station list produced by tools/stations/build-stations.mjs. */
fun parseStations(assetJson: String): List<Station> =
    json.decodeFromString<List<StationAssetDto>>(assetJson).map { dto ->
        val lines = dto.lines.map(Line::valueOf).toSet()
        Station(
            id = dto.id,
            name = dto.name,
            lat = dto.lat,
            lng = dto.lng,
            lines = lines,
            codeLines = dto.providerCodes.associateWith { code ->
                dto.codeLines?.get(code)?.map(Line::valueOf)?.toSet() ?: lines
            },
        )
    }

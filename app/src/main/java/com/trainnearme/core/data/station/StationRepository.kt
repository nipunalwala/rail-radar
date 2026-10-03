package com.trainnearme.core.data.station

import com.trainnearme.core.domain.nearestTo
import com.trainnearme.core.domain.onLines
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The suburban stations the app knows about. The bundled asset is imported into
 * the database on first use; after that the list is served from memory.
 */
class StationRepository(
    private val dao: StationDao,
    private val loadAsset: suspend () -> String,
) {
    private val mutex = Mutex()
    private var cached: List<Station>? = null

    suspend fun all(): List<Station> = mutex.withLock {
        cached ?: load().also { cached = it }
    }

    suspend fun byId(id: String): Station? = all().firstOrNull { it.id == id }

    suspend fun onLines(lines: Set<Line>): List<Station> = all().onLines(lines)

    suspend fun nearest(
        lat: Double,
        lng: Double,
        lines: Set<Line> = Line.entries.toSet(),
    ): Station? = onLines(lines).nearestTo(lat, lng)

    private suspend fun load(): List<Station> {
        if (dao.count() == 0) {
            dao.insertAll(parseStations(loadAsset()).map { it.toEntity() })
        }
        return dao.getAll().map { it.toStation() }
    }
}

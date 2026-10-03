package com.trainnearme.core.data

import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.core.data.timetable.TimetableMetaEntity
import com.trainnearme.core.data.timetable.toEntity
import com.trainnearme.core.data.timetable.toScheduledDeparture
import com.trainnearme.core.domain.mergeDepartures
import com.trainnearme.core.domain.scheduledDeparturesWithin
import com.trainnearme.core.domain.upcomingDepartures
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureBoard
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

/**
 * Next trains for a station: the cached timetable is the base and the live
 * board, when it answers in time, is laid over it.
 */
class DepartureRepository(
    private val stations: StationRepository,
    private val provider: TrainDataProvider,
    private val timetableDao: TimetableDao,
    private val now: () -> Instant = Instant::now,
) {
    private class CachedLive(val fetchedAt: Instant, val departures: List<Departure>)
    private class CodeData(val timetable: List<ScheduledDeparture>?, val live: List<Departure>?)

    private val liveCacheLock = Mutex()
    private val liveCache = HashMap<String, CachedLive>()

    /**
     * Null when [stationId] is unknown. Only the station's codes that serve
     * [lines] are queried; a station on none of them is queried in full.
     */
    suspend fun nextDepartures(
        stationId: String,
        count: Int,
        lines: Set<Line> = Line.entries.toSet(),
        localsOnly: Boolean = true,
    ): DepartureBoard? {
        val station = stations.byId(stationId) ?: return null
        val at = now()
        val codes = station.codesFor(lines).ifEmpty { station.providerCodes }
        val perCode = coroutineScope {
            codes.map { code -> async { loadCode(code, at) } }.awaitAll()
        }
        val stationNames = stations.all()
            .flatMap { s -> s.providerCodes.map { it to s.name } }
            .toMap()

        val merged = perCode.flatMap { data ->
            val timetable = data.timetable.orEmpty()
            val timetableNames = timetable.associate { it.trainNumber to it.destinationName }
            // The live board carries only a destination code. The app's own
            // station name is preferred so a station is spelt one way everywhere;
            // the provider's name covers destinations outside the suburban list.
            mergeDepartures(scheduledDeparturesWithin(timetable, at, WINDOW), data.live).map { departure ->
                val name = stationNames[departure.destinationCode]
                    ?: timetableNames[departure.trainNumber]?.takeIf { it.isNotBlank() }
                    ?: departure.destinationName
                departure.copy(destinationName = name)
            }
        }

        val source = when {
            perCode.any { it.live != null } -> BoardSource.LIVE
            perCode.any { it.timetable != null } -> BoardSource.SCHEDULED
            else -> BoardSource.UNAVAILABLE
        }
        return DepartureBoard(station, upcomingDepartures(merged, at, count, localsOnly), source, at)
    }

    /** Re-fetches every cached timetable older than [maxAge]. Returns how many were refreshed. */
    suspend fun refreshStaleTimetables(maxAge: Duration): Int {
        val cutoff = now().minus(maxAge).toEpochMilli()
        return timetableDao.allMeta()
            .filter { it.fetchedAtMillis < cutoff }
            .count { fetchTimetable(it.providerCode) != null }
    }

    private suspend fun loadCode(code: String, at: Instant): CodeData = coroutineScope {
        val live = async { liveBoard(code, at) }
        val timetable = async { timetable(code) }
        CodeData(timetable.await(), live.await())
    }

    private suspend fun liveBoard(code: String, at: Instant): List<Departure>? {
        liveCacheLock.withLock {
            liveCache[code]
                ?.takeIf { Duration.between(it.fetchedAt, at) < LIVE_CACHE_TTL }
                ?.let { return it.departures }
        }
        val departures = withTimeoutOrNull(LIVE_TIMEOUT.toMillis()) {
            orNullOnFailure { provider.liveBoard(code, WINDOW.toHours().toInt()) }
        } ?: return null
        liveCacheLock.withLock { liveCache[code] = CachedLive(at, departures) }
        return departures
    }

    /** The stored timetable, fetched on first use. Null when there is none and it cannot be fetched. */
    private suspend fun timetable(code: String): List<ScheduledDeparture>? {
        if (timetableDao.meta(code) != null) {
            return timetableDao.entries(code).map { it.toScheduledDeparture() }
        }
        return withTimeoutOrNull(TIMETABLE_TIMEOUT.toMillis()) { fetchTimetable(code) }
    }

    private suspend fun fetchTimetable(code: String): List<ScheduledDeparture>? {
        val timetable = orNullOnFailure { provider.timetable(code) } ?: return null
        timetableDao.replace(
            TimetableMetaEntity(
                providerCode = code,
                fetchedAtMillis = now().toEpochMilli(),
                localCount = timetable.count { it.trainType == TrainType.LOCAL },
            ),
            timetable.map { it.toEntity(code) },
        )
        return timetable
    }

    private suspend fun <T> orNullOnFailure(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        val WINDOW: Duration = Duration.ofHours(2)
        val LIVE_TIMEOUT: Duration = Duration.ofSeconds(4)
        val LIVE_CACHE_TTL: Duration = Duration.ofSeconds(60)
        val TIMETABLE_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}

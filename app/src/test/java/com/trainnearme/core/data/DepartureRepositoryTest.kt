package com.trainnearme.core.data

import com.trainnearme.core.data.station.StationDao
import com.trainnearme.core.data.station.StationEntity
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.core.data.timetable.TimetableEntryEntity
import com.trainnearme.core.data.timetable.TimetableMetaEntity
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DepartureRepositoryTest {

    // 2026-10-03 is a Saturday.
    private var now: Instant = at("2026-10-03T23:00:00")

    private fun at(local: String): Instant = OffsetDateTime.parse("$local+05:30").toInstant()

    private val stationsJson = """
        [
          {"id":"dadar","name":"Dadar","lat":19.0183,"lng":72.8429,"lines":["WESTERN","CENTRAL"],"providerCodes":["DDR","DR"]},
          {"id":"thane","name":"Thane","lat":19.1860,"lng":72.9756,"lines":["CENTRAL"],"providerCodes":["TNA"]}
        ]
    """.trimIndent()

    private class FakeStationDao : StationDao {
        val rows = mutableListOf<StationEntity>()
        override suspend fun count() = rows.size
        override suspend fun getAll() = rows.toList()
        override suspend fun insertAll(stations: List<StationEntity>) {
            rows += stations
        }
    }

    private class FakeTimetableDao : TimetableDao {
        val entries = mutableListOf<TimetableEntryEntity>()
        val metas = mutableMapOf<String, TimetableMetaEntity>()
        override suspend fun entries(providerCode: String) = entries.filter { it.providerCode == providerCode }
        override suspend fun meta(providerCode: String) = metas[providerCode]
        override suspend fun allMeta() = metas.values.toList()
        override suspend fun deleteEntries(providerCode: String) {
            entries.removeAll { it.providerCode == providerCode }
        }
        override suspend fun insertEntries(entries: List<TimetableEntryEntity>) {
            this.entries += entries
        }
        override suspend fun insertMeta(meta: TimetableMetaEntity) {
            metas[meta.providerCode] = meta
        }
    }

    private class FakeProvider : TrainDataProvider {
        val timetables = mutableMapOf<String, List<ScheduledDeparture>>()
        val live = mutableMapOf<String, List<Departure>>()
        var liveDelayMillis = 0L
        var liveFails = false
        var timetableFails = false
        val liveCalls = mutableListOf<String>()
        val timetableCalls = mutableListOf<String>()

        override suspend fun timetable(stationCode: String): List<ScheduledDeparture> {
            timetableCalls += stationCode
            if (timetableFails) throw IOException("offline")
            return timetables[stationCode].orEmpty()
        }

        override suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure> {
            liveCalls += stationCode
            delay(liveDelayMillis)
            if (liveFails) throw IOException("offline")
            return live[stationCode].orEmpty()
        }
    }

    private val provider = FakeProvider()
    private val timetableDao = FakeTimetableDao()
    private val repository = DepartureRepository(
        stations = StationRepository(FakeStationDao()) { stationsJson },
        provider = provider,
        timetableDao = timetableDao,
        now = { now },
    )

    private val everyDay = DayOfWeek.entries.toSet()

    private fun scheduled(
        number: String,
        time: String,
        destination: String = "Thane",
        dayOffset: Int = 0,
        runDays: Set<DayOfWeek> = everyDay,
        type: TrainType = TrainType.LOCAL,
    ) = ScheduledDeparture(
        trainNumber = number,
        trainName = "$destination Local",
        destinationCode = "TNA",
        destinationName = destination,
        departure = LocalTime.parse(time),
        dayOffset = dayOffset,
        runDays = runDays,
        trainType = type,
    )

    private fun live(
        number: String,
        scheduled: String,
        expected: String,
        delay: Int,
        status: DepartureStatus = DepartureStatus.UPCOMING,
        platform: String? = "8",
    ) = Departure(
        trainNumber = number,
        trainName = "Local",
        destinationCode = "TNA",
        destinationName = "TNA",
        scheduledTime = LocalTime.parse(scheduled),
        expectedTime = at(expected),
        delayMinutes = delay,
        platform = platform,
        trainType = TrainType.LOCAL,
        status = status,
        isLive = true,
    )

    @Test
    fun `unknown station returns null`() = runTest {
        assertNull(repository.nextDepartures("nowhere", 5))
    }

    @Test
    fun `live data is laid over the timetable`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"), scheduled("2", "23:10"))
        provider.live["TNA"] = listOf(live("1", "23:05", "2026-10-03T23:13:00", delay = 8))

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(BoardSource.LIVE, board.source)
        assertEquals(listOf("2", "1"), board.departures.map { it.trainNumber })
        val delayed = board.departures.first { it.trainNumber == "1" }
        assertTrue(delayed.isLive)
        assertEquals(8, delayed.delayMinutes)
        assertEquals("8", delayed.platform)
        // The live board carries only a code; the name comes from the timetable.
        assertEquals("Thane", delayed.destinationName)
        assertFalse(board.departures.first { it.trainNumber == "2" }.isLive)
    }

    @Test
    fun `late train scheduled before now is still shown and departed train is dropped`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "22:40"), scheduled("2", "23:01"))
        provider.live["TNA"] = listOf(
            live("1", "22:40", "2026-10-03T23:06:00", delay = 26),
            live("2", "23:01", "2026-10-03T23:01:00", delay = 0, status = DepartureStatus.DEPARTED),
        )

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(listOf("1"), board.departures.map { it.trainNumber })
    }

    @Test
    fun `slow live call falls back to the timetable after the timeout`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"))
        provider.liveDelayMillis = 60_000

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(BoardSource.SCHEDULED, board.source)
        assertEquals(listOf("1"), board.departures.map { it.trainNumber })
        assertEquals(DepartureRepository.LIVE_TIMEOUT.toMillis(), currentTime)
    }

    @Test
    fun `live error falls back to the timetable`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"))
        provider.liveFails = true

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(BoardSource.SCHEDULED, board.source)
        assertEquals(1, board.departures.size)
        assertNull(board.departures.single().delayMinutes)
    }

    @Test
    fun `no cache and no network is unavailable`() = runTest {
        provider.liveFails = true
        provider.timetableFails = true

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(BoardSource.UNAVAILABLE, board.source)
        assertTrue(board.departures.isEmpty())
    }

    @Test
    fun `cached timetable is used when the network is gone`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"))
        repository.nextDepartures("thane", 5)
        provider.liveFails = true
        provider.timetableFails = true
        now = now.plusSeconds(120)

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(BoardSource.SCHEDULED, board.source)
        assertEquals(listOf("1"), board.departures.map { it.trainNumber })
        assertEquals(listOf("TNA"), provider.timetableCalls)
    }

    @Test
    fun `window spans midnight`() = runTest {
        now = at("2026-10-03T23:50:00")
        provider.liveFails = true
        provider.timetables["TNA"] = listOf(
            scheduled("after", "00:04", dayOffset = 1),
            scheduled("before", "23:55"),
            scheduled("morning", "06:00"),
        )

        val board = repository.nextDepartures("thane", 5)!!

        assertEquals(listOf("before", "after"), board.departures.map { it.trainNumber })
        assertEquals(at("2026-10-04T00:04:00"), board.departures.last().expectedTime)
    }

    @Test
    fun `run days are those of the day the train started`() = runTest {
        now = at("2026-10-03T23:50:00") // Saturday night
        provider.liveFails = true
        provider.timetables["TNA"] = listOf(
            scheduled("weekdays", "23:55", runDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            scheduled("saturday", "23:56", runDays = setOf(DayOfWeek.SATURDAY)),
            // Reaches here just after midnight on Sunday, having started on Saturday.
            scheduled("startedSaturday", "00:04", dayOffset = 1, runDays = setOf(DayOfWeek.SATURDAY)),
            scheduled("startedSunday", "00:05", dayOffset = 1, runDays = setOf(DayOfWeek.SUNDAY)),
        )

        val board = repository.nextDepartures("thane", 10)!!

        assertEquals(listOf("saturday", "startedSaturday"), board.departures.map { it.trainNumber })
    }

    @Test
    fun `expresses are left out unless asked for`() = runTest {
        provider.liveFails = true
        provider.timetables["TNA"] = listOf(
            scheduled("local", "23:05"),
            scheduled("express", "23:06", type = TrainType.EXPRESS),
        )

        assertEquals(listOf("local"), repository.nextDepartures("thane", 5)!!.departures.map { it.trainNumber })
        assertEquals(
            listOf("local", "express"),
            repository.nextDepartures("thane", 5, localsOnly = false)!!.departures.map { it.trainNumber },
        )
    }

    @Test
    fun `interchange merges every provider code`() = runTest {
        provider.liveFails = true
        provider.timetables["DDR"] = listOf(scheduled("w1", "23:04", "Borivali"), scheduled("w2", "23:20", "Virar"))
        provider.timetables["DR"] = listOf(scheduled("c1", "23:10", "Thane"))

        val board = repository.nextDepartures("dadar", 5)!!

        assertEquals(listOf("w1", "c1", "w2"), board.departures.map { it.trainNumber })
        assertEquals(setOf("DDR", "DR"), provider.liveCalls.toSet())
    }

    @Test
    fun `one code live and the other not still counts as live`() = runTest {
        provider.timetables["DDR"] = listOf(scheduled("w1", "23:04"))
        provider.timetables["DR"] = listOf(scheduled("c1", "23:10"))
        provider.live["DR"] = listOf(live("c1", "23:10", "2026-10-03T23:12:00", delay = 2))

        val board = repository.nextDepartures("dadar", 5)!!

        assertEquals(BoardSource.LIVE, board.source)
        assertEquals(listOf("w1", "c1"), board.departures.map { it.trainNumber })
    }

    @Test
    fun `live board is cached for a minute and the timetable until refreshed`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:30"))

        repository.nextDepartures("thane", 5)
        now = now.plusSeconds(59)
        repository.nextDepartures("thane", 5)
        assertEquals(1, provider.liveCalls.size)

        now = now.plusSeconds(2)
        repository.nextDepartures("thane", 5)
        assertEquals(2, provider.liveCalls.size)
        assertEquals(1, provider.timetableCalls.size)
    }

    @Test
    fun `count limits the result`() = runTest {
        provider.liveFails = true
        provider.timetables["TNA"] = (1..9).map { scheduled("$it", "23:0$it") }

        assertEquals(listOf("1", "2", "3"), repository.nextDepartures("thane", 3)!!.departures.map { it.trainNumber })
    }

    @Test
    fun `only stale timetables are refreshed and empty ones are remembered`() = runTest {
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"))
        repository.nextDepartures("thane", 5)
        repository.nextDepartures("dadar", 5)
        assertEquals(1, timetableDao.metas.getValue("TNA").localCount)
        // An empty timetable is stored too, so it is not fetched on every visit.
        assertEquals(0, timetableDao.metas.getValue("DR").localCount)
        provider.timetableCalls.clear()

        assertEquals(0, repository.refreshStaleTimetables(Duration.ofDays(7)))
        now = now.plus(Duration.ofDays(8))
        provider.timetables["TNA"] = listOf(scheduled("1", "23:05"), scheduled("2", "23:06"))
        assertEquals(3, repository.refreshStaleTimetables(Duration.ofDays(7)))

        assertEquals(setOf("TNA", "DDR", "DR"), provider.timetableCalls.toSet())
        assertEquals(2, timetableDao.entries("TNA").size)
    }
}

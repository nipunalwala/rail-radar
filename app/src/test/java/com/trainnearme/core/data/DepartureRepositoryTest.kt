package com.trainnearme.core.data

import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.StopState
import com.trainnearme.core.model.TrainPosition
import com.trainnearme.core.model.TrainStop
import com.trainnearme.core.model.TrainType
import com.trainnearme.testing.FakeProvider
import com.trainnearme.testing.FakeStationDao
import com.trainnearme.testing.FakeTimetableDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
        originCode = "PNVL",
        originName = "Panvel Junction",
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
        originCode = "PNVL",
        originName = "PNVL",
    )

    private fun position(number: String) = TrainPosition(
        trainNumber = number,
        trainName = "Local",
        finished = false,
        isLive = true,
        updatedAt = now,
        delayMinutes = 3,
        currentCode = "DR",
        currentName = "Dadar Central",
        atCurrent = false,
        nextCode = "TNA",
        nextName = "Thane Jn",
        stops = listOf(
            TrainStop("DR", "Dadar Central", now, now, 3, "1", StopState.PASSED),
            TrainStop("XX", "Somewhere Else", null, null, null, null, StopState.AHEAD),
            TrainStop("TNA", "Thane Jn", null, null, null, null, StopState.AHEAD),
        ),
    )

    @Test
    fun `train position spells stations the app's way`() = runTest {
        provider.positions["1"] = position("1")

        val found = repository.trainPosition("1")!!

        assertEquals("Dadar", found.currentName)
        assertEquals("Thane", found.nextName)
        // A station outside the app's list keeps the provider's name.
        assertEquals(listOf("Dadar", "Somewhere Else", "Thane"), found.stops.map { it.name })
    }

    @Test
    fun `asking for the same train again within a minute makes no request`() = runTest {
        provider.positions["1"] = position("1")

        repository.trainPosition("1")
        now = now.plusSeconds(30)
        repository.trainPosition("1")
        assertEquals(listOf("1"), provider.positionCalls)

        now = now.plusSeconds(60)
        repository.trainPosition("1")
        assertEquals(listOf("1", "1"), provider.positionCalls)
    }

    @Test
    fun `train position is null when it cannot be fetched and is not remembered`() = runTest {
        assertNull(repository.trainPosition("1"))

        provider.positions["1"] = position("1")
        assertEquals("Dadar", repository.trainPosition("1")!!.currentName)
    }

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
        // So does the origin, which is not in this test's station list.
        assertEquals("Panvel Junction", delayed.originName)
        assertEquals("Panvel Junction", board.departures.first { it.trainNumber == "2" }.originName)
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
    fun `destination uses the app's station name, then the provider's`() = runTest {
        provider.liveFails = true
        provider.timetables["DR"] = listOf(
            // TNA is a known station, spelt "Thane" in the app.
            scheduled("known", "23:05", destination = "Thane Jn"),
            scheduled("unknown", "23:06", destination = "Pune Jn").copy(destinationCode = "PUNE"),
        )

        val names = repository.nextDepartures("dadar", 5)!!.departures.associate { it.trainNumber to it.destinationName }

        assertEquals(mapOf("known" to "Thane", "unknown" to "Pune Jn"), names)
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

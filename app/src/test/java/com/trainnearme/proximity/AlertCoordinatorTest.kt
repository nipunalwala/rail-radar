package com.trainnearme.proximity

import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.ALERT_COOLDOWN
import com.trainnearme.core.domain.AlertLine
import com.trainnearme.core.domain.StationAlertState
import com.trainnearme.core.domain.alertLines
import com.trainnearme.core.domain.onEnter
import com.trainnearme.core.domain.onExit
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureBoard
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.TrainType
import com.trainnearme.testing.FakeAlertScheduler
import com.trainnearme.testing.FakeAlertStateStore
import com.trainnearme.testing.FakeSettingsRepository
import com.trainnearme.testing.FakeStationDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

class AlertCoordinatorTest {

    private var now: Instant = OffsetDateTime.parse("2026-10-03T18:00:00+05:30").toInstant()

    // Parel (Central) and Prabhadevi (Western) are about 290 m apart.
    private val stationsJson = """
        [
          {"id":"parel","name":"Parel","lat":19.0094,"lng":72.8376,"lines":["CENTRAL"],"providerCodes":["PR"]},
          {"id":"prabhadevi","name":"Prabhadevi","lat":19.0082,"lng":72.8351,"lines":["WESTERN"],"providerCodes":["PBHD"]},
          {"id":"thane","name":"Thane","lat":19.1860,"lng":72.9756,"lines":["CENTRAL"],"providerCodes":["TNA"]}
        ]
    """.trimIndent()

    private val stations = StationRepository(FakeStationDao()) { stationsJson }
    private val settings = FakeSettingsRepository()
    private val store = FakeAlertStateStore()
    private val scheduler = FakeAlertScheduler()
    private val coordinator = AlertCoordinator(stations, settings, store, scheduler, now = { now })

    private val atParel = LatLng(19.0094, 72.8376)

    // State machine

    @Test
    fun `entering alerts once and staying does not repeat`() {
        val first = StationAlertState().onEnter(now)
        assertTrue(first.alert)
        assertTrue(first.state.inside)

        val again = first.state.onEnter(now.plusSeconds(60))
        assertFalse(again.alert)
        assertEquals(first.state, again.state)
    }

    @Test
    fun `coming straight back after leaving does not alert but a later return does`() {
        val left = StationAlertState(inside = true).onExit(now)
        assertEquals(StationAlertState(inside = false, lastExitAt = now), left)

        val jitter = left.onEnter(now.plus(ALERT_COOLDOWN).minusSeconds(1))
        assertFalse(jitter.alert)
        assertTrue(jitter.state.inside)

        val later = left.onEnter(now.plus(ALERT_COOLDOWN))
        assertTrue(later.alert)
    }

    @Test
    fun `an exit while already outside changes nothing`() {
        val outside = StationAlertState(inside = false, lastExitAt = now)
        assertEquals(outside, outside.onExit(now.plusSeconds(600)))
    }

    // Coordinator

    @Test
    fun `one alert on entry, none while staying, a new one after leaving and returning`() = runTest {
        coordinator.onEnter(listOf("thane"), at = null)
        coordinator.onEnter(listOf("thane"), at = null)
        assertEquals(listOf("thane"), scheduler.scheduled)

        coordinator.onExit(listOf("thane"))
        assertEquals(listOf("thane"), scheduler.cancelled)

        now = now.plus(ALERT_COOLDOWN).plusSeconds(1)
        coordinator.onEnter(listOf("thane"), at = null)
        assertEquals(listOf("thane", "thane"), scheduler.scheduled)
    }

    @Test
    fun `re-entry inside the cooldown is silent`() = runTest {
        coordinator.onEnter(listOf("thane"), at = null)
        coordinator.onExit(listOf("thane"))
        now = now.plusSeconds(120)

        coordinator.onEnter(listOf("thane"), at = null)

        assertEquals(1, scheduler.scheduled.size)
        assertTrue(store.get("thane").inside)
    }

    @Test
    fun `alerts switched off still tracks where the user is`() = runTest {
        settings.update { it.copy(alertsEnabled = false) }

        coordinator.onEnter(listOf("thane"), at = null)

        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(store.get("thane").inside)
    }

    @Test
    fun `stations on lines that are not monitored do not alert`() = runTest {
        settings.update { it.copy(lines = setOf(Line.WESTERN)) }

        coordinator.onEnter(listOf("thane"), at = null)
        coordinator.onEnter(listOf("prabhadevi"), at = null)

        assertEquals(listOf("prabhadevi"), scheduler.scheduled)
    }

    @Test
    fun `two stations entered together give one alert, for the closer`() = runTest {
        coordinator.onEnter(listOf("prabhadevi", "parel"), at = atParel)

        assertEquals(listOf("parel"), scheduler.scheduled)
        assertTrue(store.get("prabhadevi").inside)
        assertTrue(store.get("parel").inside)
    }

    @Test
    fun `a second station entered later replaces the first alert`() = runTest {
        coordinator.onEnter(listOf("parel"), at = atParel)
        coordinator.onEnter(listOf("prabhadevi"), at = null)

        assertEquals(listOf("parel", "prabhadevi"), scheduler.scheduled)
    }

    @Test
    fun `passing a station on a train gives no alert but the next real arrival does`() = runTest {
        // Boarded at Parel: the alert there is wanted.
        coordinator.onEnter(listOf("parel"), at = atParel)
        coordinator.onExit(listOf("parel"))
        assertEquals(listOf("parel"), scheduler.scheduled)

        // Thane is about 22 km up the same line; reaching it ten minutes later is a train ride.
        now = now.plusSeconds(600)
        coordinator.onEnter(listOf("thane"), at = null, speedMps = 15f)
        assertEquals(listOf("parel"), scheduler.scheduled)
        // The pass is still recorded, so leaving and coming back behaves normally.
        assertTrue(store.get("thane").inside)

        coordinator.onExit(listOf("thane"))
        now = now.plusSeconds(3 * 3600)
        coordinator.onEnter(listOf("thane"), at = null, speedMps = 1.2f)
        assertEquals(listOf("parel", "thane"), scheduler.scheduled)
    }

    @Test
    fun `unknown station ids are ignored`() = runTest {
        coordinator.onEnter(listOf("nowhere"), at = null)

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `reconcile clears a stale inside state far from the user and keeps a near one`() = runTest {
        coordinator.onEnter(listOf("thane"), at = null)
        coordinator.onEnter(listOf("parel"), at = atParel)

        // The exit from Thane was missed; the user is now at Parel.
        coordinator.reconcile(atParel, radiusMetres = 500)

        assertFalse(store.get("thane").inside)
        assertTrue(store.get("parel").inside)
        assertEquals(listOf("thane"), scheduler.cancelled)

        // Thane alerts again on the next visit, with no cooldown in the way.
        coordinator.onEnter(listOf("thane"), at = null)
        assertEquals("thane", scheduler.scheduled.last())
    }

    // Notification content

    private fun departure(
        destination: String,
        expected: String,
        platform: String? = null,
        delay: Int? = null,
        live: Boolean = delay != null,
    ) = Departure(
        trainNumber = destination,
        trainName = "Local",
        destinationCode = "X",
        destinationName = destination,
        scheduledTime = LocalTime.of(18, 0),
        expectedTime = OffsetDateTime.parse("2026-10-03T$expected+05:30").toInstant(),
        delayMinutes = delay,
        platform = platform,
        trainType = TrainType.LOCAL,
        status = if (live) DepartureStatus.UPCOMING else DepartureStatus.SCHEDULED,
        isLive = live,
    )

    @Test
    fun `alert lines carry minutes, platform and delay`() = runTest {
        val board = DepartureBoard(
            station = stations.byId("thane")!!,
            departures = listOf(
                departure("CSMT", "18:02:00", platform = "9", delay = 8),
                departure("Kalyan", "18:05:30", platform = "8", delay = 0),
                departure("Karjat", "18:09:00"),
                departure("Gone", "17:59:30", platform = "1", delay = 0),
            ),
            source = BoardSource.LIVE,
            asOf = now,
        )

        assertEquals(
            listOf(
                AlertLine("CSMT", 2, "9", 8),
                AlertLine("Kalyan", 5, "8", 0),
                // No live data: no delay is claimed.
                AlertLine("Karjat", 9, null, null),
                // Already due: shown as zero minutes, never negative.
                AlertLine("Gone", 0, "1", 0),
            ),
            alertLines(board),
        )
    }

    @Test
    fun `an unavailable board has no lines`() = runTest {
        val board = DepartureBoard(stations.byId("thane")!!, emptyList(), BoardSource.UNAVAILABLE, now)

        assertTrue(alertLines(board).isEmpty())
    }
}

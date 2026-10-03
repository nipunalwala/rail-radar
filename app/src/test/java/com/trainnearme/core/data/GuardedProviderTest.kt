package com.trainnearme.core.data

import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import com.trainnearme.testing.FakeProvider
import com.trainnearme.testing.FakeProviderHealthStore
import com.trainnearme.testing.FakeStationDao
import com.trainnearme.testing.FakeTimetableDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

class GuardedProviderTest {

    private var now: Instant = at("2026-10-30T18:00:00")

    private fun at(local: String): Instant = OffsetDateTime.parse("$local+05:30").toInstant()

    private val delegate = FakeProvider()
    private val health = FakeProviderHealthStore()
    private val provider = GuardedProvider(delegate, health, now = { now })

    private suspend fun fails(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Exception) {
            return e
        }
        throw AssertionError("expected a failure")
    }

    @Test
    fun `every request is counted, by month`() = runTest {
        provider.liveBoard("DR", 2)
        provider.timetable("DR")
        assertEquals(2, health.current.requestCount)
        assertEquals("2026-10", health.current.requestMonth)

        // A failed request still used the quota.
        delegate.liveFails = true
        fails { provider.liveBoard("DR", 2) }
        assertEquals(3, health.current.requestCount)

        delegate.liveFails = false
        now = at("2026-11-01T00:05:00")
        provider.liveBoard("DR", 2)
        assertEquals(1, health.current.requestCount)
        assertEquals("2026-11", health.current.requestMonth)
    }

    @Test
    fun `a rate limit pauses requests until the next day`() = runTest {
        delegate.failure = ProviderRateLimitedException()
        assertTrue(fails { provider.liveBoard("DR", 2) } is ProviderRateLimitedException)
        assertEquals(at("2026-10-31T00:00:00"), health.current.blockedUntil)

        // While paused, nothing reaches the provider and nothing is counted.
        delegate.failure = null
        assertTrue(fails { provider.liveBoard("DR", 2) } is ProviderRateLimitedException)
        assertTrue(fails { provider.timetable("DR") } is ProviderRateLimitedException)
        assertTrue(fails { provider.trainPosition("91006") } is ProviderRateLimitedException)
        assertTrue(delegate.positionCalls.isEmpty())
        assertEquals(1, delegate.liveCalls.size)
        assertTrue(delegate.timetableCalls.isEmpty())
        assertEquals(1, health.current.requestCount)

        now = at("2026-10-31T00:00:01")
        provider.liveBoard("DR", 2)
        assertEquals(2, delegate.liveCalls.size)
        assertNull(health.current.blockedUntil)
    }

    @Test
    fun `a rejected key is remembered until a request succeeds`() = runTest {
        delegate.failure = ProviderUnauthorizedException()
        assertTrue(fails { provider.liveBoard("DR", 2) } is ProviderUnauthorizedException)
        assertTrue(health.current.keyRejected)
        // Requests are not paused: the key may be fixed at any time.
        assertNull(health.current.blockedUntil)

        delegate.failure = null
        provider.liveBoard("DR", 2)
        assertFalse(health.current.keyRejected)
    }

    @Test
    fun `other failures change nothing but the count`() = runTest {
        delegate.failure = IOException("offline")
        fails { provider.liveBoard("DR", 2) }

        assertEquals(ProviderHealth(requestMonth = "2026-10", requestCount = 1), health.current)
    }

    @Test
    fun `the board falls back to the saved timetable when the limit is reached`() = runTest {
        val stationsJson = """[{"id":"thane","name":"Thane","lat":19.186,"lng":72.9756,"lines":["CENTRAL"],"providerCodes":["TNA"]}]"""
        delegate.timetables["TNA"] = listOf(
            ScheduledDeparture("1", "Local", "CSMT", "Mumbai CSMT", LocalTime.of(18, 5), 0, DayOfWeek.entries.toSet(), TrainType.LOCAL),
        )
        val repository = DepartureRepository(
            StationRepository(FakeStationDao()) { stationsJson },
            provider,
            FakeTimetableDao(),
        ) { now }
        assertEquals(BoardSource.LIVE, repository.nextDepartures("thane", 5)!!.source)

        delegate.failure = ProviderRateLimitedException()
        now = now.plusSeconds(120)
        val limited = repository.nextDepartures("thane", 5)!!
        assertEquals(BoardSource.SCHEDULED, limited.source)
        assertEquals(listOf("1"), limited.departures.map { it.trainNumber })

        // Later boards cost no requests at all until tomorrow.
        val callsSoFar = delegate.liveCalls.size
        now = now.plusSeconds(120)
        assertEquals(BoardSource.SCHEDULED, repository.nextDepartures("thane", 5)!!.source)
        assertEquals(callsSoFar, delegate.liveCalls.size)
    }
}

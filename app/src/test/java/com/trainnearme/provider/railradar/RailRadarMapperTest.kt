package com.trainnearme.provider.railradar

import com.trainnearme.core.domain.effectiveTime
import com.trainnearme.core.domain.upcomingDepartures
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.TrainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime

/** Runs against responses recorded from RailRadar for Dadar (DR) on 2026-10-03 at 23:01 IST. */
class RailRadarMapperTest {

    private val recordedAt = OffsetDateTime.parse("2026-10-03T23:01:56+05:30").toInstant()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/railradar/$name")) { "missing fixture $name" }.readText()

    private fun liveBoard(): List<Departure> =
        RailRadarProvider.json
            .decodeFromString<EnvelopeDto<LiveBoardDto>>(fixture("live_DR.json"))
            .data!!.trains.mapNotNull { it.toDeparture() }

    @Test
    fun `live board maps locals and expresses`() {
        val board = liveBoard()

        assertEquals(47, board.size)
        assertEquals(30, board.count { it.trainType == TrainType.LOCAL })
    }

    @Test
    fun `live train carries delay platform and status`() {
        val train = liveBoard().first { it.trainNumber == "97434" }

        assertEquals("PR", train.destinationCode)
        assertEquals(LocalTime.of(22, 54), train.scheduledTime)
        assertEquals(DepartureStatus.AT_STATION, train.status)
        assertEquals(8, train.delayMinutes)
        assertEquals("9", train.platform)
        assertTrue(train.isLive)
        assertEquals(OffsetDateTime.parse("2026-10-03T23:02:00+05:30").toInstant(), train.expectedTime)
    }

    @Test
    fun `train without live data is marked scheduled`() {
        val train = liveBoard().first { it.trainNumber == "97439" }

        assertEquals(DepartureStatus.SCHEDULED, train.status)
        assertFalse(train.isLive)
        assertNull(train.delayMinutes)
    }

    @Test
    fun `blank platform becomes null`() {
        assertNull(liveBoard().first { it.trainNumber == "15087" }.platform)
    }

    @Test
    fun `upcoming departures are local, not departed and soonest first`() {
        val upcoming = upcomingDepartures(liveBoard(), recordedAt, count = 5)

        assertEquals(5, upcoming.size)
        assertTrue(upcoming.all { it.trainType == TrainType.LOCAL })
        assertTrue(upcoming.none { it.status == DepartureStatus.DEPARTED })
        val times = upcoming.map { it.effectiveTime(recordedAt) }
        assertEquals(times.sorted(), times)
        assertEquals(OffsetDateTime.parse("2026-10-03T23:02:00+05:30").toInstant(), times.first())
    }

    @Test
    fun `timetable maps destination name and run days`() {
        val timetable = RailRadarProvider.json
            .decodeFromString<EnvelopeDto<TimetableDto>>(fixture("timetable_DR.json"))
            .data!!.trains.mapNotNull { it.toScheduledDeparture() }

        val train = timetable.first { it.trainNumber == "97439" }
        assertEquals("Thane", train.destinationName)
        assertEquals("TNA", train.destinationCode)
        assertEquals(LocalTime.of(0, 4), train.departure)
        assertEquals(1, train.dayOffset)
        assertEquals(DayOfWeek.entries.toSet(), train.runDays)
        assertEquals(TrainType.LOCAL, train.trainType)
    }
}

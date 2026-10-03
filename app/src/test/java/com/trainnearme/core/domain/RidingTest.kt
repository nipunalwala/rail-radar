package com.trainnearme.core.domain

import com.trainnearme.core.data.station.STATIONS_ASSET
import com.trainnearme.core.data.station.parseStations
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime

/** Uses the real station list, so the distances are the real ones. */
class RidingTest {

    private val stations = parseStations(File("src/main/assets/$STATIONS_ASSET").readText())
    private fun station(id: String) = stations.first { it.id == id }

    private val now: Instant = OffsetDateTime.parse("2026-10-03T18:00:00+05:30").toInstant()
    private val radius = 500

    private fun exited(secondsAgo: Long) = StationAlertState(inside = false, lastExitAt = now.minusSeconds(secondsAgo))

    @Test
    fun `nowhere before means not riding, whatever the speed`() {
        // Arriving by auto-rickshaw is fast too, and that person wants the alert.
        assertFalse(isRidingThrough(station("andheri"), radius, speedMps = 12f, others = emptyMap(), now = now))
    }

    @Test
    fun `left the previous station on the line two minutes ago means riding`() {
        // Jogeshwari to Andheri is about 1.7 km: two minutes is a train, not a walk.
        val others = mapOf(station("jogeshwari") to exited(secondsAgo = 120))

        assertTrue(isRidingThrough(station("andheri"), radius, speedMps = null, others = others, now = now))
    }

    @Test
    fun `walking from the previous station is not riding`() {
        val others = mapOf(station("jogeshwari") to exited(secondsAgo = 11 * 60))

        assertFalse(isRidingThrough(station("andheri"), radius, speedMps = 1.4f, others = others, now = now))
    }

    @Test
    fun `a previous station left long ago does not count`() {
        val others = mapOf(station("jogeshwari") to exited(secondsAgo = 13 * 60))

        assertFalse(isRidingThrough(station("andheri"), radius, speedMps = null, others = others, now = now))
    }

    @Test
    fun `a previous station on another line does not count`() {
        // Parel is Central only and Prabhadevi is Western only, though they are neighbours.
        val others = mapOf(station("parel") to exited(secondsAgo = 30))

        assertFalse(isRidingThrough(station("prabhadevi"), radius, speedMps = null, others = others, now = now))
    }

    @Test
    fun `stations whose radii overlap cannot show a ride by timing alone`() {
        // Matunga Road and Dadar are about 1 km apart; at 500 m their radii
        // touch, so stepping from one to the other says nothing about speed.
        val gap = station("matunga-road").distanceTo(station("dadar").lat, station("dadar").lng) - 2 * radius
        assertTrue(gap < 200)
        val others = mapOf(station("matunga-road") to exited(secondsAgo = 60))

        assertFalse(isRidingThrough(station("dadar"), radius, speedMps = null, others = others, now = now))
    }

    @Test
    fun `fast while still inside the previous station means its exit is late`() {
        val others = mapOf(station("matunga-road") to StationAlertState(inside = true))

        assertTrue(isRidingThrough(station("dadar"), radius, speedMps = 14f, others = others, now = now))
        assertFalse(isRidingThrough(station("dadar"), radius, speedMps = 1.2f, others = others, now = now))
        assertFalse(isRidingThrough(station("dadar"), radius, speedMps = null, others = others, now = now))
    }

    // High accuracy mode

    private val andheri = station("andheri")
    private val atAndheri = LatLng(andheri.lat, andheri.lng)
    // 0.005 degrees of latitude is about 555 m.
    private val justOutside = LatLng(andheri.lat + 0.005, andheri.lng)
    private val wellOutside = LatLng(andheri.lat + 0.0065, andheri.lng)

    @Test
    fun `a position inside the radius enters the station once`() {
        val first = proximityChange(stations, stations, inside = emptySet(), here = atAndheri, radiusMetres = radius)
        assertEquals(listOf("andheri"), first.entered)
        assertTrue(first.exited.isEmpty())

        val second = proximityChange(stations, stations, inside = setOf("andheri"), here = atAndheri, radiusMetres = radius)
        assertTrue(second.entered.isEmpty())
        assertTrue(second.exited.isEmpty())
    }

    @Test
    fun `leaving needs a margin beyond the radius`() {
        val wobble = proximityChange(stations, stations, setOf("andheri"), justOutside, radius)
        assertTrue(wobble.exited.isEmpty())
        // Not inside, and not close enough to enter either.
        assertTrue(proximityChange(stations, stations, emptySet(), justOutside, radius).entered.isEmpty())

        val gone = proximityChange(stations, stations, setOf("andheri"), wellOutside, radius)
        assertEquals(listOf("andheri"), gone.exited)
    }

    @Test
    fun `only monitored stations are entered but any station can be left`() {
        val central = stations.onLines(setOf(Line.CENTRAL))

        // Andheri is not on the Central line.
        assertTrue(proximityChange(central, stations, emptySet(), atAndheri, radius).entered.isEmpty())
        // It was entered before the lines setting changed; it can still be left.
        assertEquals(listOf("andheri"), proximityChange(central, stations, setOf("andheri"), wellOutside, radius).exited)
    }

    @Test
    fun `an unknown station id is dropped`() {
        assertEquals(listOf("gone"), proximityChange(stations, stations, setOf("gone"), atAndheri, radius).exited)
    }
}

package com.trainnearme.core.domain

import com.trainnearme.core.data.station.STATIONS_ASSET
import com.trainnearme.core.data.station.parseStations
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Plans geofences over the real station list. */
class GeofencePlanTest {

    private val stations = parseStations(File("src/main/assets/$STATIONS_ASSET").readText())
    private val allLines = Line.entries.toSet()
    private val dadar = LatLng(19.0183, 72.8429)
    private val kasara = stations.first { it.id == "kasara" }.let { LatLng(it.lat, it.lng) }

    @Test
    fun `lines that fit get a fence each and no refresh fence`() {
        val plan = planGeofences(stations, setOf(Line.WESTERN), dadar, stationRadiusMetres = 500)

        assertEquals(37, plan.stations.size)
        assertTrue(plan.stations.all { Line.WESTERN in it.lines })
        assertNull(plan.refresh)
    }

    @Test
    fun `too many stations keeps the nearest and adds a refresh fence`() {
        val plan = planGeofences(stations, allLines, dadar, stationRadiusMetres = 500)

        assertEquals(MAX_STATION_GEOFENCES, plan.stations.size)
        assertTrue(plan.stations.size + 1 <= 100)
        assertTrue(plan.stations.any { it.id == "dadar" })
        // The far ends of the network are the ones left out.
        assertFalse(plan.stations.any { it.id == "dahanu-road" })
        assertFalse(plan.stations.any { it.id == "kasara" })

        val kept = plan.stations.maxOf { it.distanceTo(dadar.lat, dadar.lng) }
        val leftOut = stations.filter { it !in plan.stations }.minOf { it.distanceTo(dadar.lat, dadar.lng) }
        assertTrue(kept <= leftOut)
    }

    @Test
    fun `refresh fence is left before any left-out station can be reached`() {
        val plan = planGeofences(stations, allLines, dadar, stationRadiusMetres = 500)
        val refresh = assertNotNullValue(plan.refresh)

        assertEquals(dadar.lat, refresh.lat, 0.0)
        assertEquals(dadar.lng, refresh.lng, 0.0)
        val nearestLeftOut = stations.filter { it !in plan.stations }.minOf { it.distanceTo(dadar.lat, dadar.lng) }
        // The user exits the fence while still outside that station's own 500 m radius.
        assertTrue(refresh.radiusMetres < nearestLeftOut - 500)
        assertTrue(refresh.radiusMetres >= 1_000f)
    }

    @Test
    fun `the plan follows the user`() {
        val plan = planGeofences(stations, allLines, kasara, stationRadiusMetres = 500)

        assertTrue(plan.stations.any { it.id == "kasara" })
        assertFalse(plan.stations.any { it.id == "churchgate" })
    }

    @Test
    fun `without a position the central stations are chosen and no refresh fence is set`() {
        val plan = planGeofences(stations, allLines, here = null, stationRadiusMetres = 500)

        assertEquals(MAX_STATION_GEOFENCES, plan.stations.size)
        assertTrue(plan.stations.any { it.id == "dadar" })
        assertNull(plan.refresh)
    }

    @Test
    fun `a small limit is respected`() {
        val plan = planGeofences(stations, allLines, dadar, stationRadiusMetres = 500, limit = 5)

        assertEquals(5, plan.stations.size)
        assertEquals("dadar", plan.stations.first().id)
        // The fence never shrinks below its minimum, however close the next station is.
        assertTrue(assertNotNullValue(plan.refresh).radiusMetres >= 1_000f)
    }

    private fun <T : Any> assertNotNullValue(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}

package com.trainnearme.core.data.station

import com.trainnearme.core.domain.distanceMeters
import com.trainnearme.core.model.Line
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against the real bundled asset, so a bad regeneration fails the build. */
class StationRepositoryTest {

    // Unit tests run with the module directory as the working directory.
    private val asset = File("src/main/assets/$STATIONS_ASSET").readText()

    private class FakeStationDao : StationDao {
        val rows = mutableListOf<StationEntity>()
        var inserts = 0
        override suspend fun count() = rows.size
        override suspend fun getAll() = rows.sortedBy { it.name }
        override suspend fun insertAll(stations: List<StationEntity>) {
            inserts++
            rows += stations
        }
    }

    private fun repository(dao: StationDao = FakeStationDao()) = StationRepository(dao) { asset }

    @Test
    fun `asset has every station once with sane coordinates`() {
        val stations = parseStations(asset)

        assertEquals(109, stations.size)
        assertEquals(stations.size, stations.map { it.id }.toSet().size)
        assertEquals(37, stations.count { Line.WESTERN in it.lines })
        assertEquals(51, stations.count { Line.CENTRAL in it.lines })
        assertEquals(35, stations.count { Line.HARBOUR in it.lines })
        assertTrue(stations.all { it.lat in 18.7..20.05 && it.lng in 72.6..73.55 })
        assertTrue(stations.all { it.providerCodes.isNotEmpty() })
        // No provider code belongs to two stations.
        val codes = stations.flatMap { it.providerCodes }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `asset is imported once and survives the entity round trip`() = runTest {
        val dao = FakeStationDao()
        val repository = repository(dao)

        assertEquals(parseStations(asset).sortedBy { it.name }, repository.all())
        repository.all()
        assertEquals(1, dao.inserts)

        // A second repository on the same database does not import again.
        repository(dao).all()
        assertEquals(1, dao.inserts)
    }

    @Test
    fun `interchange has one code per railway`() = runTest {
        val dadar = repository().byId("dadar")!!

        assertEquals(setOf(Line.WESTERN, Line.CENTRAL), dadar.lines)
        assertEquals(setOf("DDR", "DR"), dadar.providerCodes.toSet())
    }

    @Test
    fun `nearest station to a point`() = runTest {
        // About 300 m west of Dadar station.
        assertEquals("dadar", repository().nearest(19.0170, 72.8400)?.id)
        // Just outside Churchgate.
        assertEquals("churchgate", repository().nearest(18.9350, 72.8265)?.id)
    }

    @Test
    fun `nearest respects monitored lines`() = runTest {
        val repository = repository()
        // Standing at Parel (Central); Prabhadevi (Western) is about 290 m away.
        val parel = repository.byId("parel")!!

        assertEquals("parel", repository.nearest(parel.lat, parel.lng)?.id)
        assertEquals("prabhadevi", repository.nearest(parel.lat, parel.lng, setOf(Line.WESTERN))?.id)
    }

    @Test
    fun `line filter keeps shared stations`() = runTest {
        val harbour = repository().onLines(setOf(Line.HARBOUR)).map { it.id }

        assertEquals(35, harbour.size)
        assertTrue("andheri" in harbour)
        assertTrue("vashi" in harbour)
        assertTrue("churchgate" !in harbour)
    }

    @Test
    fun `distance matches a known pair`() {
        // Churchgate to Marine Lines is roughly 1.2 km.
        val stations = parseStations(asset).associateBy { it.id }
        val a = stations.getValue("churchgate")
        val b = stations.getValue("marine-lines")

        assertEquals(1200.0, distanceMeters(a.lat, a.lng, b.lat, b.lng), 300.0)
    }
}

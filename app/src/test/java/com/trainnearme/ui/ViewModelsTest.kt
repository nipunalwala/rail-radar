package com.trainnearme.ui

import androidx.lifecycle.SavedStateHandle
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.StationSelection
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import com.trainnearme.testing.FakeLocationProvider
import com.trainnearme.testing.FakeProvider
import com.trainnearme.testing.FakeStationDao
import com.trainnearme.testing.FakeTimetableDao
import com.trainnearme.testing.MainDispatcherRule
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.home.HomeViewModel
import com.trainnearme.ui.picker.StationPickerViewModel
import com.trainnearme.ui.station.StationDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

class ViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private var now: Instant = OffsetDateTime.parse("2026-10-03T23:00:00+05:30").toInstant()

    private val stationsJson = """
        [
          {"id":"dadar","name":"Dadar","lat":19.0183,"lng":72.8429,"lines":["WESTERN","CENTRAL"],"providerCodes":["DDR","DR"]},
          {"id":"thane","name":"Thane","lat":19.1860,"lng":72.9756,"lines":["CENTRAL"],"providerCodes":["TNA"]},
          {"id":"matunga-road","name":"Matunga Road","lat":19.0276,"lng":72.8468,"lines":["WESTERN"],"providerCodes":["MRU"]}
        ]
    """.trimIndent()

    private val provider = FakeProvider().apply {
        timetables["TNA"] = (1..8).map { train("t$it", "23:0$it") }
        timetables["DR"] = listOf(train("d1", "23:05"))
    }
    private val stations = StationRepository(FakeStationDao()) { stationsJson }
    private val departures = DepartureRepository(stations, provider, FakeTimetableDao()) { now }
    private val location = FakeLocationProvider()
    private val selection = StationSelection()

    private fun train(number: String, time: String) = ScheduledDeparture(
        trainNumber = number,
        trainName = "Local",
        destinationCode = "CSMT",
        destinationName = "Mumbai CSMT",
        departure = LocalTime.parse(time),
        dayOffset = 0,
        runDays = DayOfWeek.entries.toSet(),
        trainType = TrainType.LOCAL,
    )

    private fun homeViewModel() = HomeViewModel(stations, departures, location, selection)

    private fun trainNumbers(board: BoardUiState): List<String> =
        (board as BoardUiState.Loaded).board.departures.map { it.trainNumber }

    @Test
    fun `home shows the nearest station when location is available`() = runTest {
        location.location = LatLng(19.1870, 72.9760) // beside Thane station

        val state = homeViewModel().state.value

        assertFalse(state.resolving)
        assertEquals("thane", state.station?.id)
        assertTrue(state.distanceMetres!! in 50..250)
        assertEquals(HomeViewModel.HOME_TRAIN_COUNT, trainNumbers(state.board).size)
    }

    @Test
    fun `home has no station without location or a pick`() = runTest {
        val state = homeViewModel().state.value

        assertFalse(state.resolving)
        assertNull(state.station)
        assertTrue(provider.liveCalls.isEmpty())
    }

    @Test
    fun `picking a station overrides location and use nearest goes back`() = runTest {
        location.location = LatLng(19.1870, 72.9760)
        val viewModel = homeViewModel()

        selection.pick("dadar")
        assertEquals("dadar", viewModel.state.value.station?.id)
        assertNull(viewModel.state.value.distanceMetres)
        assertEquals(listOf("d1"), trainNumbers(viewModel.state.value.board))

        viewModel.useNearest()
        assertEquals("thane", viewModel.state.value.station?.id)
    }

    @Test
    fun `home refresh loads the board again`() = runTest {
        selection.pick("thane")
        val viewModel = homeViewModel()
        assertEquals(1, provider.liveCalls.size)

        now = now.plusSeconds(300)
        viewModel.refresh()

        assertEquals(2, provider.liveCalls.size)
        assertEquals("t3", trainNumbers(viewModel.state.value.board).first())
    }

    @Test
    fun `home reports unavailable data as a loaded board`() = runTest {
        provider.liveFails = true
        provider.timetableFails = true
        selection.pick("thane")

        val board = (homeViewModel().state.value.board as BoardUiState.Loaded).board

        assertEquals(BoardSource.UNAVAILABLE, board.source)
        assertTrue(board.departures.isEmpty())
    }

    @Test
    fun `station detail shows the full board`() = runTest {
        val viewModel = StationDetailViewModel(
            SavedStateHandle(mapOf(StationDetailViewModel.STATION_ID_ARG to "thane")),
            stations,
            departures,
        )

        assertEquals("Thane", viewModel.state.value.station?.name)
        assertEquals(8, trainNumbers(viewModel.state.value.board).size)
    }

    @Test
    fun `station detail fails for an unknown station`() = runTest {
        val viewModel = StationDetailViewModel(
            SavedStateHandle(mapOf(StationDetailViewModel.STATION_ID_ARG to "nowhere")),
            stations,
            departures,
        )

        assertEquals(BoardUiState.Failed, viewModel.state.value.board)
    }

    @Test
    fun `picker filters by name and records the pick`() = runTest {
        val viewModel = StationPickerViewModel(stations, selection)
        assertEquals(listOf("Dadar", "Matunga Road", "Thane"), viewModel.results.value.map { it.name })

        viewModel.onQueryChange("  da ")
        assertEquals(listOf("Dadar"), viewModel.results.value.map { it.name })

        // "Thane" starts with the query; "Matunga Road" only contains it.
        viewModel.onQueryChange("t")
        assertEquals(listOf("Thane", "Matunga Road"), viewModel.results.value.map { it.name })

        viewModel.onQueryChange("zzz")
        assertTrue(viewModel.results.value.isEmpty())

        viewModel.pick(stations.byId("thane")!!)
        assertEquals("thane", selection.stationId.value)
    }
}

package com.trainnearme.ui

import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureStatus
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import com.trainnearme.core.model.TrainPosition
import com.trainnearme.core.model.TrainType
import com.trainnearme.testing.FakeProvider
import com.trainnearme.testing.FakeStationDao
import com.trainnearme.testing.FakeTimetableDao
import com.trainnearme.testing.MainDispatcherRule
import com.trainnearme.ui.train.PositionUiState
import com.trainnearme.ui.train.TrainSheetViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class TrainSheetViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-10-03T17:30:00Z")
    private val provider = FakeProvider()
    private val repository = DepartureRepository(
        StationRepository(FakeStationDao()) { "[]" },
        provider,
        FakeTimetableDao(),
    ) { now }
    private val viewModel = TrainSheetViewModel(repository)

    private val station = Station("thane", "Thane", 19.186, 72.9756, setOf(Line.CENTRAL), mapOf("TNA" to setOf(Line.CENTRAL)))

    private fun departure(number: String) = Departure(
        trainNumber = number,
        trainName = "Local",
        destinationCode = "CSMT",
        destinationName = "Mumbai CSMT",
        scheduledTime = LocalTime.of(23, 5),
        expectedTime = now,
        delayMinutes = 0,
        platform = "1",
        trainType = TrainType.LOCAL,
        status = DepartureStatus.UPCOMING,
        isLive = true,
    )

    private fun position(number: String) = TrainPosition(
        trainNumber = number,
        trainName = "Local",
        finished = false,
        isLive = true,
        updatedAt = now,
        delayMinutes = 0,
        currentCode = "MLND",
        currentName = "Mulund",
        atCurrent = true,
        nextCode = "TNA",
        nextName = "Thane",
        stops = emptyList(),
    )

    @Test
    fun `nothing is fetched until a train is tapped`() = runTest {
        assertNull(viewModel.state.value)
        assertTrue(provider.positionCalls.isEmpty())
    }

    @Test
    fun `tapping a train loads its position`() = runTest {
        provider.positions["1"] = position("1")

        viewModel.open(departure("1"), station)

        val state = viewModel.state.value!!
        assertEquals("1", state.departure.trainNumber)
        assertEquals("Mulund", (state.position as PositionUiState.Loaded).position.currentName)
        assertEquals(listOf("1"), provider.positionCalls)
    }

    @Test
    fun `a failed lookup can be retried`() = runTest {
        viewModel.open(departure("1"), station)
        assertEquals(PositionUiState.Failed, viewModel.state.value!!.position)

        provider.positions["1"] = position("1")
        viewModel.retry()

        assertTrue(viewModel.state.value!!.position is PositionUiState.Loaded)
    }

    @Test
    fun `closing the sheet clears it`() = runTest {
        provider.positions["1"] = position("1")
        viewModel.open(departure("1"), station)

        viewModel.close()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `a slow answer for a closed sheet is dropped`() = runTest {
        provider.positions["1"] = position("1")
        provider.positions["2"] = position("2").copy(currentName = "Kalwa")

        viewModel.open(departure("1"), station)
        viewModel.open(departure("2"), station)

        val state = viewModel.state.value!!
        assertEquals("2", state.departure.trainNumber)
        assertEquals("Kalwa", (state.position as PositionUiState.Loaded).position.currentName)
    }
}

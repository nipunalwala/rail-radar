package com.trainnearme.ui

import androidx.lifecycle.SavedStateHandle
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.Settings
import com.trainnearme.core.model.TrainType
import com.trainnearme.core.permissions.AlertStatus
import com.trainnearme.core.permissions.PermissionStatus
import com.trainnearme.core.permissions.alertStatus
import com.trainnearme.testing.FakeAlertScheduler
import com.trainnearme.testing.FakeGeofenceSyncer
import com.trainnearme.testing.FakeLocationProvider
import com.trainnearme.testing.FakePermissionChecker
import com.trainnearme.ui.onboarding.OnboardingViewModel
import com.trainnearme.testing.FakeProvider
import com.trainnearme.testing.FakeSettingsRepository
import com.trainnearme.testing.FakeStationDao
import com.trainnearme.testing.FakeTimetableDao
import com.trainnearme.testing.MainDispatcherRule
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.home.HomeViewModel
import com.trainnearme.ui.picker.StationPickerViewModel
import com.trainnearme.ui.settings.SettingsViewModel
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
          {"id":"dadar","name":"Dadar","lat":19.0183,"lng":72.8429,"lines":["WESTERN","CENTRAL"],
           "providerCodes":["DDR","DR"],"codeLines":{"DDR":["WESTERN"],"DR":["CENTRAL"]}},
          {"id":"thane","name":"Thane","lat":19.1860,"lng":72.9756,"lines":["CENTRAL"],"providerCodes":["TNA"]},
          {"id":"matunga-road","name":"Matunga Road","lat":19.0276,"lng":72.8468,"lines":["WESTERN"],"providerCodes":["MRU"]}
        ]
    """.trimIndent()

    private val besideThane = LatLng(19.1870, 72.9760)
    private val besideMatungaRoad = LatLng(19.0277, 72.8469)

    private val provider = FakeProvider().apply {
        timetables["TNA"] = (1..8).map { train("t$it", "23:0$it") }
        timetables["DR"] = listOf(train("central", "23:05"))
        timetables["DDR"] = listOf(train("western", "23:06"))
    }
    private val stations = StationRepository(FakeStationDao()) { stationsJson }
    private val departures = DepartureRepository(stations, provider, FakeTimetableDao()) { now }
    private val location = FakeLocationProvider()
    private val settings = FakeSettingsRepository()

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

    private val permissions = FakePermissionChecker()

    private val geofences = FakeGeofenceSyncer()

    private fun homeViewModel() = HomeViewModel(stations, departures, location, settings, permissions, geofences)

    @Test
    fun `alert status names the first thing that is missing`() {
        val all = PermissionStatus(notifications = true, foregroundLocation = true, backgroundLocation = true)

        assertEquals(AlertStatus.ON, alertStatus(true, all))
        assertEquals(AlertStatus.OFF, alertStatus(false, all))
        assertEquals(AlertStatus.OFF, alertStatus(false, all.copy(foregroundLocation = false)))
        assertEquals(
            AlertStatus.NEEDS_LOCATION,
            alertStatus(true, PermissionStatus(notifications = false, foregroundLocation = false, backgroundLocation = false)),
        )
        assertEquals(AlertStatus.NEEDS_BACKGROUND_LOCATION, alertStatus(true, all.copy(backgroundLocation = false)))
        assertEquals(AlertStatus.NEEDS_NOTIFICATIONS, alertStatus(true, all.copy(notifications = false)))
    }

    @Test
    fun `home shows alert status and follows permission and setting changes`() = runTest {
        permissions.status = PermissionStatus(notifications = true, foregroundLocation = false, backgroundLocation = false)
        location.location = null
        val viewModel = homeViewModel()
        assertEquals(AlertStatus.NEEDS_LOCATION, viewModel.state.value.alertStatus)
        assertNull(viewModel.state.value.station)

        // The user grants location in the permissions screen and comes back.
        permissions.status = PermissionStatus(notifications = true, foregroundLocation = true, backgroundLocation = false)
        location.location = besideThane
        viewModel.onResume()
        assertEquals(AlertStatus.NEEDS_BACKGROUND_LOCATION, viewModel.state.value.alertStatus)
        assertEquals("thane", viewModel.state.value.station?.id)

        assertEquals(0, geofences.syncs)
        permissions.status = permissions.status.copy(backgroundLocation = true)
        viewModel.onResume()
        assertEquals(AlertStatus.ON, viewModel.state.value.alertStatus)
        // Background location arriving is what makes geofences possible.
        assertEquals(1, geofences.syncs)
        viewModel.onResume()
        assertEquals(1, geofences.syncs)

        settings.update { it.copy(alertsEnabled = false) }
        assertEquals(AlertStatus.OFF, viewModel.state.value.alertStatus)
        assertEquals("thane", viewModel.state.value.station?.id)
    }

    @Test
    fun `onboarding re-reads permissions and records that it was finished`() = runTest {
        permissions.status = PermissionStatus(notifications = false, foregroundLocation = false, backgroundLocation = false)
        val viewModel = OnboardingViewModel(permissions, settings)
        assertFalse(viewModel.permissions.value.all)

        permissions.status = PermissionStatus(notifications = true, foregroundLocation = true, backgroundLocation = true)
        viewModel.refresh()
        assertTrue(viewModel.permissions.value.all)

        assertFalse(settings.current.onboardingDone)
        viewModel.finish()
        assertTrue(settings.current.onboardingDone)
    }

    private fun detailViewModel(stationId: String) = StationDetailViewModel(
        SavedStateHandle(mapOf(StationDetailViewModel.STATION_ID_ARG to stationId)),
        stations,
        departures,
        settings,
        alertScheduler,
    )

    private val alertScheduler = FakeAlertScheduler()

    @Test
    fun `test alert schedules an alert for the station on screen`() = runTest {
        detailViewModel("thane").testAlert()

        assertEquals(listOf("thane"), alertScheduler.scheduled)
    }

    private fun trainNumbers(board: BoardUiState): List<String> =
        (board as BoardUiState.Loaded).board.departures.map { it.trainNumber }

    @Test
    fun `home shows the nearest station when location is available`() = runTest {
        location.location = besideThane

        val state = homeViewModel().state.value

        assertFalse(state.resolving)
        assertEquals("thane", state.station?.id)
        assertTrue(state.distanceMetres!! in 50..250)
        assertEquals(Settings.DEFAULT_TRAIN_COUNT, trainNumbers(state.board).size)
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
        location.location = besideThane
        val viewModel = homeViewModel()

        settings.update { it.copy(pickedStationId = "dadar") }
        assertEquals("dadar", viewModel.state.value.station?.id)
        assertNull(viewModel.state.value.distanceMetres)
        assertEquals(listOf("central", "western"), trainNumbers(viewModel.state.value.board))

        viewModel.useNearest()
        assertNull(settings.current.pickedStationId)
        assertEquals("thane", viewModel.state.value.station?.id)
    }

    @Test
    fun `home refresh loads the board again`() = runTest {
        settings.update { it.copy(pickedStationId = "thane") }
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
        settings.update { it.copy(pickedStationId = "thane") }

        val board = (homeViewModel().state.value.board as BoardUiState.Loaded).board

        assertEquals(BoardSource.UNAVAILABLE, board.source)
        assertTrue(board.departures.isEmpty())
    }

    @Test
    fun `home follows the number of trains setting`() = runTest {
        settings.update { it.copy(pickedStationId = "thane") }
        val viewModel = homeViewModel()

        settings.update { it.copy(trainCount = 2) }

        assertEquals(listOf("t1", "t2"), trainNumbers(viewModel.state.value.board))
    }

    @Test
    fun `monitored lines change the nearest station`() = runTest {
        location.location = besideMatungaRoad
        val viewModel = homeViewModel()
        assertEquals("matunga-road", viewModel.state.value.station?.id)

        // Matunga Road is Western only, so with Central alone the nearest is Dadar.
        settings.update { it.copy(lines = setOf(Line.CENTRAL)) }

        assertEquals("dadar", viewModel.state.value.station?.id)
    }

    @Test
    fun `monitored lines change the trains at an interchange`() = runTest {
        settings.update { it.copy(pickedStationId = "dadar", lines = setOf(Line.WESTERN)) }
        val viewModel = homeViewModel()
        assertEquals(listOf("western"), trainNumbers(viewModel.state.value.board))
        assertEquals(listOf("DDR"), provider.liveCalls)

        settings.update { it.copy(lines = setOf(Line.CENTRAL)) }
        assertEquals(listOf("central"), trainNumbers(viewModel.state.value.board))
    }

    @Test
    fun `station detail shows the full board for the monitored lines`() = runTest {
        assertEquals("Thane", detailViewModel("thane").state.value.station?.name)
        assertEquals(8, trainNumbers(detailViewModel("thane").state.value.board).size)

        settings.update { it.copy(lines = setOf(Line.WESTERN)) }
        assertEquals(listOf("western"), trainNumbers(detailViewModel("dadar").state.value.board))
    }

    @Test
    fun `station detail fails for an unknown station`() = runTest {
        assertEquals(BoardUiState.Failed, detailViewModel("nowhere").state.value.board)
    }

    @Test
    fun `picker filters by name and saves the pick`() = runTest {
        val viewModel = StationPickerViewModel(stations, settings)
        assertEquals(listOf("Dadar", "Matunga Road", "Thane"), viewModel.results.value.map { it.name })

        viewModel.onQueryChange("  da ")
        assertEquals(listOf("Dadar"), viewModel.results.value.map { it.name })

        // "Thane" starts with the query; "Matunga Road" only contains it.
        viewModel.onQueryChange("t")
        assertEquals(listOf("Thane", "Matunga Road"), viewModel.results.value.map { it.name })

        viewModel.onQueryChange("zzz")
        assertTrue(viewModel.results.value.isEmpty())

        viewModel.pick(stations.byId("thane")!!)
        assertEquals("thane", settings.current.pickedStationId)
    }

    @Test
    fun `picker lists only stations on monitored lines`() = runTest {
        val viewModel = StationPickerViewModel(stations, settings)

        settings.update { it.copy(lines = setOf(Line.WESTERN)) }

        assertEquals(listOf("Dadar", "Matunga Road"), viewModel.results.value.map { it.name })
    }

    @Test
    fun `settings screen saves each change and keeps one line`() = runTest {
        val viewModel = SettingsViewModel(settings)
        assertEquals(Settings(), viewModel.settings.value)

        viewModel.setAlertsEnabled(false)
        viewModel.setRadius(800)
        viewModel.setTrainCount(3)
        viewModel.setSound(false)
        viewModel.setVibration(false)
        viewModel.setLineMonitored(Line.WESTERN, false)
        viewModel.setLineMonitored(Line.HARBOUR, false)
        // Central is the last line left, so this is ignored.
        viewModel.setLineMonitored(Line.CENTRAL, false)

        assertEquals(
            Settings(
                alertsEnabled = false,
                radiusMetres = 800,
                trainCount = 3,
                sound = false,
                vibration = false,
                lines = setOf(Line.CENTRAL),
            ),
            viewModel.settings.value,
        )
    }
}

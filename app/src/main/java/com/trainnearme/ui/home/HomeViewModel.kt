package com.trainnearme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.ProviderHealthStore
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.distanceTo
import com.trainnearme.core.location.LocationProvider
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import com.trainnearme.core.permissions.AlertStatus
import com.trainnearme.core.permissions.PermissionChecker
import com.trainnearme.core.permissions.alertStatus
import com.trainnearme.proximity.GeofenceSyncer
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.common.loadBoardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    /** True until it is known which station to show, or that there is none. */
    val resolving: Boolean = true,
    val station: Station? = null,
    /** Set when the station was found from the user's location rather than picked. */
    val distanceMetres: Int? = null,
    val board: BoardUiState = BoardUiState.Loading,
    /** Null until settings have been read. */
    val alertStatus: AlertStatus? = null,
    /** A problem with the train data source the user should know about. */
    val providerNotice: ProviderNotice? = null,
)

enum class ProviderNotice { KEY_REJECTED, LIMIT_REACHED }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stations: StationRepository,
    private val departures: DepartureRepository,
    private val location: LocationProvider,
    private val settings: SettingsRepository,
    private val permissionChecker: PermissionChecker,
    private val geofences: GeofenceSyncer,
    private val providerHealth: ProviderHealthStore,
) : ViewModel() {

    /** Everything that decides which station and trains the home screen shows. */
    private data class Inputs(
        val pickedStationId: String?,
        val lines: Set<Line>,
        val trainCount: Int,
        val locationAllowed: Boolean,
    )

    private var inputs = Inputs(null, Line.entries.toSet(), 0, false)
    private val permissions = MutableStateFlow(permissionChecker.current())

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.settings, permissions) { s, p ->
                Inputs(s.pickedStationId, s.lines, s.trainCount, p.foregroundLocation)
            }.distinctUntilChanged().collectLatest {
                inputs = it
                showStation()
            }
        }
        viewModelScope.launch {
            combine(settings.settings, permissions) { s, p -> alertStatus(s.alertsEnabled, p) }
                .collect { status -> _state.update { it.copy(alertStatus = status) } }
        }
        viewModelScope.launch {
            providerHealth.health.collect { health ->
                val notice = when {
                    health.keyRejected -> ProviderNotice.KEY_REJECTED
                    health.isBlocked(Instant.now()) -> ProviderNotice.LIMIT_REACHED
                    else -> null
                }
                _state.update { it.copy(providerNotice = notice) }
            }
        }
    }

    /** Permissions may have changed while the screen was away. */
    fun onResume() {
        val now = permissionChecker.current()
        // Geofences can only be registered once background location is granted.
        if (now.backgroundLocation != permissions.value.backgroundLocation) geofences.requestSync()
        permissions.value = now
    }

    fun refresh() {
        val stationId = _state.value.station?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(board = BoardUiState.Loading) }
            loadBoard(stationId)
        }
    }

    fun useNearest() {
        viewModelScope.launch { settings.update { it.copy(pickedStationId = null) } }
    }

    private suspend fun showStation() {
        show(resolving = true, station = null, distanceMetres = null)
        val picked = inputs.pickedStationId?.let { stations.byId(it) }
        if (picked != null) {
            show(resolving = false, station = picked, distanceMetres = null)
            loadBoard(picked.id)
            return
        }
        val here = location.current()
        val nearest = here?.let { stations.nearest(it.lat, it.lng, inputs.lines) }
        show(
            resolving = false,
            station = nearest,
            distanceMetres = nearest?.distanceTo(here.lat, here.lng)?.roundToInt(),
        )
        if (nearest != null) loadBoard(nearest.id)
    }

    private fun show(resolving: Boolean, station: Station?, distanceMetres: Int?) {
        _state.update {
            it.copy(
                resolving = resolving,
                station = station,
                distanceMetres = distanceMetres,
                board = BoardUiState.Loading,
            )
        }
    }

    private suspend fun loadBoard(stationId: String) {
        val board = departures.loadBoardState(stationId, inputs.trainCount, inputs.lines)
        _state.update { if (it.station?.id == stationId) it.copy(board = board) else it }
    }
}

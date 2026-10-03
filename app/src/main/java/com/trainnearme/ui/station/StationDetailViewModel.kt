package com.trainnearme.ui.station

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.model.Station
import com.trainnearme.proximity.AlertScheduler
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.common.loadBoardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationDetailUiState(
    val station: Station? = null,
    val board: BoardUiState = BoardUiState.Loading,
)

@HiltViewModel
class StationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stations: StationRepository,
    private val departures: DepartureRepository,
    private val settings: SettingsRepository,
    private val alerts: AlertScheduler,
) : ViewModel() {

    private val stationId: String = checkNotNull(savedStateHandle[STATION_ID_ARG])

    private val _state = MutableStateFlow(StationDetailUiState())
    val state: StateFlow<StationDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(station = stations.byId(stationId)) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(board = BoardUiState.Loading) }
            val lines = settings.settings.first().lines
            val board = departures.loadBoardState(stationId, FULL_BOARD_COUNT, lines)
            _state.update { it.copy(board = board) }
        }
    }

    /** Runs the alert for this station as if the user had just arrived. Debug builds only. */
    fun testAlert() = alerts.schedule(stationId)

    companion object {
        const val STATION_ID_ARG = "stationId"
        const val FULL_BOARD_COUNT = 40
    }
}

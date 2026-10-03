package com.trainnearme.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.Station
import com.trainnearme.core.model.TrainPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PositionUiState {
    data object Loading : PositionUiState
    data class Loaded(val position: TrainPosition) : PositionUiState
    data object Failed : PositionUiState
}

data class TrainSheetUiState(
    /** The board row that was tapped. */
    val departure: Departure,
    /** The station whose board the row was on. */
    val station: Station,
    val position: PositionUiState = PositionUiState.Loading,
)

/**
 * The sheet shown when a train on a board is tapped. The train's position is
 * fetched only then, because each lookup is a request against the quota.
 */
@HiltViewModel
class TrainSheetViewModel @Inject constructor(
    private val departures: DepartureRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TrainSheetUiState?>(null)

    /** Null while the sheet is closed. */
    val state: StateFlow<TrainSheetUiState?> = _state.asStateFlow()

    private var loading: Job? = null

    fun open(departure: Departure, station: Station) {
        _state.value = TrainSheetUiState(departure, station)
        load(departure.trainNumber)
    }

    fun retry() {
        val current = _state.value ?: return
        _state.value = current.copy(position = PositionUiState.Loading)
        load(current.departure.trainNumber)
    }

    fun close() {
        loading?.cancel()
        _state.value = null
    }

    private fun load(trainNumber: String) {
        loading?.cancel()
        loading = viewModelScope.launch {
            val position = departures.trainPosition(trainNumber)
            val result = if (position != null) PositionUiState.Loaded(position) else PositionUiState.Failed
            // The sheet may have been closed, or opened for another train, meanwhile.
            _state.update { if (it?.departure?.trainNumber == trainNumber) it.copy(position = result) else it }
        }
    }
}

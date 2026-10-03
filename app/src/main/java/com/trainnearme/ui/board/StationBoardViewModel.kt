package com.trainnearme.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.domain.upcomingDepartures
import com.trainnearme.core.model.Departure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed interface BoardUiState {
    data object Loading : BoardUiState
    data class Loaded(val departures: List<Departure>, val asOf: Instant) : BoardUiState
    data class Failed(val message: String) : BoardUiState
}

@HiltViewModel
class StationBoardViewModel @Inject constructor(
    private val provider: TrainDataProvider,
) : ViewModel() {

    // Fixed station until proximity detection picks it (milestone 3).
    val stationName = "Dadar (Central)"
    private val stationCode = "DR"

    private val _state = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = BoardUiState.Loading
            _state.value = try {
                val board = provider.liveBoard(stationCode, hoursAhead = 2)
                val now = Instant.now()
                BoardUiState.Loaded(upcomingDepartures(board, now, count = 10), now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BoardUiState.Failed(e.message ?: "Could not load trains")
            }
        }
    }
}

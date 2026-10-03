package com.trainnearme.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.model.DepartureBoard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BoardUiState {
    data object Loading : BoardUiState
    data class Loaded(val board: DepartureBoard) : BoardUiState
    data class Failed(val message: String) : BoardUiState
}

@HiltViewModel
class StationBoardViewModel @Inject constructor(
    private val departures: DepartureRepository,
) : ViewModel() {

    // Fixed station until proximity detection picks it (milestone 3).
    val stationName = "Dadar"
    private val stationId = "dadar"

    private val _state = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = BoardUiState.Loading
            _state.value = try {
                departures.nextDepartures(stationId, count = 10)
                    ?.let { BoardUiState.Loaded(it) }
                    ?: BoardUiState.Failed("Unknown station")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BoardUiState.Failed(e.message ?: "Could not load trains")
            }
        }
    }
}

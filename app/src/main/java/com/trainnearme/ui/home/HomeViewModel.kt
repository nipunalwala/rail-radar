package com.trainnearme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.StationSelection
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.distanceTo
import com.trainnearme.core.location.LocationProvider
import com.trainnearme.core.model.Station
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.common.loadBoardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    /** True until it is known which station to show, or that there is none. */
    val resolving: Boolean = true,
    val station: Station? = null,
    /** Set when the station was found from the user's location rather than picked. */
    val distanceMetres: Int? = null,
    val board: BoardUiState = BoardUiState.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stations: StationRepository,
    private val departures: DepartureRepository,
    private val location: LocationProvider,
    private val selection: StationSelection,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selection.stationId.collectLatest { picked -> showStation(picked) }
        }
    }

    fun refresh() {
        val stationId = _state.value.station?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(board = BoardUiState.Loading) }
            loadBoard(stationId)
        }
    }

    fun useNearest() = selection.clear()

    private suspend fun showStation(pickedId: String?) {
        _state.value = HomeUiState(resolving = true)
        val picked = pickedId?.let { stations.byId(it) }
        if (picked != null) {
            _state.value = HomeUiState(resolving = false, station = picked)
            loadBoard(picked.id)
            return
        }
        val here = location.current()
        val nearest = here?.let { stations.nearest(it.lat, it.lng) }
        _state.value = HomeUiState(
            resolving = false,
            station = nearest,
            distanceMetres = nearest?.distanceTo(here.lat, here.lng)?.roundToInt(),
        )
        if (nearest != null) loadBoard(nearest.id)
    }

    private suspend fun loadBoard(stationId: String) {
        val board = departures.loadBoardState(stationId, HOME_TRAIN_COUNT)
        _state.update { if (it.station?.id == stationId) it.copy(board = board) else it }
    }

    companion object {
        const val HOME_TRAIN_COUNT = 5
    }
}

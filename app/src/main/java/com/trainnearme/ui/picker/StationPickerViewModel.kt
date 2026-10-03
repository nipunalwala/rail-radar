package com.trainnearme.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.StationSelection
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.model.Station
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StationPickerViewModel @Inject constructor(
    stations: StationRepository,
    private val selection: StationSelection,
) : ViewModel() {

    private val all = MutableStateFlow<List<Station>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Station>> =
        combine(all, _query) { stations, query -> stations.matching(query) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { all.value = stations.all().sortedBy { it.name } }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun pick(station: Station) = selection.pick(station.id)
}

/** Stations whose name contains [query], with names that start with it first. */
internal fun List<Station>.matching(query: String): List<Station> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) }
        .sortedBy { !it.name.startsWith(q, ignoreCase = true) }
}

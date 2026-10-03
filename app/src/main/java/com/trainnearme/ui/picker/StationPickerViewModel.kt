package com.trainnearme.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.data.station.StationRepository
import com.trainnearme.core.domain.onLines
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
    private val settings: SettingsRepository,
) : ViewModel() {

    private val all = MutableStateFlow<List<Station>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Stations on the monitored lines that match the search text. */
    val results: StateFlow<List<Station>> =
        combine(all, settings.settings, _query) { stations, settings, query ->
            stations.onLines(settings.lines).matching(query)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { all.value = stations.all().sortedBy { it.name } }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun pick(station: Station) {
        viewModelScope.launch { settings.update { it.copy(pickedStationId = station.id) } }
    }
}

/** Stations whose name contains [query], with names that start with it first. */
internal fun List<Station>.matching(query: String): List<Station> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) }
        .sortedBy { !it.name.startsWith(q, ignoreCase = true) }
}

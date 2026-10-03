package com.trainnearme.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The station the user picked by hand, or null to follow their location.
 * Held in memory only; it resets when the app process ends.
 */
@Singleton
class StationSelection @Inject constructor() {
    private val _stationId = MutableStateFlow<String?>(null)
    val stationId: StateFlow<String?> = _stationId.asStateFlow()

    fun pick(stationId: String) {
        _stationId.value = stationId
    }

    fun clear() {
        _stationId.value = null
    }
}

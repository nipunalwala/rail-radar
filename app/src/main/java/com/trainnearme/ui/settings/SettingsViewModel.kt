package com.trainnearme.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    /** Null until the stored settings have been read. */
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAlertsEnabled(enabled: Boolean) = update { it.copy(alertsEnabled = enabled) }

    fun setRadius(metres: Int) = update { it.copy(radiusMetres = metres) }

    fun setTrainCount(count: Int) = update { it.copy(trainCount = count) }

    fun setSound(enabled: Boolean) = update { it.copy(sound = enabled) }

    fun setVibration(enabled: Boolean) = update { it.copy(vibration = enabled) }

    fun setHighAccuracy(enabled: Boolean) = update { it.copy(highAccuracy = enabled) }

    /** The last monitored line cannot be switched off. */
    fun setLineMonitored(line: Line, monitored: Boolean) = update {
        val lines = if (monitored) it.lines + line else it.lines - line
        if (lines.isEmpty()) it else it.copy(lines = lines)
    }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }
}

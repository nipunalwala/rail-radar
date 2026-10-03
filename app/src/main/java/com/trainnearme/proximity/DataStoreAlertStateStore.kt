package com.trainnearme.proximity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.trainnearme.core.domain.StationAlertState
import kotlinx.coroutines.flow.first
import java.time.Instant

class DataStoreAlertStateStore(
    private val dataStore: DataStore<Preferences>,
) : AlertStateStore {

    override suspend fun get(stationId: String): StationAlertState {
        val prefs = dataStore.data.first()
        return StationAlertState(
            inside = prefs[insideKey(stationId)] ?: false,
            lastExitAt = prefs[exitKey(stationId)]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun set(stationId: String, state: StationAlertState) {
        dataStore.edit { prefs ->
            if (state.inside) prefs[insideKey(stationId)] = true else prefs.remove(insideKey(stationId))
            val exit = state.lastExitAt
            if (exit != null) prefs[exitKey(stationId)] = exit.toEpochMilli() else prefs.remove(exitKey(stationId))
        }
    }

    override suspend fun all(): Map<String, StationAlertState> {
        val prefs = dataStore.data.first()
        return prefs.asMap().keys
            .map { it.name.substringAfter(':') }
            .distinct()
            .associateWith { id ->
                StationAlertState(
                    inside = prefs[insideKey(id)] ?: false,
                    lastExitAt = prefs[exitKey(id)]?.let(Instant::ofEpochMilli),
                )
            }
    }

    private fun insideKey(stationId: String) = booleanPreferencesKey(INSIDE + stationId)
    private fun exitKey(stationId: String) = longPreferencesKey(EXIT + stationId)

    private companion object {
        const val INSIDE = "inside:"
        const val EXIT = "exit:"
    }
}

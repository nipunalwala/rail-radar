package com.trainnearme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<Settings>

    /** Applies [transform] to the stored settings; the result is sanitised before it is saved. */
    suspend fun update(transform: (Settings) -> Settings)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings()).sanitised()
            prefs[ALERTS_ENABLED] = updated.alertsEnabled
            prefs[RADIUS_METRES] = updated.radiusMetres
            prefs[TRAIN_COUNT] = updated.trainCount
            prefs[SOUND] = updated.sound
            prefs[VIBRATION] = updated.vibration
            prefs[LINES] = updated.lines.map { it.name }.toSet()
            prefs[ONBOARDING_DONE] = updated.onboardingDone
            prefs[HIGH_ACCURACY] = updated.highAccuracy
            if (updated.pickedStationId != null) {
                prefs[PICKED_STATION] = updated.pickedStationId
            } else {
                prefs.remove(PICKED_STATION)
            }
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            alertsEnabled = this[ALERTS_ENABLED] ?: defaults.alertsEnabled,
            radiusMetres = this[RADIUS_METRES] ?: defaults.radiusMetres,
            trainCount = this[TRAIN_COUNT] ?: defaults.trainCount,
            sound = this[SOUND] ?: defaults.sound,
            vibration = this[VIBRATION] ?: defaults.vibration,
            highAccuracy = this[HIGH_ACCURACY] ?: defaults.highAccuracy,
            lines = this[LINES]
                ?.mapNotNull { name -> Line.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: defaults.lines,
            pickedStationId = this[PICKED_STATION],
            onboardingDone = this[ONBOARDING_DONE] ?: defaults.onboardingDone,
        ).sanitised()
    }

    private companion object {
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val RADIUS_METRES = intPreferencesKey("radius_metres")
        val TRAIN_COUNT = intPreferencesKey("train_count")
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val LINES = stringSetPreferencesKey("lines")
        val PICKED_STATION = stringPreferencesKey("picked_station")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val HIGH_ACCURACY = booleanPreferencesKey("high_accuracy")
    }
}

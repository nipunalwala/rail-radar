package com.trainnearme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trainnearme.core.domain.MUMBAI_ZONE
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.ScheduledDeparture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** The provider refused the request because the quota is used up (HTTP 429). */
class ProviderRateLimitedException : IOException("Train data request limit reached")

/** The provider rejected the API key (HTTP 401 or 403). */
class ProviderUnauthorizedException : IOException("Train data key was rejected")

data class ProviderHealth(
    /** No requests are sent before this time. */
    val blockedUntil: Instant? = null,
    /** The last request was rejected for its key; cleared by the next success. */
    val keyRejected: Boolean = false,
    /** The month [requestCount] belongs to, as "2026-10". */
    val requestMonth: String = "",
    val requestCount: Int = 0,
) {
    fun isBlocked(now: Instant): Boolean = blockedUntil != null && now < blockedUntil
}

interface ProviderHealthStore {
    val health: Flow<ProviderHealth>
    suspend fun update(transform: (ProviderHealth) -> ProviderHealth)
}

/**
 * Wraps a provider to protect the request quota: counts every request, stops
 * sending them until the next day once the provider says the limit is
 * reached, and records a rejected key so the app can say so once.
 */
class GuardedProvider(
    private val delegate: TrainDataProvider,
    private val store: ProviderHealthStore,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = MUMBAI_ZONE,
) : TrainDataProvider {

    override suspend fun timetable(stationCode: String): List<ScheduledDeparture> =
        guarded { delegate.timetable(stationCode) }

    override suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure> =
        guarded { delegate.liveBoard(stationCode, hoursAhead) }

    private suspend fun <T> guarded(request: suspend () -> T): T {
        val at = now()
        val before = store.health.first()
        if (before.isBlocked(at)) throw ProviderRateLimitedException()

        val month = YearMonth.from(at.atZone(zone)).toString()
        store.update {
            it.copy(
                blockedUntil = null,
                requestMonth = month,
                requestCount = if (it.requestMonth == month) it.requestCount + 1 else 1,
            )
        }
        return try {
            request().also {
                if (before.keyRejected) store.update { it.copy(keyRejected = false) }
            }
        } catch (e: ProviderRateLimitedException) {
            val tomorrow = at.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            store.update { it.copy(blockedUntil = tomorrow) }
            throw e
        } catch (e: ProviderUnauthorizedException) {
            store.update { it.copy(keyRejected = true) }
            throw e
        }
    }
}

class DataStoreProviderHealthStore(
    private val dataStore: DataStore<Preferences>,
) : ProviderHealthStore {

    override val health: Flow<ProviderHealth> = dataStore.data.map { it.toHealth() }

    override suspend fun update(transform: (ProviderHealth) -> ProviderHealth) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toHealth())
            val blockedUntil = updated.blockedUntil
            if (blockedUntil != null) prefs[BLOCKED_UNTIL] = blockedUntil.toEpochMilli() else prefs.remove(BLOCKED_UNTIL)
            prefs[KEY_REJECTED] = updated.keyRejected
            prefs[REQUEST_MONTH] = updated.requestMonth
            prefs[REQUEST_COUNT] = updated.requestCount
        }
    }

    private fun Preferences.toHealth() = ProviderHealth(
        blockedUntil = this[BLOCKED_UNTIL]?.let(Instant::ofEpochMilli),
        keyRejected = this[KEY_REJECTED] ?: false,
        requestMonth = this[REQUEST_MONTH].orEmpty(),
        requestCount = this[REQUEST_COUNT] ?: 0,
    )

    private companion object {
        val BLOCKED_UNTIL = longPreferencesKey("blocked_until")
        val KEY_REJECTED = booleanPreferencesKey("key_rejected")
        val REQUEST_MONTH = stringPreferencesKey("request_month")
        val REQUEST_COUNT = intPreferencesKey("request_count")
    }
}

package com.trainnearme.proximity

import android.content.Context
import android.util.Log
import com.trainnearme.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short record of geofence and alert events for testing in the field.
 * Kept in debug builds only: station entries are a form of location history,
 * which release builds must not store.
 */
@Singleton
class EventLog @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "events.log")

    @Synchronized
    fun record(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, message)
        val line = "${LocalDateTime.now().format(STAMP)}  $message"
        file.writeText((recent() + line).takeLast(MAX_LINES).joinToString("\n"))
    }

    @Synchronized
    fun recent(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val TAG = "TrainNearMe"
        const val MAX_LINES = 200
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss")
    }
}

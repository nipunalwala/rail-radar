package com.trainnearme.proximity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trainnearme.MainActivity
import com.trainnearme.R
import com.trainnearme.core.domain.formatDelay
import com.trainnearme.core.domain.AlertLine
import com.trainnearme.core.domain.alertLines
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.DepartureBoard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: EventLog,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        createChannels()
    }

    /** Shows the next trains for a station, replacing any alert already showing. */
    fun showAlert(board: DepartureBoard, sound: Boolean, vibration: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            log.record("alert for ${board.station.id} not shown: notifications are disabled")
            return
        }
        val lines = alertLines(board).map(::render)
        val summary = when {
            board.source == BoardSource.UNAVAILABLE -> context.getString(R.string.alert_unavailable)
            lines.isEmpty() -> context.getString(R.string.alert_no_trains)
            else -> lines.first()
        }
        val style = NotificationCompat.InboxStyle().also { style ->
            lines.forEach(style::addLine)
            if (board.source == BoardSource.SCHEDULED) {
                style.setSummaryText(context.getString(R.string.alert_scheduled_only))
            }
        }
        val notification = NotificationCompat.Builder(context, alertChannel(sound, vibration))
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(context.getString(R.string.alert_title, board.station.name))
            .setContentText(summary)
            .setStyle(if (lines.isEmpty()) null else style)
            .setWhen(board.asOf.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openStation(board.station.id))
            .addExtras(android.os.Bundle().apply { putString(EXTRA_STATION_ID, board.station.id) })
            .build()
        manager.notify(ALERT_ID, notification)
        log.record("notification shown for ${board.station.id} (${board.source}, ${lines.size} trains)")
    }

    /** Removes the alert if it is the one for [stationId]. */
    fun cancelAlert(stationId: String) {
        val showing = manager.activeNotifications.firstOrNull { it.id == ALERT_ID }
        if (showing?.notification?.extras?.getString(EXTRA_STATION_ID) == stationId) {
            manager.cancel(ALERT_ID)
        }
    }

    /** Shown only on old Android versions, where an expedited worker runs as a foreground service. */
    fun workingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(context.getString(R.string.alert_working))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /** The permanent notification Android requires while high accuracy mode runs. */
    fun highAccuracyNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(context.getString(R.string.high_accuracy_title))
            .setContentText(context.getString(R.string.high_accuracy_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun render(line: AlertLine): String {
        val time = if (line.minutes == 0L) {
            context.getString(R.string.board_now)
        } else {
            context.getString(R.string.board_minutes, line.minutes)
        }
        val delay = line.delayMinutes
        val details = listOfNotNull(
            line.platform?.let { context.getString(R.string.board_platform, it) },
            when {
                delay == null -> null
                delay > 0 -> context.getString(R.string.board_late, formatDelay(delay))
                else -> context.getString(R.string.board_on_time)
            },
        )
        return (listOf(context.getString(R.string.alert_line, line.destination, time)) + details)
            .joinToString("  ·  ")
    }

    private fun openStation(stationId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            stationId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_STATION_ID, stationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // A channel's sound and vibration cannot be changed once it exists, so
    // there is one channel for each combination of the two settings.
    private fun alertChannel(sound: Boolean, vibration: Boolean): String = when {
        sound && vibration -> CHANNEL_SOUND_VIBRATE
        sound -> CHANNEL_SOUND
        vibration -> CHANNEL_VIBRATE
        else -> CHANNEL_SILENT
    }

    private fun createChannels() {
        fun alerts(id: String, name: Int, sound: Boolean, vibration: Boolean) =
            NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(vibration)
                if (sound) {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                    )
                } else {
                    setSound(null, null)
                }
            }
        manager.createNotificationChannels(
            listOf(
                alerts(CHANNEL_SOUND_VIBRATE, R.string.channel_alerts, sound = true, vibration = true),
                alerts(CHANNEL_SOUND, R.string.channel_alerts_sound, sound = true, vibration = false),
                alerts(CHANNEL_VIBRATE, R.string.channel_alerts_vibrate, sound = false, vibration = true),
                alerts(CHANNEL_SILENT, R.string.channel_alerts_silent, sound = false, vibration = false),
                NotificationChannel(
                    CHANNEL_WORKING,
                    context.getString(R.string.channel_working),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    companion object {
        const val EXTRA_STATION_ID = "stationId"
        const val WORKING_ID = 2
        const val HIGH_ACCURACY_ID = 3
        private const val ALERT_ID = 1
        private const val CHANNEL_SOUND_VIBRATE = "alerts_sound_vibrate"
        private const val CHANNEL_SOUND = "alerts_sound"
        private const val CHANNEL_VIBRATE = "alerts_vibrate"
        private const val CHANNEL_SILENT = "alerts_silent"
        private const val CHANNEL_WORKING = "working"
    }
}

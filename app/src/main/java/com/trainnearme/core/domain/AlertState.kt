package com.trainnearme.core.domain

import com.trainnearme.core.model.DepartureBoard
import java.time.Duration
import java.time.Instant

/** After leaving a station, coming straight back does not alert again for this long. */
val ALERT_COOLDOWN: Duration = Duration.ofMinutes(10)

/** Whether the user is inside a station's radius, and when they last left it. */
data class StationAlertState(
    val inside: Boolean = false,
    val lastExitAt: Instant? = null,
)

data class EnterResult(val state: StationAlertState, val alert: Boolean)

/**
 * Entering alerts once. A repeated enter while inside, or a re-entry within
 * [cooldown] of leaving (GPS wandering across the boundary), does not.
 */
fun StationAlertState.onEnter(now: Instant, cooldown: Duration = ALERT_COOLDOWN): EnterResult {
    if (inside) return EnterResult(this, alert = false)
    val justLeft = lastExitAt != null && Duration.between(lastExitAt, now) < cooldown
    return EnterResult(copy(inside = true), alert = !justLeft)
}

fun StationAlertState.onExit(now: Instant): StationAlertState =
    if (inside) StationAlertState(inside = false, lastExitAt = now) else this

/** One train in an alert notification, ready to be put into words. */
data class AlertLine(
    val destination: String,
    val minutes: Long,
    val platform: String?,
    /** Null when there is no live data for the train. */
    val delayMinutes: Int?,
)

fun alertLines(board: DepartureBoard): List<AlertLine> =
    board.departures.map { departure ->
        AlertLine(
            destination = departure.destinationName,
            minutes = Duration.between(board.asOf, departure.effectiveTime(board.asOf)).toMinutes().coerceAtLeast(0),
            platform = departure.platform,
            delayMinutes = departure.delayMinutes.takeIf { departure.isLive },
        )
    }

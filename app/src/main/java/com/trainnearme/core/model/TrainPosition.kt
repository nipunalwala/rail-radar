package com.trainnearme.core.model

import java.time.Instant

enum class StopState { PASSED, CURRENT, AHEAD }

/** One stop on a train's run, with what actually happened there when known. */
data class TrainStop(
    val code: String,
    val name: String,
    /** When the train should leave this stop, or reach it if it is the last one. */
    val scheduled: Instant?,
    /** When it actually did. Null when it has not happened or is not known. */
    val actual: Instant?,
    val delayMinutes: Int?,
    val platform: String?,
    val state: StopState,
)

/** Where one train is on its run right now. */
data class TrainPosition(
    val trainNumber: String,
    val trainName: String,
    /** The train has reached its last stop. */
    val finished: Boolean,
    val isLive: Boolean,
    val updatedAt: Instant?,
    val delayMinutes: Int?,
    /** The station the train is at, or the last one it passed. Blank when unknown. */
    val currentCode: String,
    val currentName: String,
    /** True when the train is standing at [currentName], false when it has left it. */
    val atCurrent: Boolean,
    val nextCode: String,
    val nextName: String,
    val stops: List<TrainStop>,
)

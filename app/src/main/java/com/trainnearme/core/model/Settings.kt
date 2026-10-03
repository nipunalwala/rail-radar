package com.trainnearme.core.model

data class Settings(
    val alertsEnabled: Boolean = true,
    val radiusMetres: Int = DEFAULT_RADIUS_METRES,
    val trainCount: Int = DEFAULT_TRAIN_COUNT,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val lines: Set<Line> = Line.entries.toSet(),
    /** Station chosen by hand on the home screen, or null to follow location. */
    val pickedStationId: String? = null,
) {
    /** Brings out-of-range values back into range; at least one line stays monitored. */
    fun sanitised(): Settings = copy(
        radiusMetres = radiusMetres.coerceIn(RADIUS_RANGE),
        trainCount = trainCount.coerceIn(TRAIN_COUNT_RANGE),
        lines = lines.ifEmpty { Line.entries.toSet() },
    )

    companion object {
        const val DEFAULT_RADIUS_METRES = 500
        const val DEFAULT_TRAIN_COUNT = 5
        const val RADIUS_STEP_METRES = 100
        val RADIUS_RANGE = 200..2000
        val TRAIN_COUNT_RANGE = 1..10
    }
}

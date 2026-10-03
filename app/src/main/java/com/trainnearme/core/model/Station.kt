package com.trainnearme.core.model

enum class Line { WESTERN, CENTRAL, HARBOUR }

/**
 * A suburban station. [providerCodes] has more than one entry at interchanges
 * where each railway uses its own code (Dadar: DDR on Western, DR on Central).
 */
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val lines: Set<Line>,
    val providerCodes: List<String>,
)

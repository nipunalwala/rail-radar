package com.trainnearme.core.model

enum class Line { WESTERN, CENTRAL, HARBOUR }

/**
 * A suburban station. [codeLines] maps each provider code to the lines it
 * serves. It has more than one entry at interchanges where each railway uses
 * its own code (Dadar: DDR on Western, DR on Central).
 */
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val lines: Set<Line>,
    val codeLines: Map<String, Set<Line>>,
) {
    val providerCodes: List<String> get() = codeLines.keys.toList()

    /**
     * Codes that serve any of [lines]. A code shared by two lines cannot be
     * split, so it is returned when either line is wanted.
     */
    fun codesFor(lines: Set<Line>): List<String> =
        codeLines.filterValues { served -> served.any { it in lines } }.keys.toList()
}

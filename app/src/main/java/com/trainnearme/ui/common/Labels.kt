package com.trainnearme.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.trainnearme.R
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import java.util.Locale

@Composable
fun Line.label(): String = stringResource(
    when (this) {
        Line.WESTERN -> R.string.line_western
        Line.CENTRAL -> R.string.line_central
        Line.HARBOUR -> R.string.line_harbour
    },
)

@Composable
fun Station.linesLabel(): String {
    val labels = Line.entries.filter { it in lines }.map { it.label() }
    return labels.joinToString("  ·  ")
}

@Composable
fun distanceLabel(metres: Int): String =
    if (metres < 1000) {
        stringResource(R.string.distance_metres, metres)
    } else {
        stringResource(R.string.distance_kilometres, String.format(Locale.US, "%.1f", metres / 1000.0))
    }

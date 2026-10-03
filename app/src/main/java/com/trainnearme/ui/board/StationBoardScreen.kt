package com.trainnearme.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.core.domain.effectiveTime
import com.trainnearme.core.model.Departure
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBoardScreen(viewModel: StationBoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.stationName) },
                actions = { TextButton(onClick = viewModel::refresh) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = state) {
                BoardUiState.Loading -> CircularProgressIndicator()
                is BoardUiState.Failed -> Text(s.message, Modifier.padding(24.dp))
                is BoardUiState.Loaded ->
                    if (s.departures.isEmpty()) {
                        Text("No upcoming local trains")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.departures, key = { it.trainNumber }) { departure ->
                                DepartureRow(departure, s.asOf)
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, now: Instant) {
    val minutes = Duration.between(now, departure.effectiveTime(now)).toMinutes().coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(departure.destinationName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    departure.platform?.let { "PF $it" },
                    delayLabel(departure),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (minutes == 0L) "Now" else "$minutes min",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun delayLabel(departure: Departure): String {
    val delay = departure.delayMinutes
    return when {
        !departure.isLive || delay == null -> "Scheduled"
        delay > 0 -> "+$delay late"
        else -> "On time"
    }
}

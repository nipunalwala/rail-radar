package com.trainnearme.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trainnearme.R
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.domain.MUMBAI_ZONE
import com.trainnearme.core.domain.effectiveTime
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureBoard
import com.trainnearme.core.model.Line
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

sealed interface BoardUiState {
    data object Loading : BoardUiState
    data class Loaded(val board: DepartureBoard) : BoardUiState
    data object Failed : BoardUiState
}

suspend fun DepartureRepository.loadBoardState(
    stationId: String,
    count: Int,
    lines: Set<Line>,
): BoardUiState =
    try {
        nextDepartures(stationId, count, lines)?.let { BoardUiState.Loaded(it) } ?: BoardUiState.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BoardUiState.Failed
    }

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Rows for a departure board, for use inside a LazyColumn. */
fun LazyListScope.boardItems(state: BoardUiState) {
    when (state) {
        BoardUiState.Loading -> item(key = "board-loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        BoardUiState.Failed -> item(key = "board-failed") { BoardMessage(stringResource(R.string.board_failed)) }
        is BoardUiState.Loaded -> {
            val board = state.board
            when {
                board.source == BoardSource.UNAVAILABLE ->
                    item(key = "board-unavailable") { BoardMessage(stringResource(R.string.board_unavailable)) }
                board.departures.isEmpty() ->
                    item(key = "board-empty") { BoardMessage(stringResource(R.string.board_empty)) }
                else -> {
                    if (board.source == BoardSource.SCHEDULED) {
                        item(key = "board-scheduled-only") {
                            Text(
                                stringResource(R.string.board_scheduled_only),
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(board.departures, key = { "train-${it.trainNumber}" }) { departure ->
                        DepartureRow(departure, board.asOf)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardMessage(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DepartureRow(departure: Departure, now: Instant) {
    val leavesAt = departure.effectiveTime(now)
    val minutes = Duration.between(now, leavesAt).toMinutes().coerceAtLeast(0)
    val detail = listOfNotNull(
        departure.platform?.let { stringResource(R.string.board_platform, it) },
        delayLabel(departure),
    ).joinToString("  ·  ")

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(departure.destinationName, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (minutes == 0L) stringResource(R.string.board_now) else stringResource(R.string.board_minutes, minutes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                CLOCK.format(leavesAt.atZone(MUMBAI_ZONE)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun delayLabel(departure: Departure): String {
    val delay = departure.delayMinutes
    return when {
        !departure.isLive || delay == null -> stringResource(R.string.board_scheduled)
        delay > 0 -> stringResource(R.string.board_late, delay)
        else -> stringResource(R.string.board_on_time)
    }
}

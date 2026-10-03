package com.trainnearme.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trainnearme.R
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.domain.MUMBAI_ZONE
import com.trainnearme.core.domain.effectiveTime
import com.trainnearme.core.model.BoardSource
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.DepartureBoard
import com.trainnearme.core.model.Line
import com.trainnearme.ui.theme.statusColors
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

// Departures this close are picked out in the primary colour.
private const val SOON_MINUTES = 2L

// A delay of this many minutes or more is shown as late rather than slightly late.
private const val LATE_MINUTES = 5

private const val PLACEHOLDER_ROWS = 3

/** Rows for a departure board, for use inside a LazyColumn. */
fun LazyListScope.boardItems(state: BoardUiState) {
    when (state) {
        BoardUiState.Loading -> items(PLACEHOLDER_ROWS, key = { "board-loading-$it" }) { PlaceholderRow() }
        BoardUiState.Failed -> item(key = "board-failed") {
            BoardMessage(Icons.Default.Warning, stringResource(R.string.board_failed))
        }
        is BoardUiState.Loaded -> {
            val board = state.board
            when {
                board.source == BoardSource.UNAVAILABLE -> item(key = "board-unavailable") {
                    BoardMessage(Icons.Default.Warning, stringResource(R.string.board_unavailable))
                }
                board.departures.isEmpty() -> item(key = "board-empty") {
                    BoardMessage(Icons.Default.Info, stringResource(R.string.board_empty))
                }
                else -> {
                    item(key = "board-source") { SourceLine(board.source, board.asOf) }
                    items(board.departures, key = { "train-${it.trainNumber}" }) { departure ->
                        DepartureRow(departure, board.asOf)
                    }
                }
            }
        }
    }
}

/** Says whether the times below are live or from the timetable, and as of when. */
@Composable
private fun SourceLine(source: BoardSource, asOf: Instant) {
    val live = source == BoardSource.LIVE
    val color = if (live) MaterialTheme.statusColors.onTime else MaterialTheme.statusColors.slightlyLate
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            if (live) {
                stringResource(R.string.board_live_as_of, CLOCK.format(asOf.atZone(MUMBAI_ZONE)))
            } else {
                stringResource(R.string.board_scheduled_only)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (live) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
    }
}

@Composable
private fun BoardMessage(icon: ImageVector, text: String) {
    EmptyState(icon = icon, title = text)
}

@Composable
private fun BoardCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

/** Stands in for a departure while the board loads, so the list does not jump. */
@Composable
private fun PlaceholderRow() {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val shape = MaterialTheme.shapes.extraSmall
    BoardCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(140.dp).height(16.dp).background(block, shape))
                Box(Modifier.width(90.dp).height(12.dp).background(block, shape))
            }
            Box(Modifier.width(48.dp).height(24.dp).background(block, shape))
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, now: Instant) {
    val leavesAt = departure.effectiveTime(now)
    val minutes = Duration.between(now, leavesAt).toMinutes().coerceAtLeast(0)
    val (status, statusColor) = delayLabel(departure)

    BoardCard {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    departure.destinationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    departure.platform?.let { PlatformBadge(stringResource(R.string.board_platform, it)) }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (minutes == 0L) stringResource(R.string.board_now) else stringResource(R.string.board_minutes, minutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (minutes <= SOON_MINUTES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    CLOCK.format(leavesAt.atZone(MUMBAI_ZONE)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun delayLabel(departure: Departure): Pair<String, Color> {
    val delay = departure.delayMinutes
    return when {
        !departure.isLive || delay == null ->
            stringResource(R.string.board_scheduled) to MaterialTheme.colorScheme.onSurfaceVariant
        delay >= LATE_MINUTES -> stringResource(R.string.board_late, delay) to MaterialTheme.statusColors.late
        delay > 0 -> stringResource(R.string.board_late, delay) to MaterialTheme.statusColors.slightlyLate
        else -> stringResource(R.string.board_on_time) to MaterialTheme.statusColors.onTime
    }
}

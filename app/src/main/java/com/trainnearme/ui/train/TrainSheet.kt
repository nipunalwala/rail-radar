package com.trainnearme.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.R
import com.trainnearme.core.domain.MUMBAI_ZONE
import com.trainnearme.core.domain.formatDelay
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.Station
import com.trainnearme.core.model.StopState
import com.trainnearme.core.model.TrainPosition
import com.trainnearme.core.model.TrainStop
import com.trainnearme.ui.common.EmptyState
import com.trainnearme.ui.common.NoticeCard
import com.trainnearme.ui.common.PlatformBadge
import com.trainnearme.ui.common.ScreenPadding
import com.trainnearme.ui.common.SectionHeader
import com.trainnearme.ui.theme.statusColors
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

// A delay of this many minutes or more is shown as late rather than slightly late.
private const val LATE_MINUTES = 5

/** Shows the tapped train's position and stops. Draws nothing while no train is selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainSheet(viewModel: TrainSheetViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return

    ModalBottomSheet(onDismissRequest = viewModel::close) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") { Header(current.departure) }
            when (val position = current.position) {
                PositionUiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                PositionUiState.Failed -> item(key = "failed") {
                    EmptyState(icon = Icons.Default.Warning, title = stringResource(R.string.train_position_failed)) {
                        FilledTonalButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_try_again)) }
                    }
                }
                is PositionUiState.Loaded -> positionItems(position.position, current.station)
            }
        }
    }
}

@Composable
private fun Header(departure: Departure) {
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Text(
            if (departure.originName.isNotBlank()) {
                stringResource(R.string.board_route, departure.originName, departure.destinationName)
            } else {
                departure.destinationName
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.train_number, departure.trainNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.positionItems(position: TrainPosition, station: Station) {
    item(key = "where") { Where(position) }
    val here = position.stops.firstOrNull { it.code in station.providerCodes }
    if (here?.state == StopState.PASSED) {
        item(key = "already-left") {
            NoticeCard(
                text = stringResource(R.string.train_already_left, station.name),
                icon = Icons.Default.Warning,
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
            )
        }
    }
    if (position.stops.isNotEmpty()) {
        item(key = "stops-title") { SectionHeader(stringResource(R.string.train_stops)) }
        itemsIndexed(position.stops, key = { index, stop -> "stop-$index-${stop.code}" }) { _, stop ->
            StopRow(stop, isHere = stop.code in station.providerCodes)
        }
    }
}

/** The headline: where the train is now, how late it is and how fresh that is. */
@Composable
private fun Where(position: TrainPosition) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp)
            .background(colors.primaryContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Text(
            whereText(position),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
        )
        val details = listOfNotNull(
            position.delayMinutes?.takeIf { position.isLive }?.let {
                if (it > 0) stringResource(R.string.board_late, formatDelay(it)) else stringResource(R.string.board_on_time)
            },
            position.updatedAt?.let { stringResource(R.string.train_updated, CLOCK.format(it.atZone(MUMBAI_ZONE))) },
        )
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  ·  "),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun whereText(position: TrainPosition): String = when {
    position.currentName.isBlank() || !position.isLive -> stringResource(R.string.train_no_live)
    position.finished -> stringResource(R.string.train_finished, position.currentName)
    position.atCurrent -> stringResource(R.string.train_at, position.currentName)
    position.nextName.isNotBlank() -> stringResource(R.string.train_left_next, position.currentName, position.nextName)
    else -> stringResource(R.string.train_left, position.currentName)
}

@Composable
private fun StopRow(stop: TrainStop, isHere: Boolean) {
    val colors = MaterialTheme.colorScheme
    val passed = stop.state == StopState.PASSED
    val dot = when (stop.state) {
        StopState.CURRENT -> colors.primary
        StopState.PASSED -> colors.outlineVariant
        StopState.AHEAD -> colors.outline
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isHere) colors.surfaceContainerHighest else Color.Transparent)
            .padding(horizontal = ScreenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (stop.state == StopState.CURRENT) 14.dp else 10.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stop.name,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (stop.state == StopState.CURRENT || isHere) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (passed) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                stop.platform?.let { PlatformBadge(stringResource(R.string.board_platform, it)) }
            }
            if (isHere) {
                Text(
                    stringResource(R.string.train_your_station),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
            }
        }
        StopTimes(stop)
    }
}

/** The scheduled time, and beside it the actual time when the two differ. */
@Composable
private fun StopTimes(stop: TrainStop) {
    val colors = MaterialTheme.colorScheme
    val scheduled = stop.scheduled?.let { CLOCK.format(it.atZone(MUMBAI_ZONE)) }
    val actual = stop.actual?.let { CLOCK.format(it.atZone(MUMBAI_ZONE)) }
    val delay = stop.delayMinutes ?: 0
    val actualColor = when {
        delay >= LATE_MINUTES -> MaterialTheme.statusColors.late
        delay > 0 -> MaterialTheme.statusColors.slightlyLate
        else -> MaterialTheme.statusColors.onTime
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scheduled != null) {
            val replaced = actual != null && actual != scheduled
            Text(
                scheduled,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textDecoration = if (replaced) TextDecoration.LineThrough else null,
            )
        }
        if (actual != null) {
            Text(actual, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = actualColor)
        }
    }
}

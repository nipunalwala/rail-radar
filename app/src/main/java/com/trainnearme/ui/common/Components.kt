package com.trainnearme.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station
import com.trainnearme.ui.theme.statusColors

/** The space between the screen edge and content, used by every screen. */
val ScreenPadding = 16.dp

/** A dot in the line's colour. Always shown next to the line's name. */
@Composable
fun LineDot(line: Line, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(MaterialTheme.statusColors.line(line), CircleShape))
}

@Composable
fun LineChip(line: Line, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Surface(modifier, shape = CircleShape, color = container) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LineDot(line)
            Text(line.label(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The lines a station is on, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChips(
    station: Station,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Line.entries.filter { it in station.lines }.forEach { LineChip(it, container = container) }
    }
}

@Composable
fun PlatformBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Groups related rows on one tinted, rounded surface. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

enum class NoticeTone { INFO, PROBLEM }

/** A message the user should not miss, with an optional way to act on it. */
@Composable
fun NoticeCard(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.PROBLEM,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        NoticeTone.INFO -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        NoticeTone.PROBLEM -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Text(
                text,
                Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Fills the space where content would be when there is none to show. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            title,
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Text(
                text,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.padding(top = 20.dp)) { action() }
    }
}

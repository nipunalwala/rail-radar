package com.trainnearme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.R
import com.trainnearme.core.model.Station
import com.trainnearme.core.permissions.AlertStatus
import com.trainnearme.ui.common.BoardPullToRefresh
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.common.EmptyState
import com.trainnearme.ui.common.LineChips
import com.trainnearme.ui.common.NoticeCard
import com.trainnearme.ui.common.NoticeTone
import com.trainnearme.ui.common.ScreenPadding
import com.trainnearme.ui.common.SectionHeader
import com.trainnearme.ui.common.boardItems
import com.trainnearme.ui.common.distanceLabel
import com.trainnearme.ui.theme.statusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStation: (String) -> Unit,
    onChangeStation: () -> Unit,
    onOpenSettings: () -> Unit,
    onFixAlerts: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val station = state.station

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_train),
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.app_name),
                            Modifier.padding(start = 10.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    if (station != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.resolving -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.home_finding_station),
                        Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            station == null -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.Place,
                    title = stringResource(R.string.home_no_station),
                    text = stringResource(R.string.home_no_station_hint),
                ) {
                    Button(onClick = onChangeStation) { Text(stringResource(R.string.action_choose_station)) }
                }
                state.alertStatus?.let { status ->
                    AlertStatusRow(status, onFixAlerts, Modifier.padding(horizontal = ScreenPadding))
                }
                Credit(Modifier.fillMaxWidth().padding(ScreenPadding))
            }
            else -> BoardPullToRefresh(
                board = state.board,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "station") {
                        StationHero(
                            station = station,
                            distanceMetres = state.distanceMetres,
                            onChangeStation = onChangeStation,
                            onUseNearest = viewModel::useNearest,
                        )
                    }
                    state.alertStatus?.let { status ->
                        item(key = "alert-status") {
                            AlertStatusRow(
                                status,
                                onFixAlerts,
                                Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
                            )
                        }
                    }
                    state.providerNotice?.let { notice ->
                        item(key = "provider-notice") {
                            NoticeCard(
                                text = stringResource(
                                    when (notice) {
                                        ProviderNotice.KEY_REJECTED -> R.string.notice_key_rejected
                                        ProviderNotice.LIMIT_REACHED -> R.string.notice_limit_reached
                                    },
                                ),
                                icon = Icons.Default.Warning,
                                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                            )
                        }
                    }
                    item(key = "next-trains") { SectionHeader(stringResource(R.string.home_next_trains)) }
                    boardItems(state.board)
                    if (state.board is BoardUiState.Loaded) {
                        item(key = "see-all") {
                            TextButton(
                                onClick = { onOpenStation(station.id) },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.action_see_all_trains))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    Modifier.size(ButtonDefaults.IconSize),
                                )
                            }
                        }
                    }
                    item(key = "credit") { Credit(Modifier.padding(ScreenPadding)) }
                }
            }
        }
    }
}

/** The station the board is for: how it was chosen, its name and lines, and how to change it. */
@Composable
private fun StationHero(
    station: Station,
    distanceMetres: Int?,
    onChangeStation: () -> Unit,
    onUseNearest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (distanceMetres != null) Icons.Default.LocationOn else Icons.Default.Place,
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
                Text(
                    if (distanceMetres != null) {
                        stringResource(R.string.home_nearest_station, distanceLabel(distanceMetres))
                    } else {
                        stringResource(R.string.home_chosen_station)
                    },
                    Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                station.name,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LineChips(station, Modifier.padding(top = 10.dp), container = colors.surface.copy(alpha = 0.6f))
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onChangeStation,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) { Text(stringResource(R.string.action_change_station)) }
                if (distanceMetres == null) {
                    TextButton(
                        onClick = onUseNearest,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                    ) { Text(stringResource(R.string.action_use_nearest)) }
                }
            }
        }
    }
}

/**
 * Says whether automatic alerts will fire: a quiet line when they will or are
 * switched off, a notice with a way to fix it when a permission is missing.
 */
@Composable
private fun AlertStatusRow(status: AlertStatus, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (status) {
            AlertStatus.ON -> R.string.alerts_on
            AlertStatus.OFF -> R.string.alerts_off
            AlertStatus.NEEDS_LOCATION -> R.string.alerts_need_location
            AlertStatus.NEEDS_BACKGROUND_LOCATION -> R.string.alerts_need_background
            AlertStatus.NEEDS_NOTIFICATIONS -> R.string.alerts_need_notifications
        },
    )
    when (status) {
        AlertStatus.ON, AlertStatus.OFF -> Row(
            modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                Modifier.size(18.dp),
                tint = if (status == AlertStatus.ON) {
                    MaterialTheme.statusColors.onTime
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text,
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> NoticeCard(
            text = text,
            icon = Icons.Default.Warning,
            modifier = modifier,
            tone = NoticeTone.PROBLEM,
            actionLabel = stringResource(R.string.action_fix),
            onAction = onFix,
        )
    }
}

@Composable
private fun Credit(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.credit_osm),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

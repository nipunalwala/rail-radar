package com.trainnearme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.core.permissions.AlertStatus
import com.trainnearme.R
import com.trainnearme.core.model.Station
import com.trainnearme.ui.common.BoardUiState
import com.trainnearme.ui.common.boardItems
import com.trainnearme.ui.common.distanceLabel
import com.trainnearme.ui.common.linesLabel

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
                title = { Text(stringResource(R.string.app_name)) },
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
            state.resolving -> Centered(Modifier.padding(padding)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.home_finding_station), Modifier.padding(top = 16.dp))
            }
            station == null -> Centered(Modifier.padding(padding)) {
                Text(
                    stringResource(R.string.home_no_station),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.home_no_station_hint),
                    Modifier.padding(top = 8.dp, bottom = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onChangeStation) { Text(stringResource(R.string.action_choose_station)) }
                state.alertStatus?.let { status ->
                    AlertStatusRow(status, onFixAlerts, Modifier.padding(top = 24.dp))
                }
                Credit(Modifier.padding(top = 32.dp))
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item(key = "station") {
                    StationHeader(
                        station = station,
                        distanceMetres = state.distanceMetres,
                        onChangeStation = onChangeStation,
                        onUseNearest = viewModel::useNearest,
                    )
                }
                state.alertStatus?.let { status ->
                    item(key = "alert-status") {
                        AlertStatusRow(status, onFixAlerts, Modifier.padding(horizontal = 16.dp))
                    }
                }
                item(key = "next-trains") {
                    Text(
                        stringResource(R.string.home_next_trains),
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                boardItems(state.board)
                if (state.board is BoardUiState.Loaded) {
                    item(key = "see-all") {
                        TextButton(
                            onClick = { onOpenStation(station.id) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) { Text(stringResource(R.string.action_see_all_trains)) }
                    }
                }
                item(key = "credit") { Credit(Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun StationHeader(
    station: Station,
    distanceMetres: Int?,
    onChangeStation: () -> Unit,
    onUseNearest: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            if (distanceMetres != null) {
                stringResource(R.string.home_nearest_station, distanceLabel(distanceMetres))
            } else {
                stringResource(R.string.home_chosen_station)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(station.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            station.linesLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onChangeStation) { Text(stringResource(R.string.action_change_station)) }
            if (distanceMetres == null) {
                TextButton(onClick = onUseNearest) { Text(stringResource(R.string.action_use_nearest)) }
            }
        }
    }
}

/** One line saying whether automatic alerts will fire, with a way to fix it when they will not. */
@Composable
private fun AlertStatusRow(status: AlertStatus, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val needsPermission = status != AlertStatus.ON && status != AlertStatus.OFF
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                when (status) {
                    AlertStatus.ON -> R.string.alerts_on
                    AlertStatus.OFF -> R.string.alerts_off
                    AlertStatus.NEEDS_LOCATION -> R.string.alerts_need_location
                    AlertStatus.NEEDS_BACKGROUND_LOCATION -> R.string.alerts_need_background
                    AlertStatus.NEEDS_NOTIFICATIONS -> R.string.alerts_need_notifications
                },
            ),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (needsPermission) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (needsPermission) {
            TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
        }
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

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

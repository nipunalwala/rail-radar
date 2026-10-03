package com.trainnearme.ui.diagnostics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trainnearme.R
import com.trainnearme.core.data.ProviderHealth
import com.trainnearme.core.data.ProviderHealthStore
import com.trainnearme.core.domain.MUMBAI_ZONE
import com.trainnearme.proximity.EventLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Debug-build screen for field testing: request count, quota state and proximity events. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    health: ProviderHealthStore,
    private val log: EventLog,
) : ViewModel() {

    val health: StateFlow<ProviderHealth> =
        health.health.stateIn(viewModelScope, SharingStarted.Eagerly, ProviderHealth())

    private val _events = MutableStateFlow(log.recent().asReversed())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    fun clearEvents() {
        log.clear()
        _events.value = emptyList()
    }
}

private val STAMP = DateTimeFormatter.ofPattern("dd MMM HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val blockedUntil = health.blockedUntil?.takeIf { health.isBlocked(Instant.now()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearEvents) { Text(stringResource(R.string.diagnostics_clear)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { Text(stringResource(R.string.diagnostics_requests, health.requestCount)) }
            item {
                Text(
                    if (blockedUntil != null) {
                        stringResource(R.string.diagnostics_blocked_until, STAMP.format(blockedUntil.atZone(MUMBAI_ZONE)))
                    } else {
                        stringResource(R.string.diagnostics_not_blocked)
                    },
                )
            }
            if (health.keyRejected) {
                item {
                    Text(stringResource(R.string.diagnostics_key_rejected), color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Text(
                    stringResource(R.string.diagnostics_events),
                    Modifier.padding(top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (events.isEmpty()) {
                item { Text(stringResource(R.string.diagnostics_no_events)) }
            }
            items(events) { event ->
                Text(
                    event,
                    Modifier.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

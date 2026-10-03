package com.trainnearme.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.BuildConfig
import com.trainnearme.R
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Settings
import com.trainnearme.ui.common.distanceLabel
import com.trainnearme.ui.common.label
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_section_alerts))
            SwitchRow(
                stringResource(R.string.settings_alerts),
                stringResource(R.string.settings_alerts_hint),
                current.alertsEnabled,
                viewModel::setAlertsEnabled,
            )
            SliderRow(
                title = stringResource(R.string.settings_radius),
                value = current.radiusMetres,
                valueLabel = { distanceLabel(it) },
                range = Settings.RADIUS_RANGE,
                step = Settings.RADIUS_STEP_METRES,
                onChange = viewModel::setRadius,
            )
            SwitchRow(stringResource(R.string.settings_sound), null, current.sound, viewModel::setSound)
            SwitchRow(stringResource(R.string.settings_vibration), null, current.vibration, viewModel::setVibration)
            SwitchRow(
                stringResource(R.string.settings_high_accuracy),
                stringResource(R.string.settings_high_accuracy_hint),
                current.highAccuracy,
                viewModel::setHighAccuracy,
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_trains))
            SliderRow(
                title = stringResource(R.string.settings_train_count),
                value = current.trainCount,
                valueLabel = { it.toString() },
                range = Settings.TRAIN_COUNT_RANGE,
                step = 1,
                onChange = viewModel::setTrainCount,
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_section_lines))
            Line.entries.forEach { line ->
                val checked = line in current.lines
                CheckboxRow(
                    title = line.label(),
                    checked = checked,
                    // The last monitored line cannot be switched off.
                    enabled = !(checked && current.lines.size == 1),
                    onChange = { viewModel.setLineMonitored(line, it) },
                )
            }
            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(8.dp)) {
                    Text(stringResource(R.string.diagnostics_title))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckboxRow(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    valueLabel: @Composable (Int) -> String,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    // The slider moves freely while dragged; the setting is saved on release.
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val snapped = ((dragging / step).roundToInt() * step).coerceIn(range)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel(snapped), style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(snapped) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
}

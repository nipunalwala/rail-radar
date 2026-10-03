package com.trainnearme.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.R
import com.trainnearme.ui.theme.statusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }
    // Permissions can also change in system settings while this screen is in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)

            PermissionCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.onboarding_notifications_title),
                reason = stringResource(R.string.onboarding_notifications_reason),
                granted = permissions.notifications,
                enabled = true,
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        single.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            PermissionCard(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.onboarding_location_title),
                reason = stringResource(R.string.onboarding_location_reason),
                granted = permissions.foregroundLocation,
                enabled = true,
                onAllow = {
                    multiple.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
            PermissionCard(
                icon = Icons.Default.Place,
                title = stringResource(R.string.onboarding_background_title),
                reason = stringResource(R.string.onboarding_background_reason),
                granted = permissions.backgroundLocation,
                // Android only offers "Allow all the time" once location is allowed at all.
                enabled = permissions.foregroundLocation,
                disabledHint = stringResource(R.string.onboarding_location_first),
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )

            Text(
                stringResource(R.string.onboarding_denied_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.onboarding_open_settings)) }

            Button(
                onClick = {
                    viewModel.finish()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (permissions.all) R.string.onboarding_done else R.string.onboarding_continue_without,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    reason: String,
    granted: Boolean,
    enabled: Boolean,
    onAllow: () -> Unit,
    disabledHint: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    reason,
                    Modifier.padding(top = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (granted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.statusColors.onTime,
                        )
                        Text(
                            stringResource(R.string.onboarding_allowed),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.statusColors.onTime,
                        )
                    }
                } else {
                    Button(onClick = onAllow, enabled = enabled) {
                        Text(stringResource(R.string.onboarding_allow))
                    }
                    if (!enabled && disabledHint != null) {
                        Text(
                            disabledHint,
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

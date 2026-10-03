package com.trainnearme.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.ui.home.HomeScreen
import com.trainnearme.ui.onboarding.OnboardingScreen
import com.trainnearme.ui.picker.StationPickerScreen
import com.trainnearme.ui.settings.SettingsScreen
import com.trainnearme.ui.station.StationDetailScreen
import com.trainnearme.ui.station.StationDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val HOME = "home"
private const val PICKER = "stations"
private const val STATION = "station"
private const val SETTINGS = "settings"
private const val ONBOARDING = "onboarding"

@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** Null until the stored settings have been read. */
    val onboardingDone: StateFlow<Boolean?> =
        settings.settings.map { it.onboardingDone }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsStateWithLifecycle()
    val done = onboardingDone ?: return
    // Decided once: finishing onboarding must not swap the graph under the user.
    val start = remember { if (done) HOME else ONBOARDING }

    val navController = rememberNavController()
    NavHost(navController, startDestination = start) {
        composable(ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(HOME) { popUpTo(ONBOARDING) { inclusive = true } }
                    }
                },
            )
        }
        composable(HOME) {
            HomeScreen(
                onOpenStation = { navController.navigate("$STATION/$it") },
                onChangeStation = { navController.navigate(PICKER) },
                onOpenSettings = { navController.navigate(SETTINGS) },
                onFixAlerts = { navController.navigate(ONBOARDING) },
            )
        }
        composable(SETTINGS) {
            SettingsScreen(onBack = navController::popBackStack)
        }
        composable("$STATION/{${StationDetailViewModel.STATION_ID_ARG}}") {
            StationDetailScreen(onBack = navController::popBackStack)
        }
        composable(PICKER) {
            StationPickerScreen(onDone = navController::popBackStack)
        }
    }
}

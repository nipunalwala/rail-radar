package com.trainnearme.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trainnearme.ui.home.HomeScreen
import com.trainnearme.ui.picker.StationPickerScreen
import com.trainnearme.ui.settings.SettingsScreen
import com.trainnearme.ui.station.StationDetailScreen
import com.trainnearme.ui.station.StationDetailViewModel

private const val HOME = "home"
private const val PICKER = "stations"
private const val STATION = "station"
private const val SETTINGS = "settings"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onOpenStation = { navController.navigate("$STATION/$it") },
                onChangeStation = { navController.navigate(PICKER) },
                onOpenSettings = { navController.navigate(SETTINGS) },
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

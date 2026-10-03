package com.trainnearme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trainnearme.proximity.Notifier
import com.trainnearme.ui.AppNavHost
import com.trainnearme.ui.theme.TrainNearMeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Station to open, set when the app is started from an alert notification. */
    private var stationToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on a restore: the notification's intent would open the station again.
        if (savedInstanceState == null) stationToOpen = intent.stationId()
        setContent {
            TrainNearMeTheme {
                AppNavHost(
                    stationToOpen = stationToOpen,
                    onStationOpened = { stationToOpen = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stationToOpen = intent.stationId()
    }

    private fun Intent.stationId(): String? = getStringExtra(Notifier.EXTRA_STATION_ID)
}

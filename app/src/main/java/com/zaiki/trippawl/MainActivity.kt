package com.zaiki.trippawl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.tracking.DriveTrackingForegroundService
import com.zaiki.trippawl.tracking.DriveTrackingManager
import com.zaiki.trippawl.ui.navigation.TripPawlNavHost
import com.zaiki.trippawl.ui.theme.ZaikiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity entry point for TripPawl.
 * Observes the theme preference from [SettingsRepository], handles incoming drive notification
 * intents ("Who is riding with you today?"), and renders [TripPawlNavHost] inside [ZaikiTheme].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var driveTrackingManager: DriveTrackingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDrivePromptIntent(intent)

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = PricingSettings()
            )

            ZaikiTheme(darkTheme = settings.isDarkTheme) {
                TripPawlNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        driveTrackingManager.ensureSpeedThresholdMonitorRunning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDrivePromptIntent(intent)
    }

    private fun handleDrivePromptIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(DriveTrackingForegroundService.EXTRA_OPEN_PASSENGER_PROMPT, false) == true) {
            driveTrackingManager.openPassengerPrompt()
        }
    }
}

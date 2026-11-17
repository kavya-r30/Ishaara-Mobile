package com.ishaara.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ishaara.mobile.input.InputActivity
import com.ishaara.mobile.service.IshaaraService
import com.ishaara.mobile.ui.HomeScreen
import com.ishaara.mobile.ui.SettingsScreen
import com.ishaara.mobile.ui.SetupScreen
import com.ishaara.mobile.ui.SetupStatus
import com.ishaara.mobile.ui.theme.IshaaraTheme

private enum class Screen { HOME, SETUP, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as IshaaraApp

        setContent {
            IshaaraTheme {
                val context = LocalContext.current
                var screen by remember { mutableStateOf(Screen.HOME) }
                val settings by app.settings.settings.collectAsState(initial = null)
                val history by app.history.entries.collectAsState(initial = emptyList())
                val service by IshaaraService.instance.collectAsState()
                var ready by remember { mutableStateOf(false) }

                LifecycleResumeEffect(settings, service) {
                    ready = SetupStatus.ready(context, settings)
                    onPauseOrDispose { }
                }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        ready = ready,
                        history = history,
                        onSetup = { screen = Screen.SETUP },
                        onSettings = { screen = Screen.SETTINGS },
                        onOpenBar = { startActivity(Intent(context, InputActivity::class.java)) },
                        onSpeak = {
                            startActivity(Intent(context, InputActivity::class.java).putExtra(InputActivity.EXTRA_LISTEN, true))
                        },
                        onExample = { app.session.start(it, null, spoken = false) },
                    )
                    Screen.SETUP -> SetupScreen(
                        settings = settings,
                        onBack = { screen = Screen.HOME },
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> settings?.let { current ->
                        SettingsScreen(
                            app = app,
                            settings = current,
                            onBack = { screen = Screen.HOME },
                            onSetup = { screen = Screen.SETUP },
                        )
                    }
                }
            }
        }
    }
}

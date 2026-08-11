package com.example.ytshortsblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.ytshortsblocker.permissions.PermissionsState
import com.example.ytshortsblocker.ui.OnboardingScreen
import com.example.ytshortsblocker.ui.SettingsScreen
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YTShortsBlockerTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { Onboarding, Settings }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var screen by remember {
        mutableStateOf(
            if (PermissionsState.read(context).allGranted) Screen.Settings
            else Screen.Onboarding
        )
    }

    when (screen) {
        Screen.Onboarding -> OnboardingScreen(onContinue = { screen = Screen.Settings })
        Screen.Settings -> SettingsScreen(onOpenPermissions = { screen = Screen.Onboarding })
    }
}

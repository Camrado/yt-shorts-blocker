package com.example.ytshortsblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.example.ytshortsblocker.ui.SettingsScreen
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind the system bars for a modern edge-to-edge look
        setContent {
            YTShortsBlockerTheme {
                SettingsScreen()
            }
        }
    }
}

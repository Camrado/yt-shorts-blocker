package com.example.ytshortsblocker.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.ytshortsblocker.permissions.AppPermissions
import com.example.ytshortsblocker.permissions.PermissionsState
import com.example.ytshortsblocker.permissions.findActivity
import com.example.ytshortsblocker.permissions.rememberPermissionsState
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme


@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val checker = rememberPermissionsState()
    val state = checker.state

    var notificationsBlocked by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        checker.refresh()
        if (!granted) {
            val activity = context.findActivity()
            notificationsBlocked = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
        }
    }

    OnboardingContent(
        state = state,
        notificationsBlocked = notificationsBlocked,
        notificationsRequired = AppPermissions.notificationPermissionRequired,
        onOpenAccessibility = { context.startActivity(AppPermissions.accessibilitySettingsIntent()) },
        onOpenOverlay = { context.startActivity(AppPermissions.overlaySettingsIntent(context)) },
        onRequestNotifications = {
            if (notificationsBlocked) {
                context.startActivity(AppPermissions.appDetailsSettingsIntent(context))
            } else {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRefresh = checker.refresh,
        onContinue = onContinue,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContent(
    state: PermissionsState,
    notificationsBlocked: Boolean,
    notificationsRequired: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Setup") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "This app needs three permissions to block Shorts. Two of them can only be granted " +
                    "in system settings — Android will not show a popup for those.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PermissionCard(
                step = 1,
                title = "Accessibility service",
                why = "Lets the app see when Shorts is on screen. This is the only way to detect it.",
                granted = state.accessibility,
                buttonText = if (state.accessibility) "Open accessibility settings" else "Grant",
                onClick = onOpenAccessibility,
                note = "Settings opens on the Accessibility list — find \"YT Shorts Blocker\", tap it, " +
                    "turn the switch on and confirm.",
            )

            PermissionCard(
                step = 2,
                title = "Display over other apps",
                why = "Lets the app draw the blocking screen on top of YouTube.",
                granted = state.overlay,
                buttonText = if (state.overlay) "Open overlay settings" else "Grant",
                onClick = onOpenOverlay,
                note = "Opens straight to this app's toggle. Turn it on and press back.",
            )

            PermissionCard(
                step = 3,
                title = "Notifications",
                why = "Required for the always-on notification the timer service must show.",
                granted = state.notifications,
                buttonText = when {
                    !notificationsRequired -> "Not needed"
                    notificationsBlocked -> "Open app settings"
                    else -> "Grant"
                },
                onClick = onRequestNotifications,
                enabled = notificationsRequired && !state.notifications,
                note = if (!notificationsRequired) {
                    "Your Android version grants this automatically."
                } else if (notificationsBlocked) {
                    "The dialog will not show again. Go to Permissions → Notifications and allow it."
                } else {
                    "A normal Android popup appears — tap Allow."
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text("Re-check") }
                Button(onClick = onContinue, enabled = state.allGranted) { Text("Continue") }
            }

            if (!state.allGranted) {
                Text(
                    "Continue unlocks once all three are granted.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    step: Int,
    title: String,
    why: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    note: String,
    enabled: Boolean = true,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$step. $title",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(granted = granted)
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick, enabled = enabled) { Text(buttonText) }
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    val background = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.errorContainer
    val foreground = if (granted) Color.White else MaterialTheme.colorScheme.onErrorContainer
    Text(
        text = if (granted) "Granted" else "Not granted",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = foreground,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    YTShortsBlockerTheme {
        OnboardingContent(
            state = PermissionsState(accessibility = false, overlay = true, notifications = true),
            notificationsBlocked = false,
            notificationsRequired = true,
            onOpenAccessibility = {},
            onOpenOverlay = {},
            onRequestNotifications = {},
            onRefresh = {},
            onContinue = {},
        )
    }
}

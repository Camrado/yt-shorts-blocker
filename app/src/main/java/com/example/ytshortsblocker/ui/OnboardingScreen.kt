package com.example.ytshortsblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ytshortsblocker.permissions.AppPermissions
import com.example.ytshortsblocker.permissions.PermissionsState
import com.example.ytshortsblocker.permissions.rememberPermissionsState
import com.example.ytshortsblocker.ui.theme.StatusCalm
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val checker = rememberPermissionsState()

    OnboardingContent(
        state = checker.state,
        onOpenAccessibility = { context.startActivity(AppPermissions.accessibilitySettingsIntent()) },
        onOpenOverlay = { context.startActivity(AppPermissions.overlaySettingsIntent(context)) },
        onRefresh = checker.refresh,
        onContinue = onContinue,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContent(
    state: PermissionsState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grantedCount = listOf(state.accessibility, state.overlay).count { it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "$grantedCount of 2 granted",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Both are switches you turn on in system settings — Android will not show a popup " +
                    "for either of them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))

            PermissionCard(
                step = 1,
                title = "Accessibility service",
                why = "Lets the app see when Shorts is on screen. This is the only way to detect it.",
                granted = state.accessibility,
                buttonText = if (state.accessibility) "Open settings" else "Grant",
                onClick = onOpenAccessibility,
                note = "Find \"YT Shorts Blocker\" in the Accessibility list, tap it, turn the " +
                    "switch on and confirm.",
            )

            PermissionCard(
                step = 2,
                title = "Display over other apps",
                why = "Lets the app draw the blocking screen on top of YouTube.",
                granted = state.overlay,
                buttonText = if (state.overlay) "Open settings" else "Grant",
                onClick = onOpenOverlay,
                note = "Opens straight to this app's toggle. Turn it on and press back.",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Re-check")
                }
                Button(
                    onClick = onContinue,
                    enabled = state.allGranted,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Continue")
                }
            }

            if (!state.allGranted) {
                Text(
                    "Continue unlocks once both are granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(step = step, granted = granted)
                Spacer(Modifier.size(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(granted = granted)
            }
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(buttonText)
            }
        }
    }
}

/** Numbered circle that turns into a tick once the permission is granted. */
@Composable
private fun StepBadge(step: Int, granted: Boolean) {
    val background = if (granted) StatusCalm else MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (granted) "✓" else "$step",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (granted) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    val color = if (granted) StatusCalm else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (granted) "Granted" else "Needed",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    YTShortsBlockerTheme(darkTheme = true) {
        OnboardingContent(
            state = PermissionsState(accessibility = true, overlay = false),
            onOpenAccessibility = {},
            onOpenOverlay = {},
            onRefresh = {},
            onContinue = {},
        )
    }
}

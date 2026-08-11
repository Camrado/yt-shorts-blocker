package com.example.ytshortsblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ytshortsblocker.data.SettingsRepository
import com.example.ytshortsblocker.service.BlockerState
import com.example.ytshortsblocker.service.ShortsDetectionState
import com.example.ytshortsblocker.service.UsageTrackingService
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val limitMinutes by repository.dailyLimitMinutes.collectAsState(initial = SettingsRepository.DEFAULT_LIMIT_MINUTES)
    val enabled by repository.enabled.collectAsState(initial = SettingsRepository.DEFAULT_ENABLED)
    val usageSeconds by repository.usageSecondsToday.collectAsState(initial = 0)

    val shortsOnScreen by ShortsDetectionState.isShortsOnScreen.collectAsState()
    val budgetExhausted by BlockerState.budgetExhausted.collectAsState()
    val serviceRunning by BlockerState.serviceRunning.collectAsState()

    LaunchedEffect(Unit) { repository.rolloverIfNewDay() }

    // The service's own lifetime follows the toggle: it stops itself when disabled, and this
    // starts it again when enabled. Starting a foreground service is only allowed while the app
    // is in the foreground, which it is whenever this screen is showing.
    LaunchedEffect(enabled) {
        if (enabled) UsageTrackingService.start(context) else UsageTrackingService.stop(context)
    }

    SettingsContent(
        limitMinutes = limitMinutes,
        enabled = enabled,
        usageSeconds = usageSeconds,
        shortsOnScreen = shortsOnScreen,
        budgetExhausted = budgetExhausted,
        serviceRunning = serviceRunning,
        onLimitChange = { newLimit -> scope.launch { repository.setDailyLimitMinutes(newLimit) } },
        onEnabledChange = { value -> scope.launch { repository.setEnabled(value) } },
        onOpenPermissions = onOpenPermissions,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    limitMinutes: Int,
    enabled: Boolean,
    usageSeconds: Int,
    shortsOnScreen: Boolean,
    budgetExhausted: Boolean,
    serviceRunning: Boolean,
    onLimitChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("YT Shorts Blocker") },
                actions = {
                    TextButton(onClick = onOpenPermissions) { Text("Permissions") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EnableCard(enabled = enabled, onEnabledChange = onEnabledChange)
            LimitCard(limitMinutes = limitMinutes, onLimitChange = onLimitChange)
            UsageCard(
                usageSeconds = usageSeconds,
                limitMinutes = limitMinutes,
                shortsOnScreen = shortsOnScreen,
                budgetExhausted = budgetExhausted,
                serviceRunning = serviceRunning,
            )
        }
    }
}

@Composable
private fun EnableCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Blocker enabled", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (enabled) "Shorts will be blocked once you hit your limit"
                    else "Blocking is currently off",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun LimitCard(limitMinutes: Int, onLimitChange: (Int) -> Unit) {
    val presets = listOf(15, 30, 60)
    var customText by remember { mutableStateOf("") }
    val customValue = customText.toIntOrNull()
    val customValid = customValue != null && customValue in 1..SettingsRepository.MAX_LIMIT_MINUTES

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Daily Shorts limit", style = MaterialTheme.typography.titleMedium)
            Text("$limitMinutes min per day", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = limitMinutes == preset,
                        onClick = { onLimitChange(preset) },
                        label = { Text("$preset min") },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customText,
                    // Keep only digits, cap length so nobody types a 9-digit "limit".
                    onValueChange = { input -> customText = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Custom (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (customValid) onLimitChange(customValue!!)
                        customText = ""
                    },
                    enabled = customValid,
                ) {
                    Text("Set")
                }
            }
        }
    }
}

@Composable
private fun UsageCard(
    usageSeconds: Int,
    limitMinutes: Int,
    shortsOnScreen: Boolean,
    budgetExhausted: Boolean,
    serviceRunning: Boolean,
) {
    val usedMinutes = usageSeconds / 60
    val usedSeconds = usageSeconds % 60
    val limitSeconds = limitMinutes * 60
    val fraction = if (limitSeconds > 0) (usageSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f) else 0f
    val remaining = ((limitSeconds - usageSeconds) / 60).coerceAtLeast(0)

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Today's Shorts usage", style = MaterialTheme.typography.titleMedium)
            Text(
                "$usedMinutes min $usedSeconds sec used of $limitMinutes min",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = when {
                    budgetExhausted -> "Daily limit reached."
                    else -> "$remaining min left today."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append(if (serviceRunning) "Timer running" else "Timer stopped")
                    append(" · ")
                    append(if (shortsOnScreen) "Shorts on screen — counting" else "Shorts not on screen")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    YTShortsBlockerTheme {
        SettingsContent(
            limitMinutes = 30,
            enabled = true,
            usageSeconds = 12 * 60 + 34,
            shortsOnScreen = true,
            budgetExhausted = false,
            serviceRunning = true,
            onLimitChange = {},
            onEnabledChange = {},
            onOpenPermissions = {},
        )
    }
}

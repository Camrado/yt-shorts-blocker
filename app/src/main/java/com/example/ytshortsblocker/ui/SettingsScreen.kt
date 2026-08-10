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
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val limitMinutes by repository.dailyLimitMinutes.collectAsState(initial = SettingsRepository.DEFAULT_LIMIT_MINUTES)
    val enabled by repository.enabled.collectAsState(initial = SettingsRepository.DEFAULT_ENABLED)
    val usageSeconds by repository.usageSecondsToday.collectAsState(initial = 0)

    LaunchedEffect(Unit) { repository.rolloverIfNewDay() }

    SettingsContent(
        limitMinutes = limitMinutes,
        enabled = enabled,
        usageSeconds = usageSeconds,
        onLimitChange = { newLimit -> scope.launch { repository.setDailyLimitMinutes(newLimit) } },
        onEnabledChange = { value -> scope.launch { repository.setEnabled(value) } },
        onAddDebugUsage = { scope.launch { repository.addUsageSeconds(5 * 60) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    limitMinutes: Int,
    enabled: Boolean,
    usageSeconds: Int,
    onLimitChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAddDebugUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("YT Shorts Blocker") }) },
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
            UsageCard(usageSeconds = usageSeconds, limitMinutes = limitMinutes, onAddDebugUsage = onAddDebugUsage)
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
private fun UsageCard(usageSeconds: Int, limitMinutes: Int, onAddDebugUsage: () -> Unit) {
    val usedMinutes = usageSeconds / 60
    val usedSeconds = usageSeconds % 60
    val limitSeconds = limitMinutes * 60
    val fraction = if (limitSeconds > 0) (usageSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f) else 0f

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
                "Placeholder value — real time tracking arrives with the service step.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onAddDebugUsage) {
                Text("+5 min (debug)")
            }
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
            onLimitChange = {},
            onEnabledChange = {},
            onAddDebugUsage = {},
        )
    }
}

package com.example.ytshortsblocker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytshortsblocker.data.DayUsage
import com.example.ytshortsblocker.data.SettingsRepository
import com.example.ytshortsblocker.service.BlockerState
import com.example.ytshortsblocker.service.ShortsDetectionState
import com.example.ytshortsblocker.ui.theme.YTShortsBlockerTheme
import com.example.ytshortsblocker.ui.theme.statusColorFor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    val monitoringActive by BlockerState.monitoringActive.collectAsState()
    val history by repository.history.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) { repository.rolloverIfNewDay() }

    SettingsContent(
        limitMinutes = limitMinutes,
        enabled = enabled,
        usageSeconds = usageSeconds,
        shortsOnScreen = shortsOnScreen,
        budgetExhausted = budgetExhausted,
        monitoringActive = monitoringActive,
        history = history,
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
    monitoringActive: Boolean,
    history: List<DayUsage>,
    onLimitChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Shorts Blocker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenPermissions) { Text("Permissions") }
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
            UsageHero(
                usageSeconds = usageSeconds,
                limitMinutes = limitMinutes,
                budgetExhausted = budgetExhausted,
                shortsOnScreen = shortsOnScreen,
                monitoringActive = monitoringActive,
                enabled = enabled,
            )
            EnableCard(enabled = enabled, onEnabledChange = onEnabledChange)
            LimitCard(limitMinutes = limitMinutes, onLimitChange = onLimitChange)
            StatsCard(history = history)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ---------------------------------- Hero ---------------------------------- */

@Composable
private fun UsageHero(
    usageSeconds: Int,
    limitMinutes: Int,
    budgetExhausted: Boolean,
    shortsOnScreen: Boolean,
    monitoringActive: Boolean,
    enabled: Boolean,
) {
    val limitSeconds = (limitMinutes * 60).coerceAtLeast(1)
    val target = (usageSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f)
    // Animating the sweep makes the ring feel alive as the counter ticks up.
    val fraction by animateFloatAsState(targetValue = target, label = "usageRing")
    val accent = statusColorFor(target, budgetExhausted)
    val remainingMinutes = ((limitSeconds - usageSeconds) / 60).coerceAtLeast(0)

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(
                    fraction = fraction,
                    accent = accent,
                    track = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${usageSeconds / 60}",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "of $limitMinutes min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = when {
                    !enabled -> "Blocking is off"
                    budgetExhausted -> "Limit reached — Shorts is blocked"
                    else -> "$remainingMinutes min left today"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    text = if (monitoringActive) "Monitoring" else "Not monitoring",
                    color = if (monitoringActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shortsOnScreen) Pill(text = "Counting now", color = accent)
            }
        }
    }
}

/** The ring itself: a full track circle with the used portion drawn over it. */
@Composable
private fun ProgressRing(fraction: Float, accent: Color, track: Color) {
    Canvas(modifier = Modifier.size(190.dp)) {
        val stroke = 18.dp.toPx()
        // Inset by half the stroke, otherwise the thick line is clipped at the canvas edge.
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (fraction > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/* ---------------------------------- Cards ---------------------------------- */

/** Shared card look so every section matches. */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        content()
    }
}

@Composable
private fun EnableCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Blocker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) "Shorts blocked once the limit is hit" else "Currently off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Daily limit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Segmented row of presets — the selected one is filled, the rest are quiet.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    val selected = limitMinutes == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onLimitChange(preset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$preset",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { input -> customText = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Custom") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (customValid) onLimitChange(customValue!!)
                        customText = ""
                    },
                    enabled = customValid,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Set")
                }
            }
        }
    }
}

/**
 * Last seven days, oldest first. Days with no record render as zero so the chart keeps a stable
 * shape, and today is highlighted in the accent colour.
 */
@Composable
private fun StatsCard(history: List<DayUsage>) {
    val byDate = remember(history) { history.associateBy { it.date } }
    val today = remember { LocalDate.now() }
    val days = remember(byDate, today) {
        (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            date to (byDate[date.toString()]?.seconds ?: 0)
        }
    }
    val peak = days.maxOf { it.second }.coerceAtLeast(1)
    val weekTotal = days.sumOf { it.second }

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last 7 days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${weekTotal / 60} min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "${weekTotal / 60 / 7} min per day on average",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))

            days.forEach { (date, seconds) ->
                val isToday = date == today
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                    ) {
                        if (seconds > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(seconds.toFloat() / peak)
                                    .fillMaxHeight()
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                    }
                    Text(
                        text = "${seconds / 60}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    YTShortsBlockerTheme(darkTheme = true) {
        SettingsContent(
            limitMinutes = 30,
            enabled = true,
            usageSeconds = 22 * 60,
            shortsOnScreen = true,
            budgetExhausted = false,
            monitoringActive = true,
            history = listOf(
                DayUsage(LocalDate.now().toString(), 22 * 60),
                DayUsage(LocalDate.now().minusDays(1).toString(), 31 * 60),
                DayUsage(LocalDate.now().minusDays(2).toString(), 12 * 60),
            ),
            onLimitChange = {},
            onEnabledChange = {},
            onOpenPermissions = {},
        )
    }
}

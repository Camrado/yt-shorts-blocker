package com.example.ytshortsblocker.service

import android.util.Log
import com.example.ytshortsblocker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Counts the seconds spent in Shorts and writes them to DataStore.
 *
 * This used to be a foreground service. It now lives inside the accessibility service, which is
 * already a persistent, system-managed process — so there is no notification, no extra permission,
 * and it comes back by itself after a reboot.
 *
 * The important property: there is NO always-running timer. The ticking coroutine is created when
 * Shorts appears and cancelled when it goes away, so an idle phone does no work at all.
 */
class UsageTracker(
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private var pendingSeconds = 0
    private var tickJob: Job? = null

    fun start() {
        scope.launch { repository.rolloverIfNewDay() }
        scope.launch { observeCountingCondition() }
        scope.launch { observeBudget() }
    }

    /** Flushes whatever is uncounted. Call before the owning service goes away. */
    fun stop() {
        stopTicking()
    }

    /**
     * Counting happens only while Shorts is on screen AND the blocker is enabled. Collecting the
     * flow means we are suspended — not polling — whenever that is false.
     */
    private suspend fun observeCountingCondition() {
        combine(
            ShortsDetectionState.isShortsOnScreen,
            repository.enabled,
        ) { onScreen, enabled -> onScreen && enabled }
            .distinctUntilChanged()
            .collect { shouldCount ->
                if (shouldCount) startTicking() else stopTicking()
            }
    }

    /** Recomputes whether today's budget is spent, reacting to limit changes mid-session. */
    private suspend fun observeBudget() {
        combine(
            repository.enabled,
            repository.dailyLimitMinutes,
            repository.usageSecondsToday,
        ) { enabled, limit, used -> Triple(enabled, limit, used) }
            .collect { (enabled, limit, used) ->
                BlockerState.setLimitMinutes(limit)
                val exhausted = enabled && used >= limit * 60
                if (exhausted != BlockerState.budgetExhausted.value) {
                    Log.d(TAG, "budgetExhausted -> $exhausted ($used s of ${limit * 60} s)")
                }
                BlockerState.setBudgetExhausted(exhausted)
            }
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        Log.d(TAG, "counting started")
        tickJob = scope.launch {
            var ticksSinceFlush = 0
            while (isActive) {
                delay(TICK_MS)
                pendingSeconds++
                ticksSinceFlush++
                if (ticksSinceFlush >= FLUSH_EVERY_TICKS) {
                    flush()
                    ticksSinceFlush = 0
                }
            }
        }
    }

    private fun stopTicking() {
        if (tickJob == null) return
        tickJob?.cancel()
        tickJob = null
        Log.d(TAG, "counting stopped")
        // The tick job is gone, so flush on the parent scope instead.
        scope.launch { flush() }
    }

    /**
     * Batched on purpose: each DataStore edit rewrites the whole file, so writing once per second
     * would be pointless disk churn. addUsageSeconds() also handles the midnight rollover.
     */
    private suspend fun flush() {
        val seconds = pendingSeconds
        pendingSeconds = 0
        if (seconds > 0) repository.addUsageSeconds(seconds)
    }

    companion object {
        private const val TAG = "ShortsBlocker"
        private const val TICK_MS = 1000L
        private const val FLUSH_EVERY_TICKS = 5
    }
}

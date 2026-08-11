package com.example.ytshortsblocker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live state published by [UsageTrackingService]. Same pattern as [ShortsDetectionState]: a
 * process-wide singleton, written only by the service, read by anyone.
 *
 * The overlay (next step) will listen to [budgetExhausted].
 */
object BlockerState {

    private val _budgetExhausted = MutableStateFlow(false)

    /** True once today's Shorts usage has reached the daily limit. */
    val budgetExhausted: StateFlow<Boolean> = _budgetExhausted.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)

    /** True while the foreground service is alive. */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    internal fun setBudgetExhausted(value: Boolean) {
        _budgetExhausted.value = value
    }

    internal fun setServiceRunning(value: Boolean) {
        _serviceRunning.value = value
    }
}

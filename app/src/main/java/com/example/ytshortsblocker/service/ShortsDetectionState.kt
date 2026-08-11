package com.example.ytshortsblocker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The debounced answer to "is the user looking at Shorts right now?".
 *
 * Process-wide singleton because the accessibility service is itself a singleton owned by the
 * system, and other components (the timer service next) need to read this without holding a
 * reference to it. Read-only from outside; only the service updates it.
 */
object ShortsDetectionState {

    private val _isShortsOnScreen = MutableStateFlow(false)

    /** Collect this to react to Shorts starting/stopping. Already debounced. */
    val isShortsOnScreen: StateFlow<Boolean> = _isShortsOnScreen.asStateFlow()

    internal fun set(value: Boolean) {
        _isShortsOnScreen.value = value
    }
}

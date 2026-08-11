package com.example.ytshortsblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ytshortsblocker.data.SettingsRepository
import com.example.ytshortsblocker.overlay.BlockOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ShortsAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** Main dispatcher: the overlay touches WindowManager, which must run on the main thread. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val overlay by lazy { BlockOverlayController(this) }
    private val tracker by lazy {
        UsageTracker(SettingsRepository(applicationContext), scope)
    }

    private var lastEvaluationAt = 0L
    private var lastDumpAt = 0L
    private var dumpCounter = 0

    /** The published, debounced value. */
    private var stableShorts = false

    /** A pending flip waiting out its debounce window, or null if none. */
    private var pendingShorts: Boolean? = null

    private val commitPending = Runnable {
        val target = pendingShorts ?: return@Runnable
        pendingShorts = null
        stableShorts = target
        Log.d(TAG, "isShortsOnScreen -> $target")
        ShortsDetectionState.set(target)
        if (target) startWatchdog() else stopWatchdog()
    }

    /**
     * While Shorts is on screen we get no events once the user leaves YouTube, because the manifest
     * filters events to the YouTube package. This poll notices that we are no longer in YouTube.
     * It only runs while Shorts is considered active.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            // Only answers "have we left YouTube?". Transitions *inside* YouTube always generate
            // events, so re-running full detection here would duplicate work we already do.
            if (rootInActiveWindow?.packageName?.toString() != ShortsSignature.YOUTUBE_PACKAGE) {
                onRawSignal(false)
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected — watching ${ShortsSignature.YOUTUBE_PACKAGE}")
        Log.d(TAG, "DEBUG_DUMP_TREE=$DEBUG_DUMP_TREE (dumps go to tag '$DUMP_TAG')")
        BlockerState.setMonitoringActive(true)
        tracker.start()
        observeBlockCondition()
    }

    /**
     * The block fires only when both conditions hold: the budget is spent AND Shorts is actually on
     * screen. distinctUntilChanged means we act on transitions, not on every emission, so the back
     * action fires once per block rather than continuously.
     */
    private fun observeBlockCondition() {
        scope.launch {
            combine(
                ShortsDetectionState.isShortsOnScreen,
                BlockerState.budgetExhausted,
            ) { onScreen, exhausted -> onScreen && exhausted }
                .distinctUntilChanged()
                .collect { shouldBlock ->
                    if (shouldBlock) {
                        Log.d(TAG, "blocking Shorts")
                        // Belt and braces: nudge YouTube off Shorts straight away, covering the few
                        // frames before the overlay is actually drawn.
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        overlay.show(BlockerState.limitMinutes.value)
                    } else {
                        overlay.hide()
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // --- Cheap rejects first. None of these touch the view tree. ---

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        if (event.packageName?.toString() != ShortsSignature.YOUTUBE_PACKAGE) return

        val now = SystemClock.uptimeMillis()
        val shouldEvaluate = now - lastEvaluationAt >= EVALUATION_INTERVAL_MS
        val shouldDump = DEBUG_DUMP_TREE && now - lastDumpAt >= DUMP_COOLDOWN_MS
        if (!shouldEvaluate && !shouldDump) return

        // --- Only now do we pay for the IPC call into YouTube's view tree. ---

        val root = rootInActiveWindow ?: return

        if (shouldEvaluate) {
            lastEvaluationAt = now
            onRawSignal(ShortsDetector.detect(root))
        }

        if (shouldDump && root.packageName?.toString() == ShortsSignature.YOUTUBE_PACKAGE) {
            lastDumpAt = now
            dumpTree(root, AccessibilityEvent.eventTypeToString(type))
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind — service switched off")
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /**
     * The overlay is attached to the window manager, not to this service, so nothing removes it
     * automatically. Failing to do this here would leave a black screen covering the phone with no
     * way to dismiss it.
     */
    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        // hideNow, not hide: a pending delayed removal would never run once we are gone.
        overlay.hideNow()
        tracker.stop()
        stableShorts = false
        pendingShorts = null
        ShortsDetectionState.set(false)
        // Monitoring is over, so nothing can clear a stale block. Fail open.
        BlockerState.setBudgetExhausted(false)
        BlockerState.setMonitoringActive(false)
        scope.cancel()
    }

    // ----- Debounce -----

    /**
     * Feeds a raw, noisy reading into the debouncer. The published state only changes after the
     * new value has held for its debounce window, so swiping between Shorts (which briefly loses
     * the markers) does not register as leaving Shorts.
     */
    private fun onRawSignal(raw: Boolean) {
        if (raw == stableShorts) {
            // Already correct — cancel any pending flip.
            if (pendingShorts != null) {
                pendingShorts = null
                handler.removeCallbacks(commitPending)
            }
            return
        }
        if (pendingShorts == raw) return // already scheduled, let it run

        pendingShorts = raw
        handler.removeCallbacks(commitPending)
        handler.postDelayed(commitPending, if (raw) DEBOUNCE_ON_MS else DEBOUNCE_OFF_MS)
    }

    private fun startWatchdog() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        handler.removeCallbacks(watchdog)
    }

    // ----- Debug tree dump -----

    private class DumpState {
        var count = 0
        var truncated = false
    }

    private fun dumpTree(root: AccessibilityNodeInfo, trigger: String) {
        val id = ++dumpCounter
        val state = DumpState()
        Log.d(DUMP_TAG, ">>>>> DUMP #$id trigger=$trigger detected=${ShortsDetector.detect(root)}")
        writeNode(root, 0, state)
        Log.d(
            DUMP_TAG,
            "<<<<< END DUMP #$id nodes=${state.count}" +
                if (state.truncated) " (TRUNCATED at $MAX_NODES)" else "",
        )
    }

    private fun writeNode(node: AccessibilityNodeInfo, depth: Int, state: DumpState) {
        if (state.count >= MAX_NODES) {
            state.truncated = true
            return
        }
        if (depth > MAX_DEPTH) return
        state.count++

        val viewId = node.viewIdResourceName
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()

        val informative = !viewId.isNullOrBlank() || !desc.isNullOrBlank() || !text.isNullOrBlank()
        if (!DUMP_ONLY_INFORMATIVE || informative) {
            val line = StringBuilder()
            line.append('|').append("  ".repeat(depth.coerceAtMost(MAX_INDENT)))
            line.append(shortClass(node.className?.toString()))
            if (!viewId.isNullOrBlank()) line.append(" id=").append(shortId(viewId))
            if (!desc.isNullOrBlank()) line.append(" desc=\"").append(clip(desc)).append('"')
            if (!text.isNullOrBlank()) line.append(" text=\"").append(clip(text)).append('"')
            if (!node.isVisibleToUser) line.append(" [offscreen]")
            Log.d(DUMP_TAG, line.toString())
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            writeNode(child, depth + 1, state)
        }
    }

    private fun shortClass(name: String?): String = name?.substringAfterLast('.') ?: "?"

    private fun shortId(viewId: String): String =
        if (viewId.startsWith("${ShortsSignature.YOUTUBE_PACKAGE}:id/")) {
            viewId.substringAfter(":id/")
        } else {
            viewId
        }

    private fun clip(value: String): String {
        val flat = value.replace('\n', ' ').trim()
        return if (flat.length <= MAX_TEXT_LENGTH) flat else flat.take(MAX_TEXT_LENGTH) + "…"
    }

    companion object {
        const val TAG = "ShortsBlocker"
        const val DUMP_TAG = "ShortsDump"

        /** Flip to true to dump YouTube's view tree again when detection needs re-tuning. */
        const val DEBUG_DUMP_TREE = false
        private const val DUMP_ONLY_INFORMATIVE = true

        /** Detection runs at most this often, no matter how many events arrive. */
        private const val EVALUATION_INTERVAL_MS = 300L

        /** Shorts must be seen for this long before we say yes. */
        private const val DEBOUNCE_ON_MS = 500L

        /** Longer, so swiping between Shorts is not read as leaving. */
        private const val DEBOUNCE_OFF_MS = 1500L

        /** How often we re-check while Shorts is active. */
        private const val WATCHDOG_INTERVAL_MS = 1000L

        private const val DUMP_COOLDOWN_MS = 5000L
        private const val MAX_NODES = 400
        private const val MAX_DEPTH = 30
        private const val MAX_INDENT = 20
        private const val MAX_TEXT_LENGTH = 80
    }
}

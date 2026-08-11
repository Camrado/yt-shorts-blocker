package com.example.ytshortsblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ShortsAccessibilityService : AccessibilityService() {

    private var lastContentLogAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected — service is live, watching $YOUTUBE_PACKAGE")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val typeName = AccessibilityEvent.eventTypeToString(event.eventType)

        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (isContentChange) {
            val now = SystemClock.uptimeMillis()
            if (now - lastContentLogAt < CONTENT_LOG_THROTTLE_MS)
                return
            lastContentLogAt = now
        }

        val activeWindowPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"

        Log.d(
            TAG,
            "YouTube event: type=$typeName " +
                "eventPackage=${event.packageName} " +
                "activeWindow=$activeWindowPackage " +
                "class=${event.className}",
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind — service switched off or app updated")
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "ShortsBlocker"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val CONTENT_LOG_THROTTLE_MS = 1000L
    }
}

package com.example.ytshortsblocker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.ytshortsblocker.R
import com.example.ytshortsblocker.permissions.AppPermissions

/**
 * Owns the full-screen blocking view that is drawn on top of YouTube.
 *
 * All methods must be called from the main thread — WindowManager requires a thread with a Looper.
 * The class is deliberately dumb: it does not decide *when* to block, only *how*.
 */
class BlockOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val handler = Handler(Looper.getMainLooper())

    /** Non-null exactly while the view is attached to the window manager. */
    private var overlayView: View? = null

    /** When the current overlay went up, used to enforce [MIN_VISIBLE_MS]. */
    private var shownAt = 0L

    private val detachRunnable = Runnable { detach() }

    val isShowing: Boolean get() = overlayView != null

    fun show(limitMinutes: Int) {
        // A fresh block cancels any pending removal and restarts the minimum display window.
        handler.removeCallbacks(detachRunnable)
        shownAt = SystemClock.uptimeMillis()

        // Adding the same view twice throws; treat a repeated show as a no-op.
        if (overlayView != null) return

        // The user can revoke "display over other apps" at any time from system settings.
        if (!AppPermissions.canDrawOverlays(context)) {
            Log.w(TAG, "overlay permission missing — cannot block")
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
        view.findViewById<TextView>(R.id.overlay_message).text =
            "You have used your $limitMinutes minute Shorts budget for today. " +
                "Shorts is blocked until tomorrow."

        try {
            windowManager.addView(view, buildLayoutParams())
            overlayView = view
            Log.d(TAG, "overlay shown")
        } catch (e: WindowManager.BadTokenException) {
            // Thrown when the overlay permission is not actually usable despite the check above.
            Log.e(TAG, "addView refused by the window manager", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "addView failed", e)
        }
    }

    /**
     * Requests removal, but keeps the overlay up until it has been visible for [MIN_VISIBLE_MS].
     * Without this the back action pulls the user out of Shorts almost immediately and the message
     * is gone before it can be read.
     */
    fun hide() {
        if (overlayView == null) return
        val remaining = MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - shownAt)
        handler.removeCallbacks(detachRunnable)
        if (remaining <= 0) {
            detach()
        } else {
            handler.postDelayed(detachRunnable, remaining)
        }
    }

    /** Removes the overlay right now, ignoring the minimum display time. Used when shutting down. */
    fun hideNow() {
        handler.removeCallbacks(detachRunnable)
        detach()
    }

    private fun detach() {
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager.removeView(view)
            Log.d(TAG, "overlay hidden")
        } catch (e: IllegalArgumentException) {
            // Already detached — nothing to do.
            Log.w(TAG, "removeView: view was not attached")
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // The only overlay type a normal app may use since Android 8. The old
            // TYPE_SYSTEM_ALERT / TYPE_PHONE types are blocked for non-system apps.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE keeps key events (including Back) flowing to YouTube underneath, so we
            // never trap the user. Touches still land on us, which is what blocks interaction.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Draw into the notch area too, so no strip of Shorts peeks through.
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return params
    }

    companion object {
        private const val TAG = "ShortsBlocker"

        /** Minimum time the block message stays on screen once shown. */
        private const val MIN_VISIBLE_MS = 5000L
    }
}

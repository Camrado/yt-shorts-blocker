package com.example.ytshortsblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.ytshortsblocker.MainActivity
import com.example.ytshortsblocker.R
import com.example.ytshortsblocker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Counts the seconds you spend in Shorts and writes them to DataStore.
 *
 * It is a *foreground* service because it must keep running while you are inside YouTube — i.e.
 * while our app is not on screen. Android aggressively kills ordinary background services; a
 * foreground service is the sanctioned way to say "this work is user-visible, leave it alone",
 * and the price the platform charges for that is a permanent notification the user can see.
 */
class UsageTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: SettingsRepository
    private lateinit var notificationManager: NotificationManager

    /** Seconds counted since the last DataStore write. */
    private var pendingSeconds = 0

    // Cached copies of DataStore values so the 1-second tick never has to touch disk.
    private var enabled = SettingsRepository.DEFAULT_ENABLED
    private var limitMinutes = SettingsRepository.DEFAULT_LIMIT_MINUTES
    private var usageSeconds = 0
    private var exhausted = false

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        notificationManager = getSystemService(NotificationManager::class.java)

        createNotificationChannel()
        // Must happen within a few seconds of being started or Android kills the process.
        startForeground(NOTIFICATION_ID, buildNotification())
        BlockerState.setServiceRunning(true)
        Log.d(TAG, "service started")

        scope.launch { repository.rolloverIfNewDay() }
        scope.launch { observeSettings() }
        scope.launch { tickLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ask the system to recreate us if it ever has to kill the process.
        return START_STICKY
    }

    /** Nothing binds to this service; the shared state objects are how others talk to it. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val unsaved = pendingSeconds
        pendingSeconds = 0
        scope.cancel()

        // The scope is gone, so persist the last few seconds on a short-lived scope of its own.
        if (unsaved > 0) {
            CoroutineScope(Dispatchers.IO).launch { repository.addUsageSeconds(unsaved) }
        }

        // Never leave the blocker latched on with nothing left to clear it: if the timer is gone
        // there is nothing keeping the budget state truthful, so fail open.
        BlockerState.setBudgetExhausted(false)
        BlockerState.setServiceRunning(false)
        Log.d(TAG, "service stopped (flushed ${unsaved}s)")
        super.onDestroy()
    }

    // ----- Settings and budget -----

    /**
     * Keeps the cached values fresh and recomputes whether the budget is spent. Because these are
     * DataStore Flows, this also reacts to the user changing the limit while Shorts is playing.
     */
    private suspend fun observeSettings() {
        combine(
            repository.enabled,
            repository.dailyLimitMinutes,
            repository.usageSecondsToday,
        ) { isEnabled, limit, used -> Triple(isEnabled, limit, used) }
            .collect { (isEnabled, limit, used) ->
                enabled = isEnabled
                limitMinutes = limit
                usageSeconds = used
                BlockerState.setLimitMinutes(limit)

                val nowExhausted = isEnabled && used >= limit * 60
                if (nowExhausted != exhausted) {
                    exhausted = nowExhausted
                    BlockerState.setBudgetExhausted(nowExhausted)
                    Log.d(TAG, "budgetExhausted -> $nowExhausted ($used s of ${limit * 60} s)")
                }
                updateNotification()

                // Turning the blocker off should not leave a permanent notification behind.
                if (!isEnabled) {
                    Log.d(TAG, "blocker disabled — stopping service")
                    stopSelf()
                }
            }
    }

    // ----- The clock -----

    /**
     * One tick per second. Time is only counted while Shorts is actually on screen and the blocker
     * is enabled, so pausing is simply "do not increment".
     */
    private suspend fun tickLoop() {
        var ticksSinceFlush = 0
        while (true) {
            delay(TICK_MS)

            val watchingShorts = ShortsDetectionState.isShortsOnScreen.value && enabled
            if (!watchingShorts) {
                // Leaving Shorts: write out whatever we counted so nothing is lost.
                if (pendingSeconds > 0) {
                    flush()
                    ticksSinceFlush = 0
                }
                continue
            }

            pendingSeconds++
            ticksSinceFlush++

            if (ticksSinceFlush >= FLUSH_EVERY_TICKS) {
                flush()
                ticksSinceFlush = 0
            }
        }
    }

    /**
     * Writes the accumulated seconds to DataStore. Batched rather than written every tick: each
     * DataStore edit rewrites the whole file, so one write per second would be needless disk churn.
     * addUsageSeconds() handles the date rollover, so a session crossing midnight resets correctly.
     */
    private suspend fun flush() {
        val seconds = pendingSeconds
        pendingSeconds = 0
        if (seconds > 0) repository.addUsageSeconds(seconds)
    }

    // ----- Notification -----

    /**
     * Android 8+ requires every notification to belong to a channel; the channel is what the user
     * sees in system settings and what they can mute. IMPORTANCE_LOW means no sound and no
     * heads-up popup — appropriate for a status notification that is always present.
     * Creating a channel that already exists is a no-op, so calling this on every start is safe.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shorts usage tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows how much Shorts time you have used today."
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val usedMinutes = usageSeconds / 60
        val text = if (exhausted) {
            "Limit reached — Shorts blocked ($usedMinutes/$limitMinutes min)"
        } else {
            "$usedMinutes of $limitMinutes min used today"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YT Shorts Blocker")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val TAG = "ShortsBlocker"
        private const val CHANNEL_ID = "usage_tracking"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1000L

        /** Persist every 5 counted seconds instead of every single one. */
        private const val FLUSH_EVERY_TICKS = 5

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UsageTrackingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageTrackingService::class.java))
        }
    }
}

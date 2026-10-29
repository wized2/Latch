package com.endroid.latch.engine

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.endroid.latch.R
import com.endroid.latch.data.RuleStore
import com.endroid.latch.data.TriggerType

/**
 * Fallback APP_OPENED poller using UsageStats when Accessibility is not enabled.
 * Prefer [LatchAccessibilityService] for instant, low-drain triggers.
 */
class AppWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null

    private val tick = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, 2500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Accessibility covers APP_OPENED — stop polling
        if (LatchAccessibilityService.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        RuleEngine.ensureChannel(this)
        val notif: Notification = NotificationCompat.Builder(this, RuleEngine.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_latch)
            .setContentTitle(getString(R.string.app_watch_title))
            .setContentText(getString(R.string.app_watch_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(42, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(42, notif)
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun poll() {
        if (LatchAccessibilityService.isEnabled(this)) {
            stopSelf()
            return
        }
        val needs = RuleStore(this).load().any {
            it.enabled && it.trigger == TriggerType.APP_OPENED && it.triggerParam.isNotBlank()
        }
        if (!needs) {
            stopSelf()
            return
        }
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val end = System.currentTimeMillis()
        val begin = end - 5000L
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var fg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                fg = event.packageName
            }
        }
        if (fg != null && fg != lastPkg) {
            lastPkg = fg
            RuleEngine.onAppOpened(this, fg)
        }
    }

    companion object {
        fun refresh(context: android.content.Context) {
            val needs = RuleStore(context).load().any {
                it.enabled && it.trigger == TriggerType.APP_OPENED
            }
            val i = Intent(context, AppWatchService::class.java)
            // Prefer Accessibility — only poll if needed and a11y off
            if (needs && !LatchAccessibilityService.isEnabled(context)) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                    else context.startService(i)
                } catch (_: Exception) { }
            } else {
                try {
                    context.stopService(i)
                } catch (_: Exception) { }
            }
        }
    }
}

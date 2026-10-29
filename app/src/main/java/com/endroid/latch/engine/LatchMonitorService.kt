package com.endroid.latch.engine

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.endroid.latch.MainActivity
import com.endroid.latch.R
import com.endroid.latch.data.RuleStore
import com.endroid.latch.receiver.ConnectivityReceiver
import com.endroid.latch.receiver.HeadsetReceiver
import com.endroid.latch.receiver.PowerReceiver
import com.endroid.latch.receiver.SystemEventReceiver
import com.endroid.latch.receiver.TimeTickReceiver

/**
 * Sticky "monitor" foreground service — host for dynamic BroadcastReceivers.
 *
 * Modern Android drops most implicit manifest receivers; registering here while
 * startForeground(specialUse) is active keeps Wi‑Fi, power, screen, and package
 * events flowing offline without WorkManager polling.
 *
 * A watchdog exact alarm restarts this service if the OS kills it.
 */
class LatchMonitorService : Service() {
    private val power = PowerReceiver()
    private val headset = HeadsetReceiver()
    private val timeTick = TimeTickReceiver()
    private val connectivity = ConnectivityReceiver()
    private val systemEvents = SystemEventReceiver()
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureMonitorChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureMonitorChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_latch)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(getString(R.string.monitor_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) {
            startForeground(NOTIF_ID, notif)
        }
        registerAll()
        scheduleWatchdog(this)
        val st = com.endroid.latch.data.SettingsStore(this)
        st.lastMonitorStartMs = System.currentTimeMillis()
        st.monitorHeals = st.monitorHeals + 1
        val pct = PowerReceiver.batteryPercent(this, null)
        if (pct >= 0) RuleEngine.onBattery(this, pct, force = false)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleWatchdog(this, delayMs = 2_000L)
        super.onTaskRemoved(rootIntent)
    }

    private fun registerAll() {
        if (registered) return
        try {
            val powerFilter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            }
            ContextCompat.registerReceiver(this, power, powerFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(
                this, headset, IntentFilter(Intent.ACTION_HEADSET_PLUG),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val timeFilter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(this, timeTick, timeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            val netFilter = IntentFilter().apply {
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(this, connectivity, netFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            val sysFilter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
                addAction(Intent.ACTION_DOCK_EVENT)
                addAction(Intent.ACTION_SHUTDOWN)
            }
            ContextCompat.registerReceiver(this, systemEvents, sysFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(this, systemEvents, pkgFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        } catch (_: Exception) {
            registered = false
        }
    }

    override fun onDestroy() {
        if (registered) {
            runCatching { unregisterReceiver(power) }
            runCatching { unregisterReceiver(headset) }
            runCatching { unregisterReceiver(timeTick) }
            runCatching { unregisterReceiver(connectivity) }
            runCatching { unregisterReceiver(systemEvents) }
            registered = false
        }
        scheduleWatchdog(this, delayMs = 3_000L)
        super.onDestroy()
    }

    private fun ensureMonitorChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                MONITOR_CHANNEL,
                getString(R.string.monitor_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.monitor_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val MONITOR_CHANNEL = "latch_monitor"
        const val NOTIF_ID = 41
        private const val WATCHDOG_MS = 10 * 60 * 1000L

        fun refresh(context: Context) {
            val needs = RuleStore(context).load().any { it.enabled }
            val i = Intent(context, LatchMonitorService::class.java)
            if (needs) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                    else context.startService(i)
                } catch (_: Exception) { }
                scheduleWatchdog(context)
            } else {
                context.stopService(i)
                cancelWatchdog(context)
            }
        }

        fun scheduleWatchdog(context: Context, delayMs: Long = WATCHDOG_MS) {
            if (!RuleStore(context).load().any { it.enabled }) return
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = watchdogPi(context)
            val trigger = SystemClock.elapsedRealtime() + delayMs
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            } catch (_: Exception) { }
        }

        fun cancelWatchdog(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(watchdogPi(context))
        }

        private fun watchdogPi(context: Context): PendingIntent {
            val i = Intent(context, com.endroid.latch.receiver.WatchdogReceiver::class.java).apply {
                action = com.endroid.latch.receiver.WatchdogReceiver.ACTION
            }
            return PendingIntent.getBroadcast(
                context, 99, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

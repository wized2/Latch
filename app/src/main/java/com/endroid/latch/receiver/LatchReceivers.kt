package com.endroid.latch.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import com.endroid.latch.data.RuleStore
import com.endroid.latch.engine.LatchMonitorService
import com.endroid.latch.engine.RuleEngine
import com.endroid.latch.engine.TimeScheduler
import com.endroid.latch.widget.LatchWidgetProvider
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val app = context.applicationContext
            RuleEngine.onBoot(app)
            TimeScheduler.rescheduleAll(app)
            com.endroid.latch.engine.AppWatchService.refresh(app)
            LatchMonitorService.refresh(app)
        }
    }
}

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> RuleEngine.onPowerConnected(app, true)
            Intent.ACTION_POWER_DISCONNECTED -> RuleEngine.onPowerConnected(app, false)
            Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> {
                val pct = batteryPercent(context, intent)
                if (pct >= 0) RuleEngine.onBattery(app, pct)
            }
        }
    }

    companion object {
        fun batteryPercent(context: Context, intent: Intent?): Int {
            if (intent != null && intent.hasExtra(BatteryManager.EXTRA_LEVEL)) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (level >= 0) return (level * 100) / scale
            }
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
            return if (level >= 0) (level * 100) / scale else -1
        }
    }
}

class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_HEADSET_PLUG) return
        val state = intent.getIntExtra("state", -1)
        if (state == 1) RuleEngine.onHeadphones(context.applicationContext, true)
        if (state == 0) RuleEngine.onHeadphones(context.applicationContext, false)
    }
}

class ConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION -> {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val wifi = isWifiConnected(cm)
                val ssid = currentSsid(app)
                // edge detection via prefs
                val prefs = app.getSharedPreferences("latch_net", Context.MODE_PRIVATE)
                val was = prefs.getBoolean("wifi", false)
                val now = System.currentTimeMillis()
                val lastTs = prefs.getLong("wifi_ts", 0L)
                if (now - lastTs < 800L) return
                if (wifi != was) {
                    prefs.edit().putBoolean("wifi", wifi).putLong("wifi_ts", now).apply()
                    RuleEngine.onWifi(app, wifi, ssid)
                } else if (wifi && !ssid.isNullOrBlank()) {
                    val lastSsid = prefs.getString("ssid", null)
                    if (ssid != lastSsid) {
                        prefs.edit().putString("ssid", ssid).putLong("wifi_ts", now).apply()
                        RuleEngine.onWifi(app, true, ssid)
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                RuleEngine.onBluetooth(app, true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                RuleEngine.onBluetooth(app, false)
            Intent.ACTION_SCREEN_ON -> RuleEngine.onScreen(app, true)
            Intent.ACTION_SCREEN_OFF -> RuleEngine.onScreen(app, false)
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                RuleEngine.onAirplane(app, on)
            }
            Intent.ACTION_MEDIA_MOUNTED -> RuleEngine.onMediaMounted(app)
        }
    }

    private fun isWifiConnected(cm: ConnectivityManager): Boolean {
        if (Build.VERSION.SDK_INT >= 23) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI &&
            cm.activeNetworkInfo?.isConnected == true
    }

    private fun currentSsid(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            val ssid = info?.ssid
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") null else ssid.trim('"')
        } catch (_: Exception) {
            null
        }
    }
}

class TimeTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_TIME_TICK &&
            intent?.action != Intent.ACTION_TIME_CHANGED &&
            intent?.action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        val cal = Calendar.getInstance()
        RuleEngine.onTimeTick(
            context.applicationContext,
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
        )
    }
}

class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val app = context.applicationContext
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val store = RuleStore(app)
        if (!ruleId.isNullOrBlank()) {
            store.load().find { it.id == ruleId && it.enabled }?.let { RuleEngine.run(app, it) }
        } else {
            val cal = Calendar.getInstance()
            RuleEngine.onTimeTick(app, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
        TimeScheduler.rescheduleAll(app)
    }

    companion object {
        const val ACTION = "com.endroid.latch.DAILY_ALARM"
        const val EXTRA_RULE_ID = "rule_id"
    }
}

/** Plugin API: other apps send broadcasts to fire CUSTOM_BROADCAST rules or run a rule by id. */
class PluginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val app = context.applicationContext
        // The Settings switch "Plugin API" used to only gate outgoing broadcasts.
        if (!com.endroid.latch.data.SettingsStore(app).pluginApiEnabled) return
        when (intent.action) {
            ACTION_FIRE_TRIGGER -> {
                val custom = intent.getStringExtra(EXTRA_TRIGGER)
                if (!custom.isNullOrBlank()) RuleEngine.onCustomBroadcast(app, custom)
            }
            ACTION_RUN_RULE -> {
                val id = intent.getStringExtra(EXTRA_RULE_ID) ?: return
                RuleStore(app).load().find { it.id == id && it.enabled }?.let {
                    RuleEngine.run(app, it)
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE_TRIGGER = "com.endroid.latch.api.FIRE_TRIGGER"
        const val ACTION_RUN_RULE = "com.endroid.latch.api.RUN_RULE"
        const val EXTRA_TRIGGER = "trigger"
        const val EXTRA_RULE_ID = "rule_id"
    }
}

class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TOGGLE) return
        val id = intent.getStringExtra("rule_id") ?: return
        val store = RuleStore(context)
        val rule = store.load().find { it.id == id } ?: return
        store.setEnabled(id, !rule.enabled)
        LatchWidgetProvider.refreshAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.endroid.latch.WIDGET_TOGGLE"
    }
}

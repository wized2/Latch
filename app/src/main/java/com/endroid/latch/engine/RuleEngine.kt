package com.endroid.latch.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.endroid.latch.R
import com.endroid.latch.data.ActionType
import com.endroid.latch.data.RingerModeKind
import com.endroid.latch.data.Rule
import com.endroid.latch.data.RuleStore
import com.endroid.latch.data.SettingsStore
import com.endroid.latch.data.TriggerType
import kotlin.concurrent.thread

object RuleEngine {
    const val CHANNEL_ID = "latch_actions"
    private const val PREFS = "latch_engine"
    private const val TAG = "LatchEngine"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_actions),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_actions_desc) },
            )
        }
    }

    private fun cooldownOk(context: Context, rule: Rule): Boolean {
        if (rule.cooldownSec <= 0) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong("cd_${rule.id}", 0L)
        val now = System.currentTimeMillis()
        if (!RuleLogic.cooldownOk(rule.cooldownSec, last, now)) return false
        prefs.edit().putLong("cd_${rule.id}", now).apply()
        return true
    }

    private fun eligible(context: Context, rule: Rule): Boolean =
        rule.enabled && Conditions.holds(context, rule) && cooldownOk(context, rule)

    /**
     * Battery rules fire when threshold is crossed, or when [force] (monitor start / rule save).
     * Per-rule last-fired state avoids spam while still reacting when the condition becomes true.
     */
    fun onBattery(context: Context, percent: Int, force: Boolean = false) {
        val settings = SettingsStore(context)
        if (settings.automationPaused) return
        val activeProfile = settings.activeProfileId
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = prefs.getInt("bat_pct", -1)
        prefs.edit().putInt("bat_pct", percent).apply()

        RuleStore(context).load().filter { it.enabled }.forEach { rule ->
            if (rule.profileId.isNotBlank() && rule.profileId != "default" && rule.profileId != activeProfile) return@forEach
            val below = when (rule.trigger) {
                TriggerType.BATTERY_BELOW -> true
                TriggerType.BATTERY_ABOVE -> false
                else -> return@forEach
            }
            val threshold = rule.triggerValue
            val latched = prefs.getBoolean("bat_latched_${rule.id}", false)
            val match = if (below) RuleLogic.batteryBelow(percent, threshold)
            else RuleLogic.batteryAbove(percent, threshold)
            if (!match) {
                prefs.edit().putBoolean("bat_latched_${rule.id}", false).apply()
                return@forEach
            }
            if (!RuleLogic.shouldFireBattery(prev, percent, threshold, below, latched, force)) {
                return@forEach
            }
            if (!Conditions.holds(context, rule)) return@forEach
            if (!cooldownOk(context, rule)) return@forEach
            prefs.edit().putBoolean("bat_latched_${rule.id}", true).apply()
            run(context, rule)
        }
    }

    fun evaluateCurrentState(context: Context) {
        val pct = com.endroid.latch.receiver.PowerReceiver.batteryPercent(context, null)
        if (pct >= 0) onBattery(context, pct, force = true)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val wifi = if (android.os.Build.VERSION.SDK_INT >= 23) {
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI
        }
        if (wifi) {
            val ssid = try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                wm.connectionInfo?.ssid?.trim('"')
            } catch (_: Exception) { null }
            onWifi(context, true, ssid)
        }
    }

    fun onPowerConnected(context: Context, connected: Boolean) =
        fire(context, if (connected) TriggerType.CHARGER_CONNECTED else TriggerType.CHARGER_DISCONNECTED)

    fun onHeadphones(context: Context, connected: Boolean) =
        fire(context, if (connected) TriggerType.HEADPHONES_CONNECTED else TriggerType.HEADPHONES_DISCONNECTED)

    fun onBoot(context: Context) = fire(context, TriggerType.BOOT_COMPLETED)

    fun onWifi(context: Context, connected: Boolean, ssid: String?) {
        if (connected) {
            fire(context, TriggerType.WIFI_CONNECTED)
            if (!ssid.isNullOrBlank()) {
                match(context) {
                    it.trigger == TriggerType.WIFI_SSID &&
                        RuleLogic.ssidMatches(ssid, it.triggerParam)
                }
            }
        } else fire(context, TriggerType.WIFI_DISCONNECTED)
    }

    fun onBluetooth(context: Context, connected: Boolean) =
        fire(context, if (connected) TriggerType.BLUETOOTH_CONNECTED else TriggerType.BLUETOOTH_DISCONNECTED)

    fun onScreen(context: Context, on: Boolean) =
        fire(context, if (on) TriggerType.SCREEN_ON else TriggerType.SCREEN_OFF)

    fun onAppOpened(context: Context, packageName: String) =
        match(context) {
            it.trigger == TriggerType.APP_OPENED &&
                it.triggerParam.equals(packageName, ignoreCase = true)
        }

    fun onCustomBroadcast(context: Context, action: String) =
        match(context) { it.trigger == TriggerType.CUSTOM_BROADCAST && it.triggerParam == action }

    fun onAirplane(context: Context, on: Boolean) =
        fire(context, if (on) TriggerType.AIRPLANE_ON else TriggerType.AIRPLANE_OFF)

    fun onMediaMounted(context: Context) = fire(context, TriggerType.MEDIA_MOUNTED)

    fun onTimeTick(context: Context, hour: Int, minute: Int) {
        val nowMin = hour * 60 + minute
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        match(context) {
            when (it.trigger) {
                TriggerType.TIME_DAILY ->
                    it.triggerValue == hour && it.triggerValue2 == minute
                TriggerType.TIME_WINDOW -> {
                    val endH = it.triggerParam.substringBefore(":").toIntOrNull() ?: 0
                    val endM = it.triggerParam.substringAfter(":").toIntOrNull() ?: 0
                    val start = it.triggerValue * 60 + it.triggerValue2
                    val end = endH * 60 + endM
                    // Fire once at window start minute
                    nowMin == start && RuleLogic.inTimeWindow(nowMin, start, end)
                }
                TriggerType.DAY_OF_WEEK -> {
                    // mask in triggerValue; optional hour:minute in value2 as minutes of day (-1 skip)
                    RuleLogic.dayBitSet(it.triggerValue, day) &&
                        (it.triggerValue2 < 0 || it.triggerValue2 == nowMin)
                }
                else -> false
            }
        }
    }

    fun fireType(context: Context, type: TriggerType) =
        match(context) { it.trigger == type }

    fun onPackageEvent(context: Context, type: TriggerType, packageName: String) {
        match(context) {
            it.trigger == type && (
                it.triggerParam.isBlank() ||
                    it.triggerParam.equals(packageName, ignoreCase = true)
            )
        }
    }

    private fun fire(context: Context, type: TriggerType) =
        match(context) { it.trigger == type }

    private fun match(context: Context, pred: (Rule) -> Boolean) {
        val settings = SettingsStore(context)
        if (settings.automationPaused) {
            settings.appendWhy("SKIP all: automation paused")
            return
        }
        val activeProfile = settings.activeProfileId
        val all = RuleStore(context).load()
        for (rule in all) {
            if (!rule.enabled) continue
            if (rule.profileId.isNotBlank() && rule.profileId != "default" && rule.profileId != activeProfile) {
                settings.appendWhy("SKIP ${rule.name}: profile ${rule.profileId} != $activeProfile")
                continue
            }
            if (!pred(rule)) continue
            if (!Conditions.holds(context, rule)) {
                settings.appendWhy("SKIP ${rule.name}: condition not met (${rule.condition}/${rule.condition2})")
                continue
            }
            if (!cooldownOk(context, rule)) {
                settings.appendWhy("SKIP ${rule.name}: cooldown ${rule.cooldownSec}s")
                continue
            }
            try {
                val store = RuleStore(context)
                val now = System.currentTimeMillis()
                store.save(store.load().map {
                    if (it.id == rule.id) it.copy(lastFiredMs = now) else it
                })
            } catch (_: Exception) { }
            if (settings.hapticOnFire) {
                try {
                    @Suppress("DEPRECATION")
                    val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        vib?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib?.vibrate(40)
                    }
                } catch (_: Exception) { }
            }
            settings.appendLog("FIRE ${rule.name}")
            run(context, rule)
        }
    }

    fun run(context: Context, rule: Rule) {
        ensureChannel(context)
        val settings = SettingsStore(context)
        settings.appendLog("RUN ${rule.name}")
        val preferShizuku = settings.preferShizuku
        val allowSu = settings.elevatedShell
        thread(name = "latch-${rule.id.take(6)}") {
            for (action in rule.actions) {
                try {
                    execute(context, rule, action, preferShizuku, allowSu)
                } catch (e: Exception) {
                    Log.w(TAG, "action ${action.type}", e)
                    settings.appendLog("ERR ${action.type}: ${e.message}")
                }
            }
        }
    }

    private fun execute(
        context: Context,
        rule: Rule,
        action: com.endroid.latch.data.RuleAction,
        preferShizuku: Boolean,
        allowSu: Boolean,
    ) {
        when (action.type) {
            ActionType.WAIT_MS -> {
                val ms = action.param.toLongOrNull()?.coerceIn(0, 60_000) ?: 0L
                if (ms > 0) Thread.sleep(ms)
            }
            ActionType.TOAST -> {
                val msg = Variables.expand(context, action.text).ifBlank { rule.name }
                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            ActionType.NOTIFY -> {
                val title = Variables.expand(context, action.title).ifBlank { rule.name }
                val body = Variables.expand(context, action.text).ifBlank { rule.triggerSummary() }
                val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_latch)
                    .setContentTitle(title).setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true).build()
                try {
                    NotificationManagerCompat.from(context)
                        .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
                } catch (_: SecurityException) { }
            }
            ActionType.RINGER_MODE -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                try {
                    am.ringerMode = when (action.ringerMode) {
                        RingerModeKind.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                        RingerModeKind.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                        RingerModeKind.SILENT -> AudioManager.RINGER_MODE_SILENT
                    }
                } catch (_: SecurityException) { }
            }
            ActionType.OPEN_APP -> {
                val pkg = action.param.trim()
                context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            ActionType.OPEN_URL -> {
                val url = Variables.expand(context, action.param.trim().ifBlank { action.text.trim() })
                if (url.isNotEmpty()) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            ActionType.SET_BRIGHTNESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                    Log.w(TAG, "SET_BRIGHTNESS blocked: WRITE_SETTINGS not granted")
                    SettingsStore(context).appendLog("ERR brightness: enable Modify system settings")
                    android.os.Handler(context.mainLooper).post {
                        Toast.makeText(context, "Enable Modify system settings for brightness", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                val pct = action.param.toIntOrNull()?.coerceIn(1, 100) ?: return
                val raw = (pct / 100f * 255).toInt().coerceIn(1, 255)
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        raw,
                    )
                    SettingsStore(context).appendLog("brightness → $pct%")
                } catch (e: Exception) {
                    Log.w(TAG, "brightness failed", e)
                    SettingsStore(context).appendLog("ERR brightness: ${e.message}")
                }
            }
            ActionType.SHELL -> {
                val cmd = Variables.expand(context, action.param.trim().ifBlank { action.text.trim() })
                if (cmd.isEmpty()) return
                Elevated.runShell(cmd, preferShizuku, allowSu)
            }
            ActionType.PLUGIN_BROADCAST -> {
                if (!SettingsStore(context).pluginApiEnabled) return
                val act = action.param.trim().ifBlank { "com.endroid.latch.PLUGIN_EVENT" }
                context.sendBroadcast(
                    Intent(act).putExtra("rule_id", rule.id).putExtra("rule_name", rule.name)
                        .putExtra("text", action.text),
                )
            }
            ActionType.MEDIA_VOLUME -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val pct = action.param.toIntOrNull()?.coerceIn(0, 100) ?: return
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct / 100f * max).toInt(), 0)
            }
            ActionType.VIBRATE -> {
                val ms = action.param.toLongOrNull()?.coerceIn(50, 5000) ?: 200L
                val v = if (Build.VERSION.SDK_INT >= 31) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
            ActionType.CLIPBOARD -> {
                val clipText = Variables.expand(context, action.text)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(
                    android.content.ClipData.newPlainText("latch", clipText.ifBlank { action.param }),
                )
            }
            ActionType.FLASHLIGHT -> {
                val on = action.param.lowercase() != "off"
                try {
                    val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cam.cameraIdList.firstOrNull {
                        cam.getCameraCharacteristics(it)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: return
                    cam.setTorchMode(id, on)
                } catch (_: Exception) { }
            }
            ActionType.WIFI_ENABLE -> {
                // Deprecated API — best effort via shell when elevated
                val enable = action.param.lowercase() != "false" && action.param.lowercase() != "off"
                val cmd = if (enable) "svc wifi enable" else "svc wifi disable"
                Elevated.runShell(cmd, preferShizuku, allowSu)
            }
            ActionType.BLUETOOTH_ENABLE -> {
                val enable = action.param.lowercase() != "false" && action.param.lowercase() != "off"
                val cmd = if (enable) "svc bluetooth enable" else "svc bluetooth disable"
                Elevated.runShell(cmd, preferShizuku, allowSu)
            }
            ActionType.SKIP_TO_NEXT -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                am.dispatchMediaKeyEvent(event)
                am.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT),
                )
            }
            ActionType.FORCE_STOP -> {
                val pkg = action.param.trim().ifBlank { action.text.trim() }
                if (pkg.isNotEmpty()) Elevated.forceStop(pkg, preferShizuku, allowSu)
            }
            ActionType.MOBILE_DATA -> {
                val on = action.param.lowercase() !in setOf("off", "false", "0")
                Elevated.setMobileData(on, preferShizuku, allowSu)
            }
            ActionType.AIRPLANE_MODE -> {
                val on = action.param.lowercase() !in setOf("off", "false", "0")
                Elevated.setAirplane(on, preferShizuku, allowSu)
            }
            ActionType.MEDIA_PREV -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionType.MEDIA_PLAY_PAUSE -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionType.ALARM_VOLUME -> setStreamPct(context, AudioManager.STREAM_ALARM, action.param)
            ActionType.RING_VOLUME -> setStreamPct(context, AudioManager.STREAM_RING, action.param)
            ActionType.DND_MODE -> {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (Build.VERSION.SDK_INT >= 23 && nm.isNotificationPolicyAccessGranted) {
                    val on = action.param.lowercase() !in setOf("off", "false", "0")
                    nm.setInterruptionFilter(
                        if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                        else NotificationManager.INTERRUPTION_FILTER_ALL,
                    )
                } else {
                    SettingsStore(context).appendLog("ERR DND: grant Do Not Disturb access")
                }
            }
            ActionType.SPEAK_TTS -> {
                val say = action.text.ifBlank { action.param }.ifBlank { rule.name }
                android.os.Handler(context.mainLooper).post {
                    try {
                        val tts = android.speech.tts.TextToSpeech(context) { st ->
                            /* engine ready async; best-effort */
                        }
                        tts.speak(say, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "latch")
                    } catch (_: Exception) { }
                }
            }
            ActionType.OPEN_APP_SETTINGS -> {
                val pkg = action.param.trim().ifBlank { context.packageName }
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            }
            ActionType.OPEN_SYSTEM_SETTINGS -> {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            ActionType.HTTP_GET -> {
                val url = action.param.trim().ifBlank { action.text.trim() }
                if (url.startsWith("http")) {
                    thread {
                        try {
                            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                            c.connectTimeout = 8000
                            c.readTimeout = 8000
                            c.requestMethod = "GET"
                            val code = c.responseCode
                            SettingsStore(context).appendLog("HTTP $code $url")
                            c.disconnect()
                        } catch (e: Exception) {
                            SettingsStore(context).appendLog("HTTP ERR ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun mediaKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun setStreamPct(context: Context, stream: Int, param: String) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val pct = param.toIntOrNull()?.coerceIn(0, 100) ?: return
        am.setStreamVolume(stream, (pct / 100f * max).toInt(), 0)
    }

    /** Settings self-test: run pure logic checks + permission snapshot. */
    fun selfTest(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("Latch self-test")
        sb.appendLine("batteryBelow(50,98)=${RuleLogic.batteryBelow(50, 98)}")
        sb.appendLine("shouldFire force=${RuleLogic.shouldFireBattery(50, 50, 98, true, true, true)}")
        sb.appendLine("brightness30=${RuleLogic.brightnessRaw(30)}")
        sb.appendLine("shizuku=${Elevated.shizukuAvailable()} perm=${Elevated.shizukuPermissionGranted()}")
        sb.appendLine("su=${Elevated.hasSu()}")
        val rules = RuleStore(context).load()
        sb.appendLine("rules=${rules.size} enabled=${rules.count { it.enabled }}")
        val pct = com.endroid.latch.receiver.PowerReceiver.batteryPercent(context, null)
        sb.appendLine("batteryNow=$pct")
        return sb.toString()
    }
}


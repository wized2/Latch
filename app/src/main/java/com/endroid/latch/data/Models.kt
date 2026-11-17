package com.endroid.latch.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TriggerType {
    BATTERY_BELOW, BATTERY_ABOVE,
    CHARGER_CONNECTED, CHARGER_DISCONNECTED,
    HEADPHONES_CONNECTED, HEADPHONES_DISCONNECTED,
    BOOT_COMPLETED, TIME_DAILY,
    WIFI_CONNECTED, WIFI_DISCONNECTED, WIFI_SSID,
    BLUETOOTH_CONNECTED, BLUETOOTH_DISCONNECTED,
    APP_OPENED, SCREEN_ON, SCREEN_OFF,
    CUSTOM_BROADCAST,
    AIRPLANE_ON, AIRPLANE_OFF,
    MEDIA_MOUNTED,
    TIME_WINDOW,
    DAY_OF_WEEK,
    USER_PRESENT,
    POWER_SAVE_ON, POWER_SAVE_OFF,
    PACKAGE_ADDED, PACKAGE_REMOVED,
    STORAGE_LOW,
    DOCK_EVENT,
    SHUTDOWN,
}

enum class ActionType {
    NOTIFY, RINGER_MODE, TOAST,
    OPEN_APP, OPEN_URL, SET_BRIGHTNESS,
    SHELL, WAIT_MS, PLUGIN_BROADCAST,
    MEDIA_VOLUME, VIBRATE, CLIPBOARD, FLASHLIGHT,
    WIFI_ENABLE, BLUETOOTH_ENABLE, SKIP_TO_NEXT,
    FORCE_STOP, MOBILE_DATA, AIRPLANE_MODE,
    MEDIA_PREV, MEDIA_PLAY_PAUSE,
    ALARM_VOLUME, RING_VOLUME,
    DND_MODE,
    SPEAK_TTS,
    OPEN_APP_SETTINGS,
    OPEN_SYSTEM_SETTINGS,
    HTTP_GET,
}

enum class RingerModeKind { NORMAL, VIBRATE, SILENT }

enum class ConditionType {
    NONE, WIFI_ON, WIFI_OFF, CHARGING, NOT_CHARGING,
    SCREEN_ON, SCREEN_OFF, BATTERY_BELOW, BATTERY_ABOVE,
    DAY_WEEKDAY, DAY_WEEKEND, POWER_SAVE_ON, POWER_SAVE_OFF,
}

data class RuleAction(
    val type: ActionType,
    val text: String = "",
    val title: String = "",
    val ringerMode: RingerModeKind = RingerModeKind.NORMAL,
    val param: String = "",
) {
    fun summary(): String = when (type) {
        ActionType.NOTIFY -> "Notify: ${title.ifBlank { text }.ifBlank { "…" }}"
        ActionType.RINGER_MODE -> "Ringer → ${ringerMode.name.lowercase()}"
        ActionType.TOAST -> "Toast: ${text.ifBlank { "…" }}"
        ActionType.OPEN_APP -> "Open: ${param.ifBlank { "?" }}"
        ActionType.OPEN_URL -> "URL ${param.take(24)}"
        ActionType.SET_BRIGHTNESS -> "Brightness ${param}%"
        ActionType.SHELL -> "Shell: ${param.take(28)}"
        ActionType.WAIT_MS -> "Wait ${param}ms"
        ActionType.PLUGIN_BROADCAST -> "Plugin: ${param.ifBlank { "event" }}"
        ActionType.MEDIA_VOLUME -> "Media vol ${param}%"
        ActionType.VIBRATE -> "Vibrate ${param}ms"
        ActionType.CLIPBOARD -> "Clipboard"
        ActionType.FLASHLIGHT -> "Flash ${param.ifBlank { "on" }}"
        ActionType.WIFI_ENABLE -> "Wi‑Fi ${param.ifBlank { "on" }}"
        ActionType.BLUETOOTH_ENABLE -> "BT ${param.ifBlank { "on" }}"
        ActionType.SKIP_TO_NEXT -> "Media next"
        ActionType.FORCE_STOP -> "Force-stop ${param.ifBlank { "?" }}"
        ActionType.MOBILE_DATA -> "Data ${param.ifBlank { "on" }}"
        ActionType.AIRPLANE_MODE -> "Airplane ${param.ifBlank { "on" }}"
        ActionType.MEDIA_PREV -> "Media previous"
        ActionType.MEDIA_PLAY_PAUSE -> "Play/Pause"
        ActionType.ALARM_VOLUME -> "Alarm vol ${param}%"
        ActionType.RING_VOLUME -> "Ring vol ${param}%"
        ActionType.DND_MODE -> "DND ${param.ifBlank { "on" }}"
        ActionType.SPEAK_TTS -> "Speak: ${text.ifBlank { param }.take(24)}"
        ActionType.OPEN_APP_SETTINGS -> "App settings ${param.ifBlank { "self" }}"
        ActionType.OPEN_SYSTEM_SETTINGS -> "System settings"
        ActionType.HTTP_GET -> "HTTP GET ${param.take(24)}"
    }

    fun toJson() = JSONObject()
        .put("type", type.name).put("text", text).put("title", title)
        .put("ringerMode", ringerMode.name).put("param", param)

    companion object {
        fun fromJson(o: JSONObject) = RuleAction(
            type = ActionType.valueOf(o.getString("type")),
            text = o.optString("text"), title = o.optString("title"),
            ringerMode = runCatching { RingerModeKind.valueOf(o.optString("ringerMode", "NORMAL")) }
                .getOrDefault(RingerModeKind.NORMAL),
            param = o.optString("param"),
        )
    }
}

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val trigger: TriggerType,
    val triggerValue: Int = 20,
    val triggerValue2: Int = 0,
    val triggerParam: String = "",
    val condition: ConditionType = ConditionType.NONE,
    val conditionValue: Int = 0,
    val actions: List<RuleAction> = emptyList(),
    val cooldownSec: Int = 0,
    val notes: String = "",
    val tag: String = "",
    val profileId: String = "default",
    val lastFiredMs: Long = 0L,
    val conditionMode: String = "AND", // AND | OR when condition2 used
    val condition2: ConditionType = ConditionType.NONE,
) {
    fun triggerSummary(): String {
        val base = when (trigger) {
            TriggerType.BATTERY_BELOW -> "Battery ≤ $triggerValue%"
            TriggerType.BATTERY_ABOVE -> "Battery ≥ $triggerValue%"
            TriggerType.CHARGER_CONNECTED -> "Charger in"
            TriggerType.CHARGER_DISCONNECTED -> "Charger out"
            TriggerType.HEADPHONES_CONNECTED -> "Headphones in"
            TriggerType.HEADPHONES_DISCONNECTED -> "Headphones out"
            TriggerType.BOOT_COMPLETED -> "Boot"
            TriggerType.TIME_DAILY -> "%02d:%02d daily".format(triggerValue, triggerValue2)
            TriggerType.WIFI_CONNECTED -> "Wi‑Fi on"
            TriggerType.WIFI_DISCONNECTED -> "Wi‑Fi off"
            TriggerType.WIFI_SSID -> "SSID ${triggerParam.ifBlank { "?" }}"
            TriggerType.BLUETOOTH_CONNECTED -> "BT connected"
            TriggerType.BLUETOOTH_DISCONNECTED -> "BT disconnected"
            TriggerType.APP_OPENED -> "App ${triggerParam.ifBlank { "?" }}"
            TriggerType.SCREEN_ON -> "Screen on"
            TriggerType.SCREEN_OFF -> "Screen off"
            TriggerType.CUSTOM_BROADCAST -> "Broadcast ${triggerParam.ifBlank { "?" }}"
            TriggerType.AIRPLANE_ON -> "Airplane on"
            TriggerType.AIRPLANE_OFF -> "Airplane off"
            TriggerType.MEDIA_MOUNTED -> "Media mounted"
            TriggerType.TIME_WINDOW -> "Window %02d:%02d–%s".format(triggerValue, triggerValue2, triggerParam.ifBlank { "?" })
            TriggerType.DAY_OF_WEEK -> com.endroid.latch.engine.RuleLogic.dayNames(triggerValue) +
                if (triggerValue2 >= 0) " at %02d:%02d".format(triggerValue2 / 60, triggerValue2 % 60) else ""
            TriggerType.USER_PRESENT -> "User unlocked"
            TriggerType.POWER_SAVE_ON -> "Battery saver on"
            TriggerType.POWER_SAVE_OFF -> "Battery saver off"
            TriggerType.PACKAGE_ADDED -> "App installed ${triggerParam.ifBlank { "any" }}"
            TriggerType.PACKAGE_REMOVED -> "App removed ${triggerParam.ifBlank { "any" }}"
            TriggerType.STORAGE_LOW -> "Storage low"
            TriggerType.DOCK_EVENT -> "Dock"
            TriggerType.SHUTDOWN -> "Shutdown"
        }
        return if (condition == ConditionType.NONE) base else "$base + ${condition.name.lowercase()}"
    }

    fun toJson() = JSONObject()
        .put("id", id).put("name", name).put("enabled", enabled)
        .put("trigger", trigger.name)
        .put("triggerValue", triggerValue).put("triggerValue2", triggerValue2)
        .put("triggerParam", triggerParam)
        .put("condition", condition.name).put("conditionValue", conditionValue)
        .put("cooldownSec", cooldownSec).put("notes", notes).put("tag", tag)
        .put("profileId", profileId).put("lastFiredMs", lastFiredMs)
        .put("conditionMode", conditionMode).put("condition2", condition2.name)
        .put("actions", JSONArray().also { a -> actions.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Rule {
            val acts = mutableListOf<RuleAction>()
            o.optJSONArray("actions")?.let { arr ->
                for (i in 0 until arr.length()) acts += RuleAction.fromJson(arr.getJSONObject(i))
            }
            return Rule(
                id = o.getString("id"), name = o.getString("name"),
                enabled = o.optBoolean("enabled", true),
                trigger = TriggerType.valueOf(o.getString("trigger")),
                triggerValue = o.optInt("triggerValue", 20),
                triggerValue2 = o.optInt("triggerValue2", 0),
                triggerParam = o.optString("triggerParam"),
                condition = runCatching { ConditionType.valueOf(o.optString("condition", "NONE")) }
                    .getOrDefault(ConditionType.NONE),
                conditionValue = o.optInt("conditionValue", 0),
                actions = acts,
                cooldownSec = o.optInt("cooldownSec", 0),
                notes = o.optString("notes"), tag = o.optString("tag"),
                profileId = o.optString("profileId", "default"),
                lastFiredMs = o.optLong("lastFiredMs", 0L),
                conditionMode = o.optString("conditionMode", "AND"),
                condition2 = runCatching { ConditionType.valueOf(o.optString("condition2", "NONE")) }
                    .getOrDefault(ConditionType.NONE),
            )
        }
    }
}

package com.endroid.latch.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.endroid.latch.data.RuleStore
import com.endroid.latch.data.TriggerType

/**
 * Real-time APP_OPENED detection via TYPE_WINDOW_STATE_CHANGED.
 * Preferred over UsageStats polling (AppWatchService fallback).
 */
class LatchAccessibilityService : AccessibilityService() {
    private var lastPkg: String? = null
    private var lastAt = 0L

    override fun onServiceConnected() {
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        } ?: AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return
        // Ignore our own UI noise
        if (pkg == packageName) return
        val needs = RuleStore(this).load().any {
            it.enabled && it.trigger == TriggerType.APP_OPENED && it.triggerParam.isNotBlank()
        }
        if (!needs) return
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < 400L) return
        lastPkg = pkg
        lastAt = now
        RuleEngine.onAppOpened(applicationContext, pkg)
    }

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${LatchAccessibilityService::class.java.name}"
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
                enabled.split(':').any { it.equals(expected, ignoreCase = true) }
            } catch (_: Exception) {
                false
            }
        }
    }
}

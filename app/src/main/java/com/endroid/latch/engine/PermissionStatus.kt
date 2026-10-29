package com.endroid.latch.engine

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermRow(
    val id: String,
    val title: String,
    val detail: String,
    val granted: Boolean,
    val actionLabel: String, // "Enabled" or "Enable"
)

object PermissionStatus {
    fun all(context: Context): List<PermRow> {
        val rows = mutableListOf<PermRow>()
        rows += notif(context)
        rows += accessibility(context)
        rows += usage(context)
        rows += writeSettings(context)
        rows += exactAlarm(context)
        rows += bluetooth(context)
        rows += location(context)
        rows += shizuku(context)
        rows += root()
        return rows
    }

    private fun notif(context: Context): PermRow {
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return PermRow("notif", "Notifications", "Rule notification actions", ok, if (ok) "Enabled" else "Enable")
    }

    private fun usage(context: Context): PermRow {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        val ok = mode == AppOpsManager.MODE_ALLOWED
        return PermRow("usage", "Usage access", "Fallback app-opened if Accessibility is off", ok, if (ok) "Enabled" else "Enable")
    }

    private fun writeSettings(context: Context): PermRow {
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
        return PermRow("write_settings", "Modify system settings", "Brightness actions", ok, if (ok) "Enabled" else "Enable")
    }

    private fun exactAlarm(context: Context): PermRow {
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(android.app.AlarmManager::class.java)
            am?.canScheduleExactAlarms() == true
        } else true
        return PermRow("exact_alarm", "Exact alarms", "Daily time triggers", ok, if (ok) "Enabled" else "Enable")
    }

    private fun bluetooth(context: Context): PermRow {
        val ok = if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return PermRow("bt", "Bluetooth", "BT connect triggers / actions", ok, if (ok) "Enabled" else "Enable")
    }

    private fun location(context: Context): PermRow {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val ok = fine || coarse
        return PermRow("location", "Location", "Required on many devices to read Wi‑Fi SSID", ok, if (ok) "Enabled" else "Enable")
    }

    private fun accessibility(context: Context): PermRow {
        val ok = LatchAccessibilityService.isEnabled(context)
        return PermRow(
            "accessibility",
            "Accessibility (app open)",
            if (ok) "Instant APP_OPENED triggers" else "Recommended for App opened rules",
            ok,
            if (ok) "Enabled" else "Enable",
        )
    }

    private fun shizuku(context: Context): PermRow {
        val avail = Elevated.shizukuAvailable()
        val ok = Elevated.shizukuPermissionGranted()
        val detail = when {
            ok -> "Connected & authorized"
            avail -> "Running — needs authorization"
            else -> "Not running (install/start Shizuku)"
        }
        return PermRow("shizuku", "Shizuku", detail, ok, if (ok) "Enabled" else "Enable")
    }

    private fun root(): PermRow {
        val ok = Elevated.hasSu()
        return PermRow("root", "Root (su)", if (ok) "su binary available" else "Not found", ok, if (ok) "Enabled" else "Unavailable")
    }
}

package com.endroid.latch.engine

/**
 * Pure rule matching helpers — no Android deps so JVM unit tests can cover them.
 */
object RuleLogic {
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /**
     * True only for a well-formed Android package name. Anything else must never be
     * placed on a shell command line (force-stop builds "am force-stop <pkg>").
     */
    fun isValidPackageName(name: String?): Boolean =
        name != null && name.length <= 255 && PACKAGE_NAME.matches(name)

    /** Only plain http(s) URLs may be requested by the HTTP GET action. */
    fun isHttpUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) &&
            u.length > 8 && u.none { it.isWhitespace() }
    }

    /** Host only (no path/query) so secrets in a URL never reach the on-device log. */
    fun hostForLog(url: String): String = try {
        java.net.URI(url.trim()).host ?: "?"
    } catch (_: Exception) {
        "?"
    }

    fun batteryBelow(percent: Int, threshold: Int): Boolean = percent <= threshold
    fun batteryAbove(percent: Int, threshold: Int): Boolean = percent >= threshold

    /**
     * True when [cooldownSec] has elapsed since [lastFiredMs] at [nowMs].
     * A non-positive cooldown always allows firing.
     */
    fun cooldownOk(cooldownSec: Int, lastFiredMs: Long, nowMs: Long): Boolean {
        if (cooldownSec <= 0) return true
        return nowMs - lastFiredMs >= cooldownSec * 1000L
    }

    /**
     * Fire when force, first time condition is true (!latched), or threshold was crossed
     * since [prevPercent] (-1 = unknown).
     */
    fun shouldFireBattery(
        prevPercent: Int,
        percent: Int,
        threshold: Int,
        below: Boolean,
        latched: Boolean,
        force: Boolean,
    ): Boolean {
        val match = if (below) batteryBelow(percent, threshold) else batteryAbove(percent, threshold)
        if (!match) return false
        if (force) return true
        if (!latched) return true
        if (prevPercent < 0) return true
        val crossed = if (below) prevPercent > threshold else prevPercent < threshold
        return crossed
    }

    fun ssidMatches(current: String?, target: String): Boolean {
        if (target.isBlank() || current.isNullOrBlank()) return false
        return current.trim('"').equals(target.trim('"'), ignoreCase = true)
    }

    /** Minutes since midnight. Window can wrap past midnight. */
    fun inTimeWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean {
        return if (startMin <= endMin) nowMin in startMin..endMin
        else nowMin >= startMin || nowMin <= endMin
    }

    fun dayBitSet(mask: Int, dayOfWeekCalendar: Int): Boolean {
        // Calendar: SUNDAY=1 … SATURDAY=7 → bit 0..6
        val bit = 1 shl (dayOfWeekCalendar - 1)
        return (mask and bit) != 0
    }

    fun clampPercent(v: Int): Int = v.coerceIn(0, 100)
    fun brightnessRaw(percent: Int): Int {
        val p = clampPercent(percent)
        if (p <= 0) return 1
        return (p * 255 / 100).coerceIn(1, 255)
    }
}

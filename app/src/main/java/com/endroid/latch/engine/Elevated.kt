package com.endroid.latch.engine

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object Elevated {
    private const val TAG = "LatchElevated"
    private val newProcessMethod = AtomicReference<Method?>()

    @Volatile private var suCache: Pair<Boolean, Long>? = null
    private const val SU_CACHE_MS = 30_000L

    /** su probe with a hard timeout (a su that waits for a prompt used to hang the caller). */
    fun hasSu(): Boolean {
        val now = System.currentTimeMillis()
        suCache?.let { (ok, at) -> if (now - at < SU_CACHE_MS) return ok }
        val ok = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
            val r = drain(p, 3)
            r.first == 0 && r.second.contains("ok")
        } catch (_: Exception) {
            false
        }
        suCache = ok to now
        return ok
    }

    fun shizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun shizukuPermissionGranted(): Boolean = try {
        shizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestShizukuPermission(requestCode: Int = 9001) {
        try {
            if (!shizukuPermissionGranted()) Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            Log.w(TAG, "shizuku request", e)
        }
    }

    /** Prefer calling once from Application/MainActivity. */
    fun attachListeners(onChange: () -> Unit = {}) {
        try {
            Shizuku.addBinderReceivedListenerSticky {
                Log.i(TAG, "Shizuku binder received")
                onChange()
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder dead")
                onChange()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "attachListeners", e)
        }
    }

    private fun resolveNewProcess(): Method? {
        newProcessMethod.get()?.let { return it }
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            m.isAccessible = true
            newProcessMethod.set(m)
            m
        } catch (e: Throwable) {
            Log.w(TAG, "resolve newProcess", e)
            null
        }
    }

    private fun shizukuProcess(argv: Array<String>): Process? {
        val m = resolveNewProcess() ?: return null
        return try {
            val p = m.invoke(null, argv, null, null)
            when (p) {
                is Process -> p
                null -> null
                else -> if (Process::class.java.isInstance(p)) p as Process else null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "shizukuProcess", e)
            null
        }
    }

    /**
     * Reads output on a helper thread so [timeoutSec] is real: the old version read the
     * stream to EOF first, so a hung process blocked the caller forever.
     */
    private fun drain(p: Process, timeoutSec: Long): Pair<Int, String> {
        val out = StringBuffer()
        val reader = Thread {
            try {
                p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(1024)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        if (out.length < 4000) out.append(buf, 0, n)
                    }
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        val finished = try {
            p.waitFor(timeoutSec, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (!finished) {
            p.destroyForcibly()
            reader.join(500)
            return -1 to out.toString().take(4000)
        }
        reader.join(1000)
        return p.exitValue() to out.toString().take(4000)
    }

    /**
     * Elevated shell: open Shizuku process only for this command, then drain/exit.
     * Do not keep a long-lived Shizuku user service bound from the monitor.
     */
    fun runShell(cmd: String, preferShizuku: Boolean, allowSu: Boolean): Pair<Int, String> {
        if (preferShizuku && shizukuPermissionGranted()) {
            for (argv in listOf(
                arrayOf("sh", "-c", cmd),
                arrayOf("/system/bin/sh", "-c", cmd),
            )) {
                val p = shizukuProcess(argv)
                if (p != null) {
                    val r = drain(p, 15)
                    if (r.first != -1 || r.second.isNotBlank()) return r
                }
            }
        }
        if (allowSu && hasSu()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                return drain(p, 15)
            } catch (e: Exception) {
                Log.w(TAG, "su", e)
            }
        }
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            drain(p, 15)
        } catch (e: Exception) {
            -1 to (e.message ?: "exec failed")
        }
    }

    /** Common elevated helpers used by actions. */
    fun setAirplane(enable: Boolean, preferShizuku: Boolean, allowSu: Boolean) =
        runShell(
            if (enable) "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
            else "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
            preferShizuku, allowSu,
        )

    fun setMobileData(enable: Boolean, preferShizuku: Boolean, allowSu: Boolean) =
        runShell(if (enable) "svc data enable" else "svc data disable", preferShizuku, allowSu)

    fun forceStop(packageName: String, preferShizuku: Boolean, allowSu: Boolean): Pair<Int, String> {
        val pkg = packageName.trim()
        if (!RuleLogic.isValidPackageName(pkg)) return -1 to "invalid package name"
        return runShell("am force-stop $pkg", preferShizuku, allowSu)
    }
}

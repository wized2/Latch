/** Exact daily windows via AlarmManager.setExactAndAllowWhileIdle (SCHEDULE_EXACT_ALARM). */
package com.endroid.latch.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.endroid.latch.data.RuleStore
import com.endroid.latch.data.TriggerType
import com.endroid.latch.receiver.DailyAlarmReceiver
import java.util.Calendar

object TimeScheduler {
    fun rescheduleAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val rules = RuleStore(context).load().filter {
            it.enabled && it.trigger == TriggerType.TIME_DAILY
        }
        // Cancel a reasonable range of request codes then set active ones
        for (code in 0 until 64) {
            val pi = pending(context, code)
            am.cancel(pi)
        }
        rules.forEachIndexed { index, rule ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, rule.triggerValue.coerceIn(0, 23))
                set(Calendar.MINUTE, rule.triggerValue2.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            val pi = pending(context, index, rule.id)
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pi,
                )
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pi,
                )
            }
        }
    }

    private fun pending(context: Context, code: Int, ruleId: String = ""): PendingIntent {
        val intent = Intent(context, DailyAlarmReceiver::class.java).apply {
            action = DailyAlarmReceiver.ACTION
            putExtra(DailyAlarmReceiver.EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

package io.github.rosymaro.familyweekly.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.rosymaro.familyweekly.alarm.planner.AlarmPlanner
import io.github.rosymaro.familyweekly.alarm.planner.PlannedAlarm
import io.github.rosymaro.familyweekly.widget.runAsync

/** 알림 울리기 / 다시 알림 / 확인 / 하루 한 번 다시 계산. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { app ->
            val alarm = intent.getStringExtra(EXTRA_ALARM)?.let {
                runCatching { AlarmScheduler.json.decodeFromString(PlannedAlarm.serializer(), it) }.getOrNull()
            }
            when (intent.action) {
                ACTION_FIRE -> {
                    if (alarm != null && (alarm.key.endsWith(AlarmScheduler.SNOOZE_SUFFIX) || stillPlanned(app, alarm))) {
                        app.notifier.show(alarm)
                    } else {
                        Log.i(TAG, "skip stale alarm ${alarm?.key}")
                    }
                    app.alarms.reschedule()
                }
                ACTION_SNOOZE -> alarm?.let {
                    app.notifier.cancel(it.key)
                    app.alarms.snooze(it)
                }
                ACTION_DISMISS -> alarm?.let { app.notifier.cancel(it.key) }
                ACTION_REFRESH -> app.alarms.reschedule()
            }
        }
    }

    /**
     * 등록한 뒤 일정이 바뀌어 해제가 늦었을 수도 있으니, 울리기 직전에 지금 데이터로 한 번 더 확인
     * (취소된 수업 알림이 울리지 않게).
     */
    private fun stillPlanned(app: io.github.rosymaro.familyweekly.FamilyWeeklyApp, a: PlannedAlarm): Boolean {
        val data = app.store.data.value
        val from = a.atTime.minusMinutes(2)
        return AlarmPlanner.plan(data, app.holidays.calendar(data.customHolidays), from, horizonHours = 1).any { it.key == a.key }
    }

    companion object {
        private const val TAG = "FamilyWeeklyAlarm"
        const val ACTION_FIRE = "io.github.rosymaro.familyweekly.ALARM_FIRE"
        const val ACTION_SNOOZE = "io.github.rosymaro.familyweekly.ALARM_SNOOZE"
        const val ACTION_DISMISS = "io.github.rosymaro.familyweekly.ALARM_DISMISS"
        const val ACTION_REFRESH = "io.github.rosymaro.familyweekly.ALARM_REFRESH"
        const val EXTRA_ALARM = "alarm"
    }
}

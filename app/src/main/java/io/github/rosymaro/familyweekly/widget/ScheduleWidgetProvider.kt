package io.github.rosymaro.familyweekly.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import kotlinx.coroutines.launch

/** 받은 일을 앱 범위 코루틴에서 처리 (BroadcastReceiver는 10초 안에 끝내야 해서 goAsync). */
internal fun BroadcastReceiver.runAsync(context: Context, work: (FamilyWeeklyApp) -> Unit) {
    val app = context.applicationContext as FamilyWeeklyApp
    val pending = goAsync()
    app.appScope.launch {
        try {
            work(app)
        } finally {
            pending.finish()
        }
    }
}

/** 홈 화면 위젯 (스케줄 하나 = 위젯 하나). 그림은 WidgetUpdater가 그린다. */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { app ->
            appWidgetIds.forEach { app.widgets.update(it, force = true) }
            app.widgets.scheduleTicks()
        }
    }

    /** 크기를 바꾸면 그 크기(작게/보통/크게)에 맞춰 다시 그림 */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        runAsync(context) { app -> app.widgets.update(appWidgetId, force = true) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        runAsync(context) { app -> app.store.update { WidgetLogic.remove(it, appWidgetIds.toList()) } }
    }

    override fun onEnabled(context: Context) {
        runAsync(context) { app -> app.widgets.scheduleTicks() }
    }

    override fun onDisabled(context: Context) {
        runAsync(context) { app -> app.widgets.cancelTicks() }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        // 백업 복원으로 위젯 id가 바뀌면 연결도 옮긴다
        runAsync(context) { app ->
            app.store.update { data ->
                var d = data
                oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
                    WidgetLogic.binding(d, old)?.let { b -> d = WidgetLogic.bind(WidgetLogic.remove(d, listOf(old)), b.copy(appWidgetId = new)) }
                }
                d
            }
            app.widgets.updateAll()
        }
    }
}

/**
 * 시스템 일 → 위젯 다시 그리기·알림 다시 등록: 5분 틱(현재 시간선·날짜 바뀜), 시간/시간대 변경, 부팅, 앱 업데이트,
 * 앱에서 "홈 화면에 위젯 추가"로 고정한 위젯의 연결.
 */
class WidgetEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PINNED -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID || scheduleId == null) return
                runAsync(context) { app ->
                    app.store.update { WidgetLogic.bind(it, WidgetBinding(id, scheduleId)) }
                    app.widgets.update(id)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> runAsync(context) { app ->
                app.widgets.pruneBindings()
                app.widgets.updateAll()
                app.alarms.rescheduleAll() // 부팅하면 AlarmManager 등록이 모두 사라짐
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> runAsync(context) { app ->
                app.widgets.updateAll()
                app.alarms.rescheduleAll()
            }
            ACTION_EXACT_ALARM_PERMISSION -> runAsync(context) { app -> app.alarms.rescheduleAll() }
            else -> runAsync(context) { app -> app.widgets.updateAll() } // 5분 틱
        }
    }

    companion object {
        const val ACTION_TICK = "io.github.rosymaro.familyweekly.WIDGET_TICK"
        const val ACTION_PINNED = "io.github.rosymaro.familyweekly.WIDGET_PINNED"
        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+) */
        const val ACTION_EXACT_ALARM_PERMISSION = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
    }
}

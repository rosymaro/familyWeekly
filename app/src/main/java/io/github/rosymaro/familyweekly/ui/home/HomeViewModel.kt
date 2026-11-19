package io.github.rosymaro.familyweekly.ui.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.widget.ScheduleWidgetProvider
import io.github.rosymaro.familyweekly.widget.WidgetEventsReceiver
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.Orphans
import io.github.rosymaro.familyweekly.domain.ScheduleOps
import io.github.rosymaro.familyweekly.domain.mapSchedule
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory

class HomeViewModel(app: FamilyWeeklyApp) : StoreViewModel(app) {

    val loadIssue = app.store.loadIssue
    val presets = app.presets

    /** 공휴일을 새로 받으면 바뀜 → 화면이 다시 계산 */
    val holidayUpdates = app.holidays.updates

    fun holidays() = app.holidays.calendar(data.value.customHolidays)

    fun clearLoadIssue() = app.store.clearLoadIssue()

    fun add(name: String, emoji: String?, days: DaysMode) = update {
        ScheduleOps.add(it, name, days, emoji = emoji?.trim()?.ifEmpty { null }).first
    }

    fun rename(id: String, name: String) = update { ScheduleOps.rename(it, id, name) }

    fun duplicate(id: String, name: String, attach: Boolean) = update {
        ScheduleOps.duplicate(it, id, name, attach)?.first ?: it
    }

    fun orphansIfDeleted(id: String): Orphans = ScheduleOps.orphansIfDeleted(data.value, id)

    fun delete(id: String, removeOrphans: Boolean) = update { ScheduleOps.delete(it, id, removeOrphans) }

    fun move(id: String, toIndex: Int) = update { ScheduleOps.move(it, id, toIndex) }

    fun setTheme(id: String, themeId: String) = update { it.mapSchedule(id) { s -> s.copy(themeId = themeId) } }

    /**
     * 홈 화면에 이 스케줄 위젯 고정 (Android 8+, 런처가 지원할 때). 추가되면 WidgetEventsReceiver가 연결한다.
     * 지원하지 않으면 false → 화면에서 직접 추가 방법을 안내.
     */
    fun pinWidget(scheduleId: String): Boolean {
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            app,
            scheduleId.hashCode(),
            Intent(app, WidgetEventsReceiver::class.java)
                .setAction(WidgetEventsReceiver.ACTION_PINNED)
                .putExtra(WidgetEventsReceiver.EXTRA_SCHEDULE_ID, scheduleId),
            // 시스템이 위젯 id를 덧붙여야 해서 MUTABLE
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        return manager.requestPinAppWidget(ComponentName(app, ScheduleWidgetProvider::class.java), null, callback)
    }

    fun toggleAlarms(id: String) = update { it.mapSchedule(id) { s -> s.copy(alarmsEnabled = !s.alarmsEnabled) } }

    companion object {
        val Factory = appViewModelFactory { app, _ -> HomeViewModel(app) }
    }
}

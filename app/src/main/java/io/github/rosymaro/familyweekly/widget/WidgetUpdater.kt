package io.github.rosymaro.familyweekly.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.MainActivity
import io.github.rosymaro.familyweekly.R
import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.TodaySummary
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.render.WeekPainter
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import java.time.LocalDateTime

/**
 * 위젯 그리기 + 갱신 예약. 그림은 앱과 같은 WeekLayout → WeekPainter (DESIGN.md 0절).
 * 메인 스레드가 아닌 곳에서 부른다 (FamilyWeeklyApp.appScope).
 */
class WidgetUpdater(private val app: FamilyWeeklyApp) {

    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(app)
    private val painter = WeekPainter()

    fun allIds(): IntArray = manager.getAppWidgetIds(ComponentName(app, ScheduleWidgetProvider::class.java))

    @Synchronized
    fun updateAll() {
        val ids = allIds()
        ids.forEach(::update)
        if (ids.isEmpty()) cancelTicks() else scheduleTicks()
    }

    @Synchronized
    fun update(appWidgetId: Int) {
        try {
            val views = render(appWidgetId, densityFactor = 1f)
            try {
                manager.updateAppWidget(appWidgetId, views)
            } catch (e: RuntimeException) {
                // 런처로 보내는 그림이 너무 크면 (기기마다 한도가 다름) 해상도를 낮춰 한 번 더
                Log.w(TAG, "widget $appWidgetId too large, retrying smaller", e)
                manager.updateAppWidget(appWidgetId, render(appWidgetId, densityFactor = 0.6f))
            }
        } catch (e: Exception) {
            Log.e(TAG, "widget $appWidgetId render failed", e)
        }
    }

    private fun render(appWidgetId: Int, densityFactor: Float): RemoteViews {
        val data = app.store.data.value
        val binding = WidgetLogic.binding(data, appWidgetId)
        val schedule = binding?.scheduleId?.let { data.schedule(it) }

        val res = app.resources
        val opts = manager.getAppWidgetOptions(appWidgetId)
        val landscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val (wDp, hDp) = WidgetLogic.currentSizeDp(
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            landscape,
        )
        val dm = res.displayMetrics
        val systemRadiusDp = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { res.getDimension(android.R.dimen.system_app_widget_background_radius) / dm.density }.getOrNull()
        } else null
        val spec = WidgetLogic.renderSpec(
            widthDp = wDp,
            heightDp = hDp,
            density = dm.density * densityFactor,
            fontScale = res.configuration.fontScale,
            maxBitmapBytes = WidgetLogic.maxBitmapBytes(dm.widthPixels, dm.heightPixels),
            systemCornerRadiusDp = systemRadiusDp,
        )
        val systemDark = (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = WidgetLogic.isDark(binding?.darkMode ?: DarkMode.SYSTEM, systemDark)
        val rt = app.themes.resolve(data, schedule, spec.sizeClass, dark, binding)

        val views = RemoteViews(app.packageName, R.layout.widget_schedule)
        if (schedule == null) {
            val text = if (data.schedules.isEmpty()) "앱에서 스케줄을 먼저 만들어 주세요" else "연결된 스케줄이 없어요\n눌러서 선택"
            views.setImageViewBitmap(R.id.widget_image, painter.renderMessage(spec, rt, text))
            views.setContentDescription(R.id.widget_image, text.replace("\n", " "))
            views.setOnClickPendingIntent(R.id.widget_root, configIntent(appWidgetId))
            return views
        }

        val now = LocalDateTime.now()
        val holidays = app.holidays.calendar(data.customHolidays)
        val week = OccurrenceResolver.resolve(data, schedule.id, WeekRange.of(now.toLocalDate()), holidays, now)!!
        val layout = WeekLayout.compute(week, rt.theme, spec, now, spec.sizeClass)
        views.setImageViewBitmap(R.id.widget_image, painter.render(layout, rt))
        views.setContentDescription(R.id.widget_image, "${schedule.name} 이번 주 시간표. ${TodaySummary.of(week, now).text()}")
        views.setOnClickPendingIntent(R.id.widget_root, openScheduleIntent(appWidgetId, schedule.id))
        return views
    }

    private fun openScheduleIntent(appWidgetId: Int, scheduleId: String): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setAction(ACTION_OPEN_SCHEDULE + "/" + appWidgetId) // 위젯마다 다른 PendingIntent
            .putExtra(MainActivity.EXTRA_SCHEDULE_ID, scheduleId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(app, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun configIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(app, WidgetConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE + "/" + appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(app, appWidgetId + 100_000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * 현재 시간선용 5분 갱신. RTC(비깨우기)라 화면이 꺼져 있으면 폰을 깨우지 않고,
     * 켜진 뒤 처음 기회에 한 번 그린다. 날짜가 바뀌는 것(오늘 강조·새 주)도 이걸로 따라간다.
     */
    fun scheduleTicks() {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val first = System.currentTimeMillis() + WidgetLogic.millisToNextTick(System.currentTimeMillis())
        am.setRepeating(AlarmManager.RTC, first, WidgetLogic.TICK_MINUTES * 60_000, tickIntent())
    }

    fun cancelTicks() {
        app.getSystemService(AlarmManager::class.java)?.cancel(tickIntent())
    }

    private fun tickIntent(): PendingIntent = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, WidgetEventsReceiver::class.java).setAction(WidgetEventsReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 런처에서 지워진 위젯의 연결 정리. */
    fun pruneBindings() {
        val live = allIds().toSet()
        if (app.store.data.value.widgets.any { it.appWidgetId !in live }) {
            app.store.update { WidgetLogic.keepOnly(it, live) }
        }
    }

    companion object {
        private const val TAG = "FamilyWeeklyWidget"
        const val ACTION_OPEN_SCHEDULE = "io.github.rosymaro.familyweekly.OPEN_SCHEDULE"
    }
}

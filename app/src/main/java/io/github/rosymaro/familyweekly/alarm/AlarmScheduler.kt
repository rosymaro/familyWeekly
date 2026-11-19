package io.github.rosymaro.familyweekly.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.MainActivity
import io.github.rosymaro.familyweekly.alarm.planner.AlarmDiff
import io.github.rosymaro.familyweekly.alarm.planner.AlarmPlanner
import io.github.rosymaro.familyweekly.alarm.planner.ChannelRules
import io.github.rosymaro.familyweekly.alarm.planner.PlannedAlarm
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId

/** 알림 권한 상태 (설정 화면 안내 카드). */
data class AlarmPermissions(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
)

/**
 * AlarmManager 등록. 계획(AlarmPlanner) → 이전 등록과 비교(AlarmDiff) → 바뀐 것만 해제/등록.
 * 정확한 알람 권한이 없으면 ±30초 창(setWindow)으로 대신한다.
 */
class AlarmScheduler(private val app: FamilyWeeklyApp) {

    private val am: AlarmManager get() = app.getSystemService(AlarmManager::class.java)
    private val prefs = app.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun permissions(): AlarmPermissions {
        val nm = app.getSystemService(NotificationManager::class.java)
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else nm.areNotificationsEnabled()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val full = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        return AlarmPermissions(notif && nm.areNotificationsEnabled(), exact, full)
    }

    /** 지금 데이터로 다시 계산해서 등록. */
    @Synchronized
    fun reschedule(): List<PlannedAlarm> {
        val data = app.store.data.value
        val planned = AlarmPlanner.plan(data, app.holidays.calendar(data.customHolidays), LocalDateTime.now())
        val registered = loadRegistered()
        val diff = AlarmDiff.diff(registered, planned)
        diff.cancel.forEach { am.cancel(fireIntent(it, null)) }
        val exact = permissions().exactAlarms
        diff.set.forEach { set(it, exact) }
        saveRegistered(planned.associate { it.key to AlarmDiff.fingerprint(it) })
        scheduleDailyRefresh()
        cleanupChannels(planned)
        return planned
    }

    /** 시간대가 바뀌면 같은 "시각"이라도 실제 순간이 바뀌므로 전부 다시 등록. */
    @Synchronized
    fun rescheduleAll() {
        loadRegistered().keys.forEach { am.cancel(fireIntent(it, null)) }
        saveRegistered(emptyMap())
        reschedule()
    }

    fun nextAlarms(limit: Int = 10): List<PlannedAlarm> {
        val data = app.store.data.value
        return AlarmPlanner.plan(data, app.holidays.calendar(data.customHolidays), LocalDateTime.now()).take(limit)
    }

    /** "N분 뒤 다시": 한 번만 울리는 알림 (계획 목록과 따로). */
    fun snooze(alarm: PlannedAlarm) {
        val at = LocalDateTime.now().plusMinutes(alarm.snoozeMinutes.coerceAtLeast(1).toLong())
        set(alarm.copy(key = alarm.key + SNOOZE_SUFFIX, at = at.withSecond(0).withNano(0).toString()), permissions().exactAlarms)
    }

    private fun set(a: PlannedAlarm, exact: Boolean) {
        val whenMs = a.atTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = fireIntent(a.key, a)
        try {
            when {
                exact && a.style == AlarmStyle.FULL_SCREEN_ALARM ->
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, openAppIntent(a.scheduleId)), pi)
                exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                else -> am.setWindow(AlarmManager.RTC_WAKEUP, whenMs - 30_000, 60_000, pi)
            }
        } catch (e: SecurityException) {
            // 정확한 알람 권한을 막 빼앗긴 경우
            Log.w(TAG, "exact alarm denied, using window", e)
            am.setWindow(AlarmManager.RTC_WAKEUP, whenMs - 30_000, 60_000, pi)
        }
    }

    /** 키마다 다른 data Uri → PendingIntent가 겹치지 않고, 같은 키로 해제할 수 있다. */
    private fun fireIntent(key: String, payload: PlannedAlarm?): PendingIntent {
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("familyweekly://alarm/" + Uri.encode(key)))
        payload?.let { intent.putExtra(AlarmReceiver.EXTRA_ALARM, json.encodeToString(PlannedAlarm.serializer(), it)) }
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openAppIntent(scheduleId: String): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setAction("io.github.rosymaro.familyweekly.OPEN_FROM_ALARM/$scheduleId")
            .putExtra(MainActivity.EXTRA_SCHEDULE_ID, scheduleId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(app, scheduleId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** 48시간 창을 이어 가도록 하루 한 번 (자정 직후, 비정확) 다시 계산. */
    private fun scheduleDailyRefresh() {
        val next = LocalDateTime.now().toLocalDate().plusDays(1).atTime(0, 5)
        val ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            app, 1, Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setWindow(AlarmManager.RTC_WAKEUP, ms, 30 * 60_000L, pi)
    }

    private fun cleanupChannels(planned: List<PlannedAlarm>) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = app.getSystemService(NotificationManager::class.java)
        val active = nm.activeNotifications.mapNotNull { it.notification.channelId }.toSet()
        ChannelRules.unused(nm.notificationChannels.map { it.id }, planned)
            .filterNot { it in active }
            .forEach { runCatching { nm.deleteNotificationChannel(it) } }
    }

    private fun loadRegistered(): Map<String, String> =
        prefs.getString(KEY_REGISTERED, null)?.let { runCatching { json.decodeFromString(mapSer, it) }.getOrNull() } ?: emptyMap()

    private fun saveRegistered(m: Map<String, String>) {
        prefs.edit().putString(KEY_REGISTERED, json.encodeToString(mapSer, m)).apply()
    }

    companion object {
        private const val TAG = "FamilyWeeklyAlarm"
        private const val KEY_REGISTERED = "registered"
        const val SNOOZE_SUFFIX = "|snooze"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val mapSer = MapSerializer(String.serializer(), String.serializer())
    }
}

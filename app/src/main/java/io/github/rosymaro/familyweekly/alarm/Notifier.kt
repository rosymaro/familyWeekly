package io.github.rosymaro.familyweekly.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.R
import io.github.rosymaro.familyweekly.alarm.planner.ChannelImportance
import io.github.rosymaro.familyweekly.alarm.planner.ChannelRules
import io.github.rosymaro.familyweekly.alarm.planner.ChannelSpec
import io.github.rosymaro.familyweekly.alarm.planner.PlannedAlarm
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import java.time.ZoneId

/** 알림 만들기. 채널은 ChannelRules가 정한 조합마다 하나 (minSdk 26이라 채널 항상 사용). */
class Notifier(private val app: FamilyWeeklyApp) {

    private val nm: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    fun notificationId(key: String): Int = key.removeSuffix(AlarmScheduler.SNOOZE_SUFFIX).hashCode()

    fun show(a: PlannedAlarm) {
        val spec = ChannelRules.specFor(a)
        ensureChannel(spec)
        val id = notificationId(a.key)
        val payload = AlarmScheduler.json.encodeToString(PlannedAlarm.serializer(), a)

        val b = Notification.Builder(app, spec.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(a.title)
            .setContentText(a.text)
            .setWhen(a.atTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(app.alarms.openAppIntent(a.scheduleId))
            .setCategory(if (a.style == AlarmStyle.FULL_SCREEN_ALARM) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, "확인", actionIntent(AlarmReceiver.ACTION_DISMISS, a, payload)).build())
        if (a.snoozeMinutes > 0) {
            b.addAction(Notification.Action.Builder(null, "${a.snoozeMinutes}분 뒤 다시", actionIntent(AlarmReceiver.ACTION_SNOOZE, a, payload)).build())
        }
        if (a.style == AlarmStyle.FULL_SCREEN_ALARM && canFullScreen()) {
            val full = Intent(app, AlarmActivity::class.java)
                .setAction("io.github.rosymaro.familyweekly.ALARM/" + a.key)
                .putExtra(AlarmReceiver.EXTRA_ALARM, payload)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            b.setFullScreenIntent(PendingIntent.getActivity(app, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE), true)
        }
        val n = b.build()
        if (a.repeatUntilDismissed) n.flags = n.flags or Notification.FLAG_INSISTENT // 끌 때까지 소리·진동 반복
        runCatching { nm.notify(id, n) } // 알림 권한이 없으면 조용히 무시 (설정 화면에서 안내)
    }

    fun cancel(key: String) = nm.cancel(notificationId(key))

    private fun canFullScreen() = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()

    private fun actionIntent(action: String, a: PlannedAlarm, payload: String): PendingIntent {
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("familyweekly://action/" + Uri.encode(a.key)))
            .putExtra(AlarmReceiver.EXTRA_ALARM, payload)
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel(spec: ChannelSpec) {
        if (nm.getNotificationChannel(spec.id) != null) return
        val importance = when (spec.importance) {
            ChannelImportance.LOW -> NotificationManager.IMPORTANCE_LOW
            ChannelImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
            ChannelImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH
        }
        val ch = NotificationChannel(spec.id, spec.name, importance)
        ch.description = "familyWeekly 일정 알림 (${spec.name})"
        if (spec.soundUri == null) {
            ch.setSound(null, null)
        } else {
            val uri = if (spec.soundUri == ChannelRules.DEFAULT_SOUND) {
                RingtoneManager.getDefaultUri(if (spec.alarmUsage) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            } else Uri.parse(spec.soundUri)
            val attrs = AudioAttributes.Builder()
                .setUsage(if (spec.alarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ch.setSound(uri, attrs)
        }
        if (spec.vibration != null) {
            ch.enableVibration(true)
            ch.vibrationPattern = spec.vibration
        } else {
            ch.enableVibration(false)
        }
        if (spec.importance == ChannelImportance.HIGH) ch.setBypassDnd(false)
        nm.createNotificationChannel(ch)
    }
}

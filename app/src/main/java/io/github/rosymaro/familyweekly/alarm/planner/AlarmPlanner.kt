package io.github.rosymaro.familyweekly.alarm.planner

import io.github.rosymaro.familyweekly.domain.AlarmRule
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.AlarmTrigger
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.OccurrenceStatus
import io.github.rosymaro.familyweekly.domain.QuietHours
import io.github.rosymaro.familyweekly.domain.SoundSetting
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.domain.TriggerType
import io.github.rosymaro.familyweekly.domain.VibrationSetting
import io.github.rosymaro.familyweekly.domain.WeekRange
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
enum class AlarmSource { BLOCK, EVENT }

/** 울릴 알림 하나. 수신기에 그대로 넘겨서(JSON) 다시 계산하지 않아도 알림을 만들 수 있다. */
@Serializable
data class PlannedAlarm(
    /** 안정적인 키: 같은 블록·날짜·트리거면 늘 같음 (requestCode·차등 등록에 씀) */
    val key: String,
    /** 울릴 시각 ISO LocalDateTime ("2026-11-16T14:50") */
    val at: String,
    val source: AlarmSource,
    val sourceId: String,
    /** 첫 스케줄 (알림 탭 → 그 주간 화면) */
    val scheduleId: String,
    val trigger: TriggerType,
    val minutes: Int,
    val title: String,
    val text: String,
    val style: AlarmStyle,
    val sound: SoundSetting,
    val vibration: VibrationSetting,
    val repeatUntilDismissed: Boolean,
    val snoozeMinutes: Int,
    /** 방해 금지 시간이라 조용히 낮췄는지 */
    val quieted: Boolean = false,
) {
    val atTime: LocalDateTime get() = LocalDateTime.parse(at)
}

/**
 * 지금부터 [horizonHours] 안에 울릴 알림 목록 (SPEC 5절). Android 의존성 없음.
 * - 취소된 블록(휴가·개별·특이 일정·공휴일)은 알림 없음
 * - 규칙: 블록/특이 일정 개별값 > 스케줄 기본값 > 앱 기본값 (규칙 통째로)
 * - 특이 일정은 여러 아이에 붙어도 한 번만 울림 (알림이 켜진 아이가 하나라도 있으면)
 * - 포스트잇을 뗀 특이 일정도 알림은 울림 (떼기는 보기만 바꿈)
 * - 방해 금지 시간에는 무음 알림으로 낮춤 (전체 화면 알람은 설정에 따라 예외)
 */
object AlarmPlanner {

    const val HORIZON_HOURS = 48L

    fun plan(
        data: AppData,
        holidays: HolidayCalendar,
        now: LocalDateTime,
        horizonHours: Long = HORIZON_HOURS,
    ): List<PlannedAlarm> {
        val settings = data.settings
        if (!settings.alarmsEnabled) return emptyList()
        val end = now.plusHours(horizonHours)
        val weeks = generateSequence(WeekRange.of(now.toLocalDate())) { it.shift(1) }
            .takeWhile { !it.monday.isAfter(end.toLocalDate()) }
            .toList()
        val out = mutableListOf<PlannedAlarm>()

        // 반복 블록
        for (schedule in data.schedules) {
            if (!schedule.alarmsEnabled) continue
            for (week in weeks) {
                val resolved = OccurrenceResolver.resolve(data, schedule.id, week, holidays) ?: continue
                for (occ in resolved.occurrences) {
                    if (occ.status != OccurrenceStatus.ACTIVE) continue
                    val b = occ.block
                    val rule = b.alarm ?: schedule.alarmDefaults ?: settings.defaultAlarm
                    for (t in rule.triggers) {
                        val at = triggerTime(occ.date, b.startMin, b.endMin, t) ?: continue
                        if (!at.isAfter(now) || at.isAfter(end)) continue
                        out += make(
                            key = "B|${b.id}|${occ.date}|${t.type}|${t.minutes}",
                            at = at,
                            source = AlarmSource.BLOCK,
                            sourceId = b.id,
                            scheduleId = schedule.id,
                            trigger = t,
                            rule = rule,
                            who = schedule.name,
                            what = b.title,
                            place = b.place,
                            startMin = b.startMin,
                            endMin = b.endMin,
                            quiet = settings.quietHours,
                            template = settings.notificationFormat,
                        )
                    }
                }
            }
        }

        // 특이 일정 (한 번만)
        val firstDay = now.toLocalDate()
        for (e in data.specialEvents) {
            if (e.date.isBefore(firstDay) || e.date.isAfter(end.toLocalDate())) continue
            val alarmed = e.scheduleIds.mapNotNull { data.schedule(it) }.filter { it.alarmsEnabled }
            if (alarmed.isEmpty()) continue
            val rule = e.alarm ?: settings.eventDefaultAlarm
            val (s, f) = eventTimes(e, settings.allDayAlarmMin)
            for (t in rule.triggers) {
                val at = triggerTime(e.date, s, f, t) ?: continue
                if (!at.isAfter(now) || at.isAfter(end)) continue
                out += make(
                    key = "E|${e.id}|${t.type}|${t.minutes}",
                    at = at,
                    source = AlarmSource.EVENT,
                    sourceId = e.id,
                    scheduleId = alarmed.first().id,
                    trigger = t,
                    rule = rule,
                    who = alarmed.joinToString("·") { it.name },
                    what = "📌 " + e.title,
                    place = null,
                    startMin = s,
                    endMin = f,
                    quiet = settings.quietHours,
                    template = settings.notificationFormat,
                    allDay = e.allDay,
                )
            }
        }

        return out.distinctBy { it.key }.sortedWith(compareBy({ it.at }, { it.key }))
    }

    /** 하루 종일이면 [allDayMin]을 시작·끝으로 본다 (끝 알림은 시작과 같은 때). */
    private fun eventTimes(e: SpecialEvent, allDayMin: Int): Pair<Int, Int> =
        if (e.allDay) allDayMin to allDayMin else e.startMin to e.endMin

    /** 트리거 시각. 전날로 넘어가는 사전 알림(0:10 일정의 30분 전)도 계산한다. */
    fun triggerTime(date: LocalDate, startMin: Int, endMin: Int, t: AlarmTrigger): LocalDateTime? {
        val minuteOfDay = when (t.type) {
            TriggerType.BEFORE_START -> startMin - t.minutes
            TriggerType.AT_START -> startMin
            TriggerType.BEFORE_END -> endMin - t.minutes
            TriggerType.AT_END -> endMin
        }
        if (t.minutes < 0) return null
        return date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
    }

    /** 방해 금지 시간 안인지 (22:00~07:00처럼 자정을 넘는 구간도). */
    fun inQuietHours(q: QuietHours?, at: LocalDateTime): Boolean {
        if (q == null || q.startMin == q.endMin) return false
        val m = TimeStep.fromLocalTime(at.toLocalTime())
        return if (q.startMin < q.endMin) m >= q.startMin && m < q.endMin
        else m >= q.startMin || m < q.endMin
    }

    private fun make(
        key: String,
        at: LocalDateTime,
        source: AlarmSource,
        sourceId: String,
        scheduleId: String,
        trigger: AlarmTrigger,
        rule: AlarmRule,
        who: String,
        what: String,
        place: String?,
        startMin: Int,
        endMin: Int,
        quiet: QuietHours?,
        template: String,
        allDay: Boolean = false,
    ): PlannedAlarm {
        val isQuiet = inQuietHours(quiet, at) &&
            !(rule.style == AlarmStyle.FULL_SCREEN_ALARM && quiet?.allowFullScreenAlarm == true)
        val style = if (isQuiet) AlarmStyle.SILENT_NOTIFICATION else rule.style
        val fullScreen = style == AlarmStyle.FULL_SCREEN_ALARM
        return PlannedAlarm(
            key = key,
            at = at.toString(),
            source = source,
            sourceId = sourceId,
            scheduleId = scheduleId,
            trigger = trigger.type,
            minutes = trigger.minutes,
            title = AlarmText.title(template, who, what, trigger, place),
            text = AlarmText.body(trigger, startMin, endMin, place, allDay),
            style = style,
            sound = if (isQuiet) rule.sound.copy(enabled = false) else rule.sound,
            vibration = if (isQuiet) rule.vibration.copy(enabled = false) else rule.vibration,
            repeatUntilDismissed = fullScreen && rule.repeatUntilDismissed,
            snoozeMinutes = rule.snoozeMinutes.coerceAtLeast(0),
            quieted = isQuiet,
        )
    }
}

/** 알림 문구. */
object AlarmText {

    /**
     * 사전·종료 전 알림은 설정 문구 형식({아이} {일정} {N} {장소}),
     * 시작·끝 알림은 "{아이} · {일정} 시작/끝".
     */
    fun title(template: String, who: String, what: String, t: AlarmTrigger, place: String?): String = when (t.type) {
        TriggerType.BEFORE_START -> fill(template, who, what, t.minutes, place)
        TriggerType.BEFORE_END -> fill(template, who, what, t.minutes, place).replace("분 전", "분 뒤 끝")
        TriggerType.AT_START -> "$who · $what 시작"
        TriggerType.AT_END -> "$who · $what 끝"
    }

    fun fill(template: String, who: String, what: String, minutes: Int, place: String?): String {
        // 정규식은 쓰지 않는다 (안드로이드 정규식은 JVM보다 엄격해서 "{장소}" 같은 패턴에서 오류가 날 수 있음)
        var s = template
        s = if (place.isNullOrBlank()) {
            s.replace(" ({장소})", "").replace("({장소})", "").replace("{장소}", "")
        } else s.replace("{장소}", place)
        return s.replace("{아이}", who).replace("{일정}", what).replace("{N}", minutes.toString()).trim()
    }

    fun body(t: AlarmTrigger, startMin: Int, endMin: Int, place: String?, allDay: Boolean): String {
        val time = if (allDay) "하루 종일" else "${TimeStep.format(startMin)}~${TimeStep.format(endMin)}"
        return listOfNotNull(time, place?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }
}

/** 이미 등록한 알림과 새 계획 비교 → 해제할 것 / 새로 등록할 것. */
object AlarmDiff {

    data class Result(val cancel: List<String>, val set: List<PlannedAlarm>)

    /**
     * [registered]: 키 → 등록할 때의 "내용 지문"(시각·문구·스타일).
     * 내용이 바뀐 알림은 해제 후 다시 등록.
     */
    fun diff(registered: Map<String, String>, planned: List<PlannedAlarm>): Result {
        val plannedByKey = planned.associateBy { it.key }
        val cancel = registered.keys.filter { k -> plannedByKey[k]?.let { fingerprint(it) } != registered[k] }
        val set = planned.filter { registered[it.key] != fingerprint(it) }
        return Result(cancel, set)
    }

    fun fingerprint(a: PlannedAlarm): String = a.copy(key = "").hashCode().toString(16) + "@" + a.at

    /** PendingIntent requestCode (키에서 안정적으로) */
    fun requestCode(key: String): Int = key.hashCode()
}

@file:UseSerializers(LocalDateSerializer::class, DayOfWeekSerializer::class)

package io.github.rosymaro.familyweekly.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDate

// 저장 파일(family_weekly.json)의 모양. agent/SPEC.md 4절.
// 시각은 자정부터의 분(Int, 5의 배수), 날짜는 ISO 문자열("2026-11-19").
// 필드를 추가할 때는 기본값을 꼭 줘서 이전 파일도 읽히게 한다. 형식을 바꾸면 AppData.CURRENT_VERSION을 올리고 Migrations에 추가.

@Serializable
data class AppData(
    val version: Int = CURRENT_VERSION,
    val settings: Settings = Settings(),
    /** 홈 목록 순서 그대로. */
    val schedules: List<Schedule> = emptyList(),
    val specialEvents: List<SpecialEvent> = emptyList(),
    val vacations: List<Vacation> = emptyList(),
    val customHolidays: List<CustomHoliday> = emptyList(),
    val widgets: List<WidgetBinding> = emptyList(),
) {
    fun schedule(id: String): Schedule? = schedules.firstOrNull { it.id == id }

    companion object {
        /** 1: 처음 / 2: SpecialEvent.detachedFrom(포스트잇 떼기) 추가 */
        const val CURRENT_VERSION = 2
    }
}

// ───────────── 설정 ─────────────

@Serializable
enum class DarkMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val use24Hour: Boolean = true,
    val alarmsEnabled: Boolean = true,
    /** 방해 금지 시간대. null이면 없음. */
    val quietHours: QuietHours? = null,
    /** 앱 전체 기본 알림 규칙 (스케줄·블록에 없을 때). */
    val defaultAlarm: AlarmRule = AlarmRule(),
    /** 특이 일정 기본 알림 규칙. */
    val eventDefaultAlarm: AlarmRule = AlarmRule(),
    /** 알림 문구 형식. {아이} {일정} {N} {장소} 치환. */
    val notificationFormat: String = "{아이} · {일정} {N}분 전 ({장소})",
)

@Serializable
data class QuietHours(
    val startMin: Int,
    val endMin: Int,
    /** 방해 금지 시간에도 FULL_SCREEN_ALARM은 그대로 울릴지. */
    val allowFullScreenAlarm: Boolean = false,
)

// ───────────── 스케줄 / 블록 ─────────────

@Serializable
enum class DaysMode {
    MON_FRI, MON_SUN;

    val days: List<DayOfWeek>
        get() = when (this) {
            MON_FRI -> DayOfWeek.entries.take(5)
            MON_SUN -> DayOfWeek.entries.toList()
        }
}

@Serializable
enum class TimeRangeMode { AUTO, FIXED }

/** 위젯·주간 화면에 보일 시간 범위. FIXED일 때만 startMin/endMin을 쓴다. */
@Serializable
data class TimeRange(
    val mode: TimeRangeMode = TimeRangeMode.AUTO,
    val startMin: Int = 9 * 60,
    val endMin: Int = 18 * 60,
)

@Serializable
enum class HolidayPolicy {
    /** 공휴일은 표시만. */
    SHOW_ONLY,

    /** 공휴일에는 반복 블록을 취소 처리. */
    CANCEL_BLOCKS,
}

@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val days: DaysMode = DaysMode.MON_FRI,
    val timeRange: TimeRange = TimeRange(),
    val themeId: String? = null,
    /** 이 스케줄 블록들의 기본 알림. null이면 Settings.defaultAlarm. */
    val alarmDefaults: AlarmRule? = null,
    val alarmsEnabled: Boolean = true,
    val holidayPolicy: HolidayPolicy = HolidayPolicy.SHOW_ONLY,
    val blocks: List<Block> = emptyList(),
    /** "이번 주만 취소" 같은 개별 취소. */
    val cancellations: List<ManualCancellation> = emptyList(),
)

/** 매주 반복되는 칸. 같은 일정을 여러 요일에 넣으면 요일마다 블록 하나, groupId로 묶음. */
@Serializable
data class Block(
    val id: String,
    val title: String,
    val day: DayOfWeek,
    val startMin: Int,
    val endMin: Int,
    val groupId: String? = null,
    /** 테마 팔레트 키 또는 "#RRGGBB". */
    val colorKey: String = "default",
    val place: String? = null,
    val memo: String? = null,
    /** null이면 스케줄 기본 알림. */
    val alarm: AlarmRule? = null,
    /** 이 날짜부터 (포함). null이면 처음부터. */
    val validFrom: LocalDate? = null,
    /** 이 날짜까지 (포함). null이면 무기한. */
    val validUntil: LocalDate? = null,
) {
    fun isValidOn(date: LocalDate): Boolean =
        (validFrom == null || !date.isBefore(validFrom)) &&
            (validUntil == null || !date.isAfter(validUntil))
}

@Serializable
data class ManualCancellation(
    val blockId: String,
    val date: LocalDate,
    val reason: String? = null,
)

// ───────────── 휴가 / 특이 일정 / 공휴일 ─────────────

/** 반복 일정 예외 처리. 기간 동안 해당 블록은 '취소'로 보이고 알림이 없다. */
@Serializable
data class Vacation(
    val id: String,
    val title: String,
    val from: LocalDate,
    /** 포함. */
    val to: LocalDate,
    val scheduleIds: List<String>,
    /** null이면 대상 스케줄의 모든 블록. 있으면 이 블록들만. */
    val blockIds: List<String>? = null,
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

@Serializable
enum class OverlapAction {
    /** 겹치는 기존 블록 유지 (둘 다 활성). */
    KEEP,

    /** 겹치는 기존 블록 취소. */
    CANCEL,
}

/** 특이 일정 = 포스트잇. 여러 스케줄에 붙을 수 있다. */
@Serializable
data class SpecialEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val scheduleIds: List<String>,
    val startMin: Int = 0,
    val endMin: Int = 0,
    /** true면 시각 없이 그날 전체. 그날 모든 블록과 겹친 것으로 본다. */
    val allDay: Boolean = false,
    /** 스케줄 id → 겹치는 기존 블록 처리. 없으면 KEEP으로 본다. */
    val overlapActions: Map<String, OverlapAction> = emptyMap(),
    val memo: String? = null,
    /** 포스트잇 종이 색 키 (테마 postIt.papers). */
    val paperKey: String = "yellow",
    /** null이면 Settings.eventDefaultAlarm. */
    val alarm: AlarmRule? = null,
    /**
     * 포스트잇을 뗀 스케줄. 그 아이 시간표(앱·위젯)에는 포스트잇이 안 보이지만
     * 특이 일정은 캘린더에 그대로 남고, scheduleIds·유지/취소 선택도 그대로다 (다시 붙일 수 있음).
     */
    val detachedFrom: List<String> = emptyList(),
) {
    fun isDetachedFrom(scheduleId: String): Boolean = scheduleId in detachedFrom

    fun overlapActionFor(scheduleId: String): OverlapAction =
        overlapActions[scheduleId] ?: OverlapAction.KEEP

    /** 이 일정이 [start, end) 블록과 시간이 겹치는지. 하루 종일이면 항상 겹침. */
    fun overlaps(start: Int, end: Int): Boolean =
        allDay || (start < endMin && startMin < end)
}

@Serializable
data class Holiday(
    val date: LocalDate,
    val name: String,
)

/**
 * 사용자 공휴일. hidden=false면 공휴일로 추가 (예: 재량휴업일),
 * hidden=true면 그 날짜의 기본 공휴일을 숨김.
 */
@Serializable
data class CustomHoliday(
    val date: LocalDate,
    val name: String = "",
    val hidden: Boolean = false,
)

// ───────────── 위젯 연결 ─────────────

@Serializable
data class WidgetBinding(
    val appWidgetId: Int,
    /** null이면 연결된 스케줄 없음 (스케줄이 삭제됨). */
    val scheduleId: String?,
    /** null이면 스케줄 테마. (토큰 부분 덮어쓰기는 Phase 2/7에서 추가) */
    val themeId: String? = null,
    val darkMode: DarkMode = DarkMode.SYSTEM,
)

// ───────────── 알림 규칙 (계산·등록은 Phase 6) ─────────────

@Serializable
enum class TriggerType { BEFORE_START, AT_START, BEFORE_END, AT_END }

@Serializable
data class AlarmTrigger(
    val type: TriggerType,
    /** BEFORE_* 일 때 몇 분 전 (0~180, 5분 단위). */
    val minutes: Int = 0,
)

@Serializable
enum class AlarmStyle { SILENT_NOTIFICATION, NOTIFICATION, HEADS_UP, FULL_SCREEN_ALARM }

@Serializable
enum class SoundKind { NOTIFICATION, ALARM }

@Serializable
data class SoundSetting(
    val enabled: Boolean = true,
    /** null이면 기본음. */
    val uri: String? = null,
    val kind: SoundKind = SoundKind.NOTIFICATION,
)

@Serializable
enum class VibrationPattern { OFF, SHORT, LONG, DOUBLE, CUSTOM }

@Serializable
data class VibrationSetting(
    val enabled: Boolean = true,
    val pattern: VibrationPattern = VibrationPattern.SHORT,
    /** CUSTOM일 때 ms 배열 (쉼, 진동, 쉼, 진동 ...). */
    val customMs: List<Long> = emptyList(),
)

@Serializable
data class AlarmRule(
    val triggers: List<AlarmTrigger> = listOf(AlarmTrigger(TriggerType.BEFORE_START, 10)),
    val style: AlarmStyle = AlarmStyle.HEADS_UP,
    val sound: SoundSetting = SoundSetting(),
    val vibration: VibrationSetting = VibrationSetting(),
    /** FULL_SCREEN_ALARM에서만: 끌 때까지 반복. */
    val repeatUntilDismissed: Boolean = false,
    /** 0이면 다시 알림 버튼 없음. */
    val snoozeMinutes: Int = 5,
)

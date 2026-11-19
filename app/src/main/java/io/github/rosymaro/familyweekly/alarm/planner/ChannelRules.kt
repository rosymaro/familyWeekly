package io.github.rosymaro.familyweekly.alarm.planner

import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.SoundKind
import io.github.rosymaro.familyweekly.domain.VibrationPattern

enum class ChannelImportance { LOW, DEFAULT, HIGH }

/** 알림 채널 하나의 내용. Android 채널은 만든 뒤 소리·진동을 바꿀 수 없어서 조합마다 채널을 따로 만든다. */
data class ChannelSpec(
    val id: String,
    val name: String,
    val importance: ChannelImportance,
    /** null이면 소리 없음. "default"면 종류별 기본음 */
    val soundUri: String?,
    val alarmUsage: Boolean,
    /** null이면 진동 없음 */
    val vibration: LongArray?,
) {
    override fun equals(other: Any?) = other is ChannelSpec && other.id == id
    override fun hashCode() = id.hashCode()
}

/** 알림 채널 규칙 (SPEC 5절: 이 규칙은 한 곳에만). */
object ChannelRules {

    const val PREFIX = "fw_alarm_"
    const val DEFAULT_SOUND = "default"

    fun vibrationOf(pattern: VibrationPattern, custom: List<Long>): LongArray? = when (pattern) {
        VibrationPattern.OFF -> null
        VibrationPattern.SHORT -> longArrayOf(0, 250)
        VibrationPattern.LONG -> longArrayOf(0, 900)
        VibrationPattern.DOUBLE -> longArrayOf(0, 200, 150, 200)
        VibrationPattern.CUSTOM -> custom.takeIf { it.size >= 2 }?.toLongArray() ?: longArrayOf(0, 250)
    }

    fun specFor(a: PlannedAlarm): ChannelSpec {
        val importance = when (a.style) {
            AlarmStyle.SILENT_NOTIFICATION -> ChannelImportance.LOW
            AlarmStyle.NOTIFICATION -> ChannelImportance.DEFAULT
            AlarmStyle.HEADS_UP, AlarmStyle.FULL_SCREEN_ALARM -> ChannelImportance.HIGH
        }
        val silent = a.style == AlarmStyle.SILENT_NOTIFICATION
        val sound = if (!silent && a.sound.enabled) a.sound.uri ?: DEFAULT_SOUND else null
        val alarmUsage = a.sound.kind == SoundKind.ALARM
        val vib = if (!silent && a.vibration.enabled) vibrationOf(a.vibration.pattern, a.vibration.customMs) else null
        val signature = listOf(importance, sound, alarmUsage, vib?.joinToString(",")).joinToString("|")
        val id = PREFIX + Integer.toHexString(signature.hashCode())
        val name = buildString {
            append(
                when (a.style) {
                    AlarmStyle.SILENT_NOTIFICATION -> "무음 알림"
                    AlarmStyle.NOTIFICATION -> "알림"
                    AlarmStyle.HEADS_UP -> "팝업 알림"
                    AlarmStyle.FULL_SCREEN_ALARM -> "알람"
                },
            )
            if (sound != null) append(if (alarmUsage) " · 알람음" else " · 소리") else if (!silent) append(" · 소리 없음")
            if (vib != null) append(" · 진동(${a.vibration.pattern.label()})")
            if (sound != null && sound != DEFAULT_SOUND) append(" · 고른 소리")
        }
        return ChannelSpec(id, name, importance, sound, alarmUsage, vib)
    }

    fun VibrationPattern.label(): String = when (this) {
        VibrationPattern.OFF -> "없음"
        VibrationPattern.SHORT -> "짧게"
        VibrationPattern.LONG -> "길게"
        VibrationPattern.DOUBLE -> "두 번"
        VibrationPattern.CUSTOM -> "직접"
    }

    /** 지금 계획에 안 쓰이는 우리 채널 (정리 대상). */
    fun unused(existingIds: Collection<String>, planned: List<PlannedAlarm>): List<String> {
        val used = planned.map { specFor(it).id }.toSet()
        return existingIds.filter { it.startsWith(PREFIX) && it !in used }
    }
}

/** 알림 규칙 한 줄 요약 (설정 화면 표시용). */
object AlarmRuleText {
    fun summary(rule: io.github.rosymaro.familyweekly.domain.AlarmRule): String {
        val t = rule.triggers.sortedWith(compareBy({ it.type.ordinal }, { -it.minutes })).map {
            when (it.type) {
                io.github.rosymaro.familyweekly.domain.TriggerType.BEFORE_START -> "${it.minutes}분 전"
                io.github.rosymaro.familyweekly.domain.TriggerType.AT_START -> "시작"
                io.github.rosymaro.familyweekly.domain.TriggerType.BEFORE_END -> "끝 ${it.minutes}분 전"
                io.github.rosymaro.familyweekly.domain.TriggerType.AT_END -> "끝"
            }
        }
        if (t.isEmpty()) return "알림 없음"
        val style = when (rule.style) {
            AlarmStyle.SILENT_NOTIFICATION -> "무음 알림"
            AlarmStyle.NOTIFICATION -> "알림"
            AlarmStyle.HEADS_UP -> "팝업"
            AlarmStyle.FULL_SCREEN_ALARM -> "알람(전체 화면)"
        }
        val extras = buildList {
            if (rule.style != AlarmStyle.SILENT_NOTIFICATION) {
                if (rule.sound.enabled) add(if (rule.sound.kind == SoundKind.ALARM) "알람음" else "소리")
                if (rule.vibration.enabled && rule.vibration.pattern != VibrationPattern.OFF) add("진동")
            }
            if (rule.style == AlarmStyle.FULL_SCREEN_ALARM && rule.repeatUntilDismissed) add("끌 때까지")
        }
        return (t + style + extras).joinToString(" · ")
    }
}

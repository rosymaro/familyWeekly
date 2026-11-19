package io.github.rosymaro.familyweekly.domain

import java.time.LocalDateTime

/** 홈 카드의 "오늘 남은 일정" 요약. */
data class TodaySummary(
    /** 오늘 아직 끝나지 않은 활성 블록 수 */
    val remaining: Int,
    /** 다음(또는 지금 진행 중인) 활성 블록 */
    val next: Occurrence?,
    /** 오늘 취소된 블록 수 */
    val cancelledToday: Int,
    /** 오늘 붙은 특이 일정 수 */
    val eventsToday: Int,
    /** 오늘이 이 스케줄의 표시 요일인지 (월~금 스케줄의 주말이면 false) */
    val isScheduleDay: Boolean,
) {
    fun text(): String = when {
        !isScheduleDay -> "오늘은 시간표가 없는 날"
        else -> buildString {
            append(if (remaining == 0) "오늘 남은 일정 없음" else "오늘 남은 일정 ${remaining}개")
            next?.let { append(" · 다음 ${TimeStep.format(it.startMin)} ${it.block.title}") }
            if (eventsToday > 0) append(" · 특이 ${eventsToday}")
            if (cancelledToday > 0) append(" · 취소 ${cancelledToday}")
        }
    }

    companion object {
        fun of(week: ResolvedWeek, now: LocalDateTime): TodaySummary {
            val today = now.toLocalDate()
            val minute = TimeStep.fromLocalTime(now.toLocalTime())
            val todays = week.occurrences.filter { it.date == today }
            val active = todays.filter { it.status == OccurrenceStatus.ACTIVE && it.endMin > minute }
            return TodaySummary(
                remaining = active.size,
                next = active.minByOrNull { it.startMin },
                cancelledToday = todays.count { it.status == OccurrenceStatus.CANCELLED },
                eventsToday = week.events.count { it.event.date == today },
                isScheduleDay = week.days.any { it.date == today },
            )
        }
    }
}

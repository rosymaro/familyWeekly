package io.github.rosymaro.familyweekly.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 시간표를 말로 (TalkBack). 그림만으로는 화면 읽기 도구가 알 수 없는 것 —
 * 취소 여부·사유, 포스트잇이 기존 일정을 유지/취소했는지 — 를 글로 풀어 준다.
 */
object WeekDescription {

    fun reasonLabel(r: CancelReason): String = when (r) {
        CancelReason.MANUAL -> "이번 주만 취소"
        CancelReason.EVENT -> "특이 일정으로 취소"
        CancelReason.VACATION -> "휴가로 취소"
        CancelReason.HOLIDAY -> "공휴일로 취소"
    }

    private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN) + " (${d.monthValue}월 ${d.dayOfMonth}일)"

    fun occurrence(o: Occurrence): String = buildString {
        append(o.block.title).append(' ')
        append(TimeStep.format(o.startMin)).append("부터 ").append(TimeStep.format(o.endMin)).append("까지")
        o.block.place?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
        o.displayReason?.let { append(", ").append(reasonLabel(it)) }
    }

    fun event(e: EventOccurrence): String = buildString {
        append("특이 일정 ").append(e.event.title).append(' ')
        append(if (e.event.allDay) "하루 종일" else "${TimeStep.format(e.event.startMin)}부터 ${TimeStep.format(e.event.endMin)}까지")
        when (e.underState) {
            UnderState.KEEP -> append(", 기존 일정 유지")
            UnderState.CANCEL -> append(", 기존 일정 취소")
            UnderState.NONE -> Unit
        }
    }

    /** 하루: "월요일 (11월 16일), 오늘, 공휴일 추석, 휴가 여름방학. 피아노 15:00부터 16:00까지, … " */
    fun day(week: ResolvedWeek, date: LocalDate): String {
        val info = week.days.firstOrNull { it.date == date } ?: return ""
        val head = buildList {
            add(dayName(date))
            if (info.isToday) add("오늘")
            info.holiday?.let { add("공휴일 ${it.name}") }
            info.vacations.forEach { add("휴가 ${it.title}") }
        }.joinToString(", ")
        val items = week.events.filter { it.event.date == date }.map(::event) +
            week.occurrences.filter { it.date == date }.map(::occurrence)
        return head + ". " + if (items.isEmpty()) "일정 없음." else items.joinToString(". ") + "."
    }

    /** 한 주 전체 */
    fun week(week: ResolvedWeek): String =
        "${week.schedule.name} 주간 시간표. " + week.days.joinToString(" ") { day(week, it.date) }

    /** 위젯용 짧은 설명: 이름 + 오늘 (오늘이 표시 요일이 아니면 요약) */
    fun widget(week: ResolvedWeek, today: LocalDate): String {
        val todayText = if (week.days.any { it.date == today }) day(week, today) else "오늘은 시간표가 없는 날."
        return "${week.schedule.name} 이번 주 시간표. $todayText 눌러서 앱 열기."
    }
}

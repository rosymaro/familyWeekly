package io.github.rosymaro.familyweekly.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 월요일 시작 한 주. 표시 요일은 스케줄의 DaysMode(월~금 / 월~일). */
data class WeekRange(val monday: LocalDate) {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY) { "주 시작은 월요일이어야 합니다: $monday" }
    }

    val sunday: LocalDate get() = monday.plusDays(6)

    fun dates(mode: DaysMode): List<LocalDate> = mode.days.map { dateOf(it) }

    fun dateOf(day: DayOfWeek): LocalDate = monday.plusDays((day.value - 1).toLong())

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(monday) && !date.isAfter(sunday)

    fun shift(weeks: Long): WeekRange = WeekRange(monday.plusWeeks(weeks))

    companion object {
        fun of(date: LocalDate): WeekRange =
            WeekRange(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
}

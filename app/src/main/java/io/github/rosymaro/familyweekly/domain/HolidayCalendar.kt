package io.github.rosymaro.familyweekly.domain

import java.time.LocalDate

/**
 * 기본 공휴일(assets/data/holidays_kr.json) + 사용자 공휴일.
 * 사용자 항목이 이긴다: hidden=true면 그날 기본 공휴일을 숨기고, 아니면 그 이름으로 추가/덮어씀.
 */
class HolidayCalendar(
    builtIn: List<Holiday>,
    custom: List<CustomHoliday> = emptyList(),
) {
    private val byDate: Map<LocalDate, Holiday> = buildMap {
        builtIn.forEach { put(it.date, it) }
        custom.forEach { c ->
            if (c.hidden) remove(c.date) else put(c.date, Holiday(c.date, c.name))
        }
    }

    fun holidayOn(date: LocalDate): Holiday? = byDate[date]

    fun isHoliday(date: LocalDate): Boolean = date in byDate

    fun between(from: LocalDate, to: LocalDate): List<Holiday> =
        byDate.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    companion object {
        val EMPTY = HolidayCalendar(emptyList())
    }
}

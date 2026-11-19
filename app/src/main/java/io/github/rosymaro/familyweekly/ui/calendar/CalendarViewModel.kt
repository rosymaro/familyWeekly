package io.github.rosymaro.familyweekly.ui.calendar

import androidx.lifecycle.SavedStateHandle
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.HolidayOps
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(app: FamilyWeeklyApp, private val handle: SavedStateHandle) : StoreViewModel(app) {

    val presets = app.presets

    /** 보고 있는 달 ("2026-11"), 고른 날 ("2026-11-19"). 화면 회전에도 유지. */
    val month: StateFlow<String> = handle.getStateFlow(KEY_MONTH, YearMonth.now().toString())
    val selected: StateFlow<String> = handle.getStateFlow(KEY_SELECTED, LocalDate.now().toString())

    /** 공휴일을 새로 받으면 바뀜 → 화면이 다시 계산 */
    val holidayUpdates = app.holidays.updates

    fun holidays() = app.holidays.calendar(data.value.customHolidays)

    fun shiftMonth(by: Long) {
        handle[KEY_MONTH] = YearMonth.parse(month.value).plusMonths(by).toString()
    }

    fun goToday() {
        handle[KEY_MONTH] = YearMonth.now().toString()
        handle[KEY_SELECTED] = LocalDate.now().toString()
    }

    fun select(date: LocalDate) {
        handle[KEY_SELECTED] = date.toString()
        if (YearMonth.from(date).toString() != month.value) handle[KEY_MONTH] = YearMonth.from(date).toString()
    }

    fun hideHoliday(date: LocalDate) = update { HolidayOps.hideBuiltIn(it, date) }

    companion object {
        private const val KEY_MONTH = "month"
        private const val KEY_SELECTED = "selected"
        val Factory = appViewModelFactory { app, handle -> CalendarViewModel(app, handle) }
    }
}

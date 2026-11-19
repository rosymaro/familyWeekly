package io.github.rosymaro.familyweekly.ui.home

import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.Orphans
import io.github.rosymaro.familyweekly.domain.ScheduleOps
import io.github.rosymaro.familyweekly.domain.mapSchedule
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory

class HomeViewModel(app: FamilyWeeklyApp) : StoreViewModel(app) {

    val loadIssue = app.store.loadIssue
    val presets = app.presets

    fun holidays() = app.holidays.calendar(data.value.customHolidays)

    fun clearLoadIssue() = app.store.clearLoadIssue()

    fun add(name: String, emoji: String?, days: DaysMode) = update {
        ScheduleOps.add(it, name, days, emoji = emoji?.trim()?.ifEmpty { null }).first
    }

    fun rename(id: String, name: String) = update { ScheduleOps.rename(it, id, name) }

    fun duplicate(id: String, name: String, attach: Boolean) = update {
        ScheduleOps.duplicate(it, id, name, attach)?.first ?: it
    }

    fun orphansIfDeleted(id: String): Orphans = ScheduleOps.orphansIfDeleted(data.value, id)

    fun delete(id: String, removeOrphans: Boolean) = update { ScheduleOps.delete(it, id, removeOrphans) }

    fun move(id: String, toIndex: Int) = update { ScheduleOps.move(it, id, toIndex) }

    fun setTheme(id: String, themeId: String) = update { it.mapSchedule(id) { s -> s.copy(themeId = themeId) } }

    fun toggleAlarms(id: String) = update { it.mapSchedule(id) { s -> s.copy(alarmsEnabled = !s.alarmsEnabled) } }

    companion object {
        val Factory = appViewModelFactory { app, _ -> HomeViewModel(app) }
    }
}

package io.github.rosymaro.familyweekly.ui.week

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.BlockDraft
import io.github.rosymaro.familyweekly.domain.BlockOps
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.mapSchedule
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import io.github.rosymaro.familyweekly.ui.navigation.WeekRoute
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate

class WeekViewModel(app: FamilyWeeklyApp, private val handle: SavedStateHandle) : StoreViewModel(app) {

    val scheduleId: String = handle.toRoute<WeekRoute>().scheduleId
    val presets = app.presets

    /** 이번 주 기준 몇 주 앞/뒤 (화면 회전에도 유지). */
    val weekOffset: StateFlow<Long> = handle.getStateFlow(KEY_OFFSET, 0L)

    fun holidays() = app.holidays.calendar(data.value.customHolidays)

    fun shiftWeek(by: Long) {
        handle[KEY_OFFSET] = weekOffset.value + by
    }

    fun goToday() {
        handle[KEY_OFFSET] = 0L
    }

    fun saveBlock(draft: BlockDraft, editingBlockId: String?, applyToGroup: Boolean) =
        update { BlockOps.save(it, scheduleId, draft, editingBlockId, applyToGroup) }

    fun deleteBlock(blockId: String, wholeGroup: Boolean) =
        update { BlockOps.delete(it, scheduleId, blockId, wholeGroup) }

    fun moveBlock(blockId: String, day: DayOfWeek, startMin: Int) =
        update { BlockOps.move(it, scheduleId, blockId, day, startMin) }

    fun toggleCancellation(blockId: String, date: LocalDate) =
        update { BlockOps.toggleCancellation(it, scheduleId, blockId, date) }

    fun updateSchedule(transform: (Schedule) -> Schedule) =
        update { it.mapSchedule(scheduleId, transform) }

    companion object {
        private const val KEY_OFFSET = "weekOffset"
        val Factory = appViewModelFactory { app, handle -> WeekViewModel(app, handle) }
    }
}

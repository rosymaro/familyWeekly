package io.github.rosymaro.familyweekly.ui.week

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.BlockDraft
import io.github.rosymaro.familyweekly.domain.BlockOps
import io.github.rosymaro.familyweekly.domain.EventOps
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

    /** 공휴일을 새로 받으면 바뀜 → 화면이 다시 계산 */
    val holidayUpdates = app.holidays.updates

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

    /** 포스트잇 떼기/다시 붙이기 (이 아이 시간표에서만, 특이 일정은 남음) */
    fun setDetached(eventId: String, detached: Boolean) =
        update { EventOps.setDetached(it, eventId, scheduleId, detached) }

    fun updateSchedule(transform: (Schedule) -> Schedule) =
        update { it.mapSchedule(scheduleId, transform) }

    companion object {
        private const val KEY_OFFSET = "weekOffset"
        val Factory = appViewModelFactory { app, handle -> WeekViewModel(app, handle) }
    }
}

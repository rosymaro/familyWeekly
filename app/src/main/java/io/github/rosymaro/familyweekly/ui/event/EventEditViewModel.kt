package io.github.rosymaro.familyweekly.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.EventOps
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.Vacation
import io.github.rosymaro.familyweekly.domain.VacationOps
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import io.github.rosymaro.familyweekly.ui.navigation.EventEditRoute
import io.github.rosymaro.familyweekly.ui.navigation.VacationEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID

class EventEditViewModel(app: FamilyWeeklyApp, handle: SavedStateHandle) : StoreViewModel(app) {
    private val route = handle.toRoute<EventEditRoute>()
    val presets = app.presets
    private val existing = route.eventId?.let { id -> data.value.specialEvents.firstOrNull { it.id == id } }
    val isNew = existing == null

    private val _draft = MutableStateFlow(
        existing ?: SpecialEvent(
            id = UUID.randomUUID().toString(),
            title = "",
            date = route.date?.let(LocalDate::parse) ?: LocalDate.now(),
            scheduleIds = route.scheduleId?.let { listOf(it) }
                ?: data.value.schedules.singleOrNull()?.let { listOf(it.id) }.orEmpty(),
            startMin = 15 * 60,
            endMin = 16 * 60,
        ),
    )
    val draft: StateFlow<SpecialEvent> = _draft.asStateFlow()

    fun edit(transform: (SpecialEvent) -> SpecialEvent) {
        _draft.value = transform(_draft.value)
    }

    fun save(onDone: () -> Unit) {
        val e = _draft.value
        if (EventOps.canSave(data.value, e) != null) return
        update { EventOps.save(it, e) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        update { EventOps.delete(it, _draft.value.id) }
        onDone()
    }

    companion object {
        val Factory = appViewModelFactory { app, handle -> EventEditViewModel(app, handle) }
    }
}

class VacationEditViewModel(app: FamilyWeeklyApp, handle: SavedStateHandle) : StoreViewModel(app) {
    private val route = handle.toRoute<VacationEditRoute>()
    private val existing = route.vacationId?.let { id -> data.value.vacations.firstOrNull { it.id == id } }
    val isNew = existing == null

    private val _draft = MutableStateFlow(
        existing ?: run {
            val start = route.date?.let(LocalDate::parse) ?: LocalDate.now()
            Vacation(
                id = UUID.randomUUID().toString(),
                title = "",
                from = start,
                to = start,
                scheduleIds = data.value.schedules.map { it.id },
            )
        },
    )
    val draft: StateFlow<Vacation> = _draft.asStateFlow()

    fun edit(transform: (Vacation) -> Vacation) {
        _draft.value = transform(_draft.value)
    }

    fun save(onDone: () -> Unit) {
        val v = _draft.value
        if (VacationOps.validate(VacationOps.normalize(data.value, v)) != null) return
        update { VacationOps.save(it, v) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        update { VacationOps.delete(it, _draft.value.id) }
        onDone()
    }

    companion object {
        val Factory = appViewModelFactory { app, handle -> VacationEditViewModel(app, handle) }
    }
}

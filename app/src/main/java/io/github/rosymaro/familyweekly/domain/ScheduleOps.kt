package io.github.rosymaro.familyweekly.domain

import java.util.UUID

/** 새 id 만들기. 테스트에서는 순서대로 나오는 생성기를 넣는다. */
fun interface IdGenerator {
    fun next(): String

    companion object {
        val UUIDS = IdGenerator { UUID.randomUUID().toString() }
    }
}

/** 스케줄을 지우면 대상이 하나도 안 남는 특이 일정·휴가 (삭제 전에 "같이 지울까요?"를 묻는 데 씀). */
data class Orphans(
    val eventIds: List<String>,
    val vacationIds: List<String>,
) {
    val isEmpty: Boolean get() = eventIds.isEmpty() && vacationIds.isEmpty()
}

/** 스케줄 단위 조작. 모두 AppData를 받아 새 AppData를 돌려주는 순수 함수. */
object ScheduleOps {

    fun add(
        data: AppData,
        name: String,
        days: DaysMode = DaysMode.MON_FRI,
        themeId: String? = null,
        emoji: String? = null,
        ids: IdGenerator = IdGenerator.UUIDS,
    ): Pair<AppData, Schedule> {
        val schedule = Schedule(id = ids.next(), name = name.trim(), emoji = emoji, days = days, themeId = themeId)
        return data.copy(schedules = data.schedules + schedule) to schedule
    }

    fun rename(data: AppData, scheduleId: String, newName: String): AppData =
        data.mapSchedule(scheduleId) { it.copy(name = newName.trim()) }

    /**
     * 깊은 복사: 블록(새 id, groupId도 새로 묶음), 개별 취소, 알림, 테마, 요일, 시간 범위.
     * 특이 일정·휴가는 [attachEventsAndVacations]가 true일 때만 새 스케줄에도 붙인다
     * (특이 일정은 원본의 유지/취소 선택도 그대로, 블록 지정 휴가는 복사된 블록 id도 추가).
     * 위젯 연결은 복사하지 않는다. 복사본은 원본 바로 뒤에 놓는다.
     */
    fun duplicate(
        data: AppData,
        scheduleId: String,
        newName: String? = null,
        attachEventsAndVacations: Boolean = false,
        ids: IdGenerator = IdGenerator.UUIDS,
    ): Pair<AppData, Schedule>? {
        val source = data.schedule(scheduleId) ?: return null
        val newId = ids.next()

        val blockIdMap = source.blocks.associate { it.id to ids.next() }
        val groupIdMap = source.blocks.mapNotNull { it.groupId }.distinct().associateWith { ids.next() }

        val copy = source.copy(
            id = newId,
            name = (newName ?: "${source.name} 복사본").trim(),
            blocks = source.blocks.map { b ->
                b.copy(id = blockIdMap.getValue(b.id), groupId = b.groupId?.let { groupIdMap.getValue(it) })
            },
            cancellations = source.cancellations.mapNotNull { c ->
                blockIdMap[c.blockId]?.let { c.copy(blockId = it) }
            },
        )

        val index = data.schedules.indexOfFirst { it.id == scheduleId }
        val schedules = data.schedules.toMutableList().apply { add(index + 1, copy) }

        var result = data.copy(schedules = schedules)
        if (attachEventsAndVacations) {
            result = result.copy(
                specialEvents = result.specialEvents.map { ev ->
                    if (scheduleId !in ev.scheduleIds) ev
                    else ev.copy(
                        scheduleIds = ev.scheduleIds + newId,
                        overlapActions = ev.overlapActions[scheduleId]
                            ?.let { ev.overlapActions + (newId to it) } ?: ev.overlapActions,
                    )
                },
                vacations = result.vacations.map { v ->
                    if (scheduleId !in v.scheduleIds) v
                    else v.copy(
                        scheduleIds = v.scheduleIds + newId,
                        blockIds = v.blockIds?.let { list -> list + list.mapNotNull { blockIdMap[it] } },
                    )
                },
            )
        }
        return result to copy
    }

    /** 이 스케줄을 지우면 대상이 없어지는 특이 일정·휴가. */
    fun orphansIfDeleted(data: AppData, scheduleId: String): Orphans = Orphans(
        eventIds = data.specialEvents.filter { it.scheduleIds == listOf(scheduleId) }.map { it.id },
        vacationIds = data.vacations.filter { it.scheduleIds == listOf(scheduleId) }.map { it.id },
    )

    /**
     * 스케줄 삭제 + 참조 정리:
     * - 특이 일정·휴가의 scheduleIds / overlapActions / blockIds에서 제거
     * - 대상이 0개가 된 특이 일정·휴가는 [removeOrphans]가 true면 삭제, false면 대상 없이 남김
     * - 이 스케줄에 연결된 위젯은 scheduleId = null ("연결된 스케줄이 없어요" 상태)
     */
    fun delete(data: AppData, scheduleId: String, removeOrphans: Boolean): AppData {
        val schedule = data.schedule(scheduleId) ?: return data
        val blockIds = schedule.blocks.map { it.id }.toSet()

        val events = data.specialEvents
            .map { ev ->
                if (scheduleId !in ev.scheduleIds) ev
                else ev.copy(scheduleIds = ev.scheduleIds - scheduleId, overlapActions = ev.overlapActions - scheduleId)
            }
            .filterNot { removeOrphans && it.scheduleIds.isEmpty() && it.id in data.specialEvents.affectedBy(scheduleId) }

        val vacations = data.vacations
            .map { v ->
                if (scheduleId !in v.scheduleIds) v
                else v.copy(scheduleIds = v.scheduleIds - scheduleId, blockIds = v.blockIds?.filterNot { it in blockIds })
            }
            .filterNot { removeOrphans && it.scheduleIds.isEmpty() && it.id in data.vacations.affectedVacations(scheduleId) }

        return data.copy(
            schedules = data.schedules.filterNot { it.id == scheduleId },
            specialEvents = events,
            vacations = vacations,
            widgets = data.widgets.map { if (it.scheduleId == scheduleId) it.copy(scheduleId = null) else it },
        )
    }

    /** 홈 목록 순서 바꾸기. [toIndex]는 옮긴 뒤 위치. */
    fun move(data: AppData, scheduleId: String, toIndex: Int): AppData {
        val from = data.schedules.indexOfFirst { it.id == scheduleId }
        if (from < 0) return data
        val list = data.schedules.toMutableList()
        val item = list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), item)
        return data.copy(schedules = list)
    }

    private fun List<SpecialEvent>.affectedBy(scheduleId: String): Set<String> =
        filter { scheduleId in it.scheduleIds }.map { it.id }.toSet()

    private fun List<Vacation>.affectedVacations(scheduleId: String): Set<String> =
        filter { scheduleId in it.scheduleIds }.map { it.id }.toSet()
}

/** 스케줄 하나만 바꾸기. */
fun AppData.mapSchedule(scheduleId: String, transform: (Schedule) -> Schedule): AppData =
    copy(schedules = schedules.map { if (it.id == scheduleId) transform(it) else it })

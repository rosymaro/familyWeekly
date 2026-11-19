package io.github.rosymaro.familyweekly.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** 블록 수정 시트에서 입력한 값. */
data class BlockDraft(
    val title: String,
    val days: Set<DayOfWeek>,
    val startMin: Int,
    val endMin: Int,
    val colorKey: String = "default",
    val place: String? = null,
    val memo: String? = null,
)

/** 반복 블록 추가·수정·삭제·이동·개별 취소. 모두 순수 함수. */
object BlockOps {

    /** 저장할 수 없으면 이유(한국어), 괜찮으면 null. */
    fun validate(d: BlockDraft): String? = when {
        d.title.isBlank() -> "제목을 입력해 주세요"
        d.days.isEmpty() -> "요일을 하나 이상 골라 주세요"
        !TimeStep.isOnStep(d.startMin) || !TimeStep.isOnStep(d.endMin) -> "시각은 5분 단위여야 해요"
        d.startMin < 0 || d.endMin > TimeStep.MINUTES_PER_DAY -> "하루 안의 시각이어야 해요"
        d.endMin <= d.startMin -> "끝 시각이 시작보다 늦어야 해요"
        else -> null
    }

    /** 같은 그룹(여러 요일로 만든 같은 일정) 블록들. 그룹이 없으면 자기 자신만. */
    fun groupOf(schedule: Schedule, blockId: String): List<Block> {
        val block = schedule.blocks.firstOrNull { it.id == blockId } ?: return emptyList()
        val gid = block.groupId ?: return listOf(block)
        return schedule.blocks.filter { it.groupId == gid }
    }

    /**
     * 저장.
     * - [editingBlockId]가 null이면 새로 만든다: 요일마다 블록 하나, 요일이 여럿이면 같은 groupId.
     * - 수정 + [applyToGroup]: 그룹 전체에 같은 내용. 고른 요일에 없던 건 새로 만들고, 빠진 요일은 지운다.
     * - 수정 + !applyToGroup: 이 블록만 바꾼다 (요일은 [BlockDraft.days]의 첫 번째).
     */
    fun save(
        data: AppData,
        scheduleId: String,
        draft: BlockDraft,
        editingBlockId: String?,
        applyToGroup: Boolean = true,
        ids: IdGenerator = IdGenerator.UUIDS,
    ): AppData {
        require(validate(draft) == null) { validate(draft)!! }
        val schedule = data.schedule(scheduleId) ?: return data
        val days = draft.days.sorted()

        fun Block.withDraft(day: DayOfWeek) = copy(
            title = draft.title.trim(),
            day = day,
            startMin = draft.startMin,
            endMin = draft.endMin,
            colorKey = draft.colorKey,
            place = draft.place?.trim()?.ifEmpty { null },
            memo = draft.memo?.trim()?.ifEmpty { null },
        )

        if (editingBlockId == null) {
            val gid = if (days.size > 1) ids.next() else null
            val created = days.map { day -> Block(id = ids.next(), title = "", day = day, startMin = 0, endMin = 0, groupId = gid).withDraft(day) }
            return data.mapSchedule(scheduleId) { it.copy(blocks = it.blocks + created) }
        }

        val editing = schedule.blocks.firstOrNull { it.id == editingBlockId } ?: return data
        if (!applyToGroup) {
            return data.mapSchedule(scheduleId) { s ->
                s.copy(blocks = s.blocks.map { if (it.id == editingBlockId) it.withDraft(days.first()) else it })
            }
        }

        val members = groupOf(schedule, editingBlockId)
        val gid = editing.groupId ?: if (days.size > 1) ids.next() else null
        val byDay = members.groupBy { it.day }
        val keep = mutableListOf<Block>()
        for (day in days) {
            val existing = byDay[day]?.firstOrNull() ?: if (day == editing.day) editing else null
            keep += (existing ?: Block(id = ids.next(), title = "", day = day, startMin = 0, endMin = 0))
                .withDraft(day).copy(groupId = gid)
        }
        val keepIds = keep.map { it.id }.toSet()
        val removed = members.map { it.id }.filterNot { it in keepIds }.toSet()

        // 원래 순서를 지키면서 바꾸기
        val replaced = schedule.blocks.mapNotNull { b ->
            when {
                b.id in removed -> null
                b.id in keepIds -> keep.first { it.id == b.id }
                else -> b
            }
        } + keep.filter { k -> schedule.blocks.none { it.id == k.id } }

        return removeReferences(data.mapSchedule(scheduleId) { it.copy(blocks = replaced) }, scheduleId, removed)
    }

    /** 삭제. [wholeGroup]이면 같은 그룹 블록 전부. 개별 취소·휴가의 블록 지정도 정리한다. */
    fun delete(data: AppData, scheduleId: String, blockId: String, wholeGroup: Boolean): AppData {
        val schedule = data.schedule(scheduleId) ?: return data
        val targets = (if (wholeGroup) groupOf(schedule, blockId) else schedule.blocks.filter { it.id == blockId })
            .map { it.id }.toSet()
        if (targets.isEmpty()) return data
        val updated = data.mapSchedule(scheduleId) { s -> s.copy(blocks = s.blocks.filterNot { it.id in targets }) }
        return removeReferences(updated, scheduleId, targets)
    }

    /**
     * 드래그 이동: 요일과 시작 시각을 바꾼다 (길이 유지, 5분 스냅, 하루 안으로).
     * 매주 반복되는 블록 자체를 옮긴다 (이번 주만 옮기기 없음).
     */
    fun move(data: AppData, scheduleId: String, blockId: String, day: DayOfWeek, startMin: Int): AppData =
        data.mapSchedule(scheduleId) { s ->
            s.copy(blocks = s.blocks.map { b ->
                if (b.id != blockId) b
                else {
                    val duration = b.endMin - b.startMin
                    val start = TimeStep.snap(startMin).coerceIn(0, TimeStep.MINUTES_PER_DAY - duration)
                    b.copy(day = day, startMin = start, endMin = start + duration)
                }
            })
        }

    /** "이번 주만 취소" 켜고 끄기. */
    fun toggleCancellation(data: AppData, scheduleId: String, blockId: String, date: LocalDate): AppData =
        data.mapSchedule(scheduleId) { s ->
            val exists = s.cancellations.any { it.blockId == blockId && it.date == date }
            s.copy(
                cancellations = if (exists) s.cancellations.filterNot { it.blockId == blockId && it.date == date }
                else s.cancellations + ManualCancellation(blockId, date),
            )
        }

    fun isCancelled(schedule: Schedule, blockId: String, date: LocalDate): Boolean =
        schedule.cancellations.any { it.blockId == blockId && it.date == date }

    private fun removeReferences(data: AppData, scheduleId: String, blockIds: Set<String>): AppData {
        if (blockIds.isEmpty()) return data
        return data
            .mapSchedule(scheduleId) { s -> s.copy(cancellations = s.cancellations.filterNot { it.blockId in blockIds }) }
            .copy(vacations = data.vacations.map { v -> v.blockIds?.let { v.copy(blockIds = it - blockIds) } ?: v })
    }
}

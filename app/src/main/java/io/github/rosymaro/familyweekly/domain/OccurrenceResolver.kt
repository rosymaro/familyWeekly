package io.github.rosymaro.familyweekly.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 블록 하나가 특정 날짜에 실제로 어떤 상태인지. */
enum class OccurrenceStatus { ACTIVE, CANCELLED }

/**
 * 취소 사유. 선언 순서 = 표시 우선순위 (앞이 높음): MANUAL > EVENT > VACATION > HOLIDAY.
 */
enum class CancelReason { MANUAL, EVENT, VACATION, HOLIDAY }

/** 그 주의 한 날짜에 나타난 반복 블록. */
data class Occurrence(
    val block: Block,
    val date: LocalDate,
    val status: OccurrenceStatus,
    /** 모든 취소 사유 (비어 있으면 ACTIVE). */
    val reasons: Set<CancelReason>,
    /** 이 블록과 시간이 겹치는 특이 일정 id (KEEP/CANCEL 모두). */
    val overlappingEventIds: List<String>,
    /** 이 블록을 취소시킨 특이 일정 id (CANCEL만). */
    val cancellingEventIds: List<String>,
    /** 취소시킨 휴가 id. */
    val vacationIds: List<String>,
    /** 지금 기준 이미 끝났는지 (now를 넘겼을 때만 계산). */
    val isPast: Boolean,
) {
    val startMin: Int get() = block.startMin
    val endMin: Int get() = block.endMin

    /** 여러 사유 중 표시할 하나 (우선순위 순). */
    val displayReason: CancelReason? get() = reasons.minByOrNull { it.ordinal }
}

/** 포스트잇 아래 블록 상태 (DESIGN.md postIt.underBadge). */
enum class UnderState {
    /** 겹치는 블록 없음. */
    NONE,

    /** 겹치는 블록을 유지. */
    KEEP,

    /** 겹치는 블록을 취소. */
    CANCEL,
}

/** 이 스케줄에 붙은 특이 일정 하나. */
data class EventOccurrence(
    val event: SpecialEvent,
    val overlappedBlockIds: List<String>,
    val underState: UnderState,
    val isPast: Boolean,
)

data class DayInfo(
    val date: LocalDate,
    val holiday: Holiday?,
    /** 이 스케줄에 적용되는 그날의 휴가. */
    val vacations: List<Vacation>,
    val isToday: Boolean,
)

/** 스케줄 하나의 한 주 계산 결과. 레이아웃(Phase 2)과 알림(Phase 6)이 이것만 보고 그린다/울린다. */
data class ResolvedWeek(
    val schedule: Schedule,
    val week: WeekRange,
    val days: List<DayInfo>,
    /** 날짜, 시작 시각 순. */
    val occurrences: List<Occurrence>,
    /** 날짜, 시작 시각 순 (하루 종일이 먼저). */
    val events: List<EventOccurrence>,
    /** 표시할 시간 범위 [startMin, endMin). */
    val displayStartMin: Int,
    val displayEndMin: Int,
    /** 이 스케줄에서 포스트잇을 뗀 그 주 특이 일정 (그리지 않음, "다시 붙이기" 목록용). 유지/취소 효과는 그대로. */
    val detachedEvents: List<SpecialEvent> = emptyList(),
)

/**
 * 반복 블록 + 휴가 + 개별 취소 + 특이 일정 + 공휴일 → 한 주의 실제 상태.
 * 취소 상태는 저장하지 않고 매번 여기서 계산한다 (특이 일정을 지우면 취소도 자동으로 풀림).
 */
object OccurrenceResolver {

    const val DEFAULT_START_MIN = 9 * 60
    const val DEFAULT_END_MIN = 18 * 60

    fun resolve(
        data: AppData,
        scheduleId: String,
        week: WeekRange,
        holidays: HolidayCalendar = HolidayCalendar.EMPTY,
        now: LocalDateTime? = null,
    ): ResolvedWeek? {
        val schedule = data.schedule(scheduleId) ?: return null
        val dates = week.dates(schedule.days)
        val vacations = data.vacations.filter { scheduleId in it.scheduleIds }
        val events = data.specialEvents.filter { scheduleId in it.scheduleIds && it.date in week }

        val days = dates.map { date ->
            DayInfo(
                date = date,
                holiday = holidays.holidayOn(date),
                vacations = vacations.filter { it.covers(date) },
                isToday = now != null && now.toLocalDate() == date,
            )
        }

        val occurrences = dates.flatMap { date ->
            schedule.blocks
                .filter { it.day == date.dayOfWeek && it.isValidOn(date) }
                .map { block -> resolveBlock(schedule, block, date, vacations, events, holidays, now) }
        }.sortedWith(compareBy({ it.date }, { it.startMin }, { it.endMin }))

        val shownDates = dates.toSet()
        val eventOccurrences = events
            .filter { it.date in shownDates && !it.isDetachedFrom(scheduleId) }
            .map { ev ->
                val overlapped = schedule.blocks
                    .filter { it.day == ev.date.dayOfWeek && it.isValidOn(ev.date) && ev.overlaps(it.startMin, it.endMin) }
                    .map { it.id }
                val under = when {
                    overlapped.isEmpty() -> UnderState.NONE
                    ev.overlapActionFor(scheduleId) == OverlapAction.CANCEL -> UnderState.CANCEL
                    else -> UnderState.KEEP
                }
                EventOccurrence(ev, overlapped, under, isPast(ev.date, if (ev.allDay) TimeStep.MINUTES_PER_DAY else ev.endMin, now))
            }
            .sortedWith(compareBy({ it.event.date }, { !it.event.allDay }, { it.event.startMin }))

        val (start, end) = displayRange(schedule.timeRange, occurrences, eventOccurrences)
        val detached = events.filter { it.date in shownDates && it.isDetachedFrom(scheduleId) }
            .sortedWith(compareBy({ it.date }, { !it.allDay }, { it.startMin }))
        return ResolvedWeek(schedule, week, days, occurrences, eventOccurrences, start, end, detached)
    }

    private fun resolveBlock(
        schedule: Schedule,
        block: Block,
        date: LocalDate,
        vacations: List<Vacation>,
        events: List<SpecialEvent>,
        holidays: HolidayCalendar,
        now: LocalDateTime?,
    ): Occurrence {
        val reasons = mutableSetOf<CancelReason>()

        if (schedule.cancellations.any { it.blockId == block.id && it.date == date }) {
            reasons += CancelReason.MANUAL
        }

        val overlapping = events.filter { it.date == date && it.overlaps(block.startMin, block.endMin) }
        val cancelling = overlapping.filter { it.overlapActionFor(schedule.id) == OverlapAction.CANCEL }
        if (cancelling.isNotEmpty()) reasons += CancelReason.EVENT

        val vacationHits = vacations.filter { v ->
            v.covers(date) && (v.blockIds == null || block.id in v.blockIds)
        }
        if (vacationHits.isNotEmpty()) reasons += CancelReason.VACATION

        if (schedule.holidayPolicy == HolidayPolicy.CANCEL_BLOCKS && holidays.isHoliday(date)) {
            reasons += CancelReason.HOLIDAY
        }

        return Occurrence(
            block = block,
            date = date,
            status = if (reasons.isEmpty()) OccurrenceStatus.ACTIVE else OccurrenceStatus.CANCELLED,
            reasons = reasons,
            overlappingEventIds = overlapping.map { it.id },
            cancellingEventIds = cancelling.map { it.id },
            vacationIds = vacationHits.map { it.id },
            isPast = isPast(date, block.endMin, now),
        )
    }

    private fun isPast(date: LocalDate, endMin: Int, now: LocalDateTime?): Boolean {
        if (now == null) return false
        val today = now.toLocalDate()
        return when {
            date.isBefore(today) -> true
            date.isAfter(today) -> false
            else -> endMin <= TimeStep.fromLocalTime(now.toLocalTime())
        }
    }

    /**
     * AUTO: 보이는 블록(취소 포함)·시각 있는 특이 일정의 최소 시작을 정시로 내림, 최대 끝을 정시로 올림.
     *       아무것도 없으면 9:00~18:00.
     * FIXED: 사용자가 정한 값 그대로 (잘림 표시는 레이아웃에서).
     */
    fun displayRange(
        range: TimeRange,
        occurrences: List<Occurrence>,
        events: List<EventOccurrence>,
    ): Pair<Int, Int> {
        if (range.mode == TimeRangeMode.FIXED) {
            val start = range.startMin.coerceIn(0, TimeStep.MINUTES_PER_DAY - TimeStep.STEP_MIN)
            val end = range.endMin.coerceIn(start + TimeStep.STEP_MIN, TimeStep.MINUTES_PER_DAY)
            return start to end
        }
        val spans = occurrences.map { it.startMin to it.endMin } +
            events.filter { !it.event.allDay }.map { it.event.startMin to it.event.endMin }
        if (spans.isEmpty()) return DEFAULT_START_MIN to DEFAULT_END_MIN
        val start = TimeStep.floorToHour(spans.minOf { it.first })
        val end = TimeStep.ceilToHour(spans.maxOf { it.second }).coerceAtMost(TimeStep.MINUTES_PER_DAY)
        return start to maxOf(end, start + 60)
    }

    /**
     * 특이 일정을 저장하기 전에 "기존 일정 유지/취소"를 물어볼 블록 목록 (스케줄 id → 겹치는 블록).
     * 겹치는 블록이 없는 스케줄은 빠진다.
     */
    fun overlappingBlocks(data: AppData, event: SpecialEvent): Map<String, List<Block>> =
        event.scheduleIds.mapNotNull { sid ->
            val schedule = data.schedule(sid) ?: return@mapNotNull null
            val blocks = schedule.blocks.filter {
                it.day == event.date.dayOfWeek && it.isValidOn(event.date) && event.overlaps(it.startMin, it.endMin)
            }
            if (blocks.isEmpty()) null else sid to blocks.sortedBy { it.startMin }
        }.toMap()
}

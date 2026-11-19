package io.github.rosymaro.familyweekly.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 특이 일정 저장·삭제와 "기존 일정 유지/취소" 확인. */
object EventOps {

    /** 저장할 수 없으면 이유(한국어). */
    fun validate(e: SpecialEvent): String? = when {
        e.title.isBlank() -> "제목을 입력해 주세요"
        e.scheduleIds.isEmpty() -> "붙일 스케줄(아이)을 하나 이상 골라 주세요"
        !e.allDay && (!TimeStep.isOnStep(e.startMin) || !TimeStep.isOnStep(e.endMin)) -> "시각은 5분 단위여야 해요"
        !e.allDay && e.endMin <= e.startMin -> "끝 시각이 시작보다 늦어야 해요"
        !e.allDay && (e.startMin < 0 || e.endMin > TimeStep.MINUTES_PER_DAY) -> "하루 안의 시각이어야 해요"
        else -> null
    }

    /**
     * 겹치는 기존 블록이 있는데 "유지/취소"를 아직 고르지 않은 스케줄.
     * 비어 있어야 저장할 수 있다 (SPEC 4절: 기본값 없이 반드시 묻는다).
     */
    fun missingChoices(data: AppData, e: SpecialEvent): List<String> =
        OccurrenceResolver.overlappingBlocks(data, e).keys.filter { it !in e.overlapActions }

    /** 저장 전에 정리: 붙이지 않은 스케줄·겹치지 않는 스케줄의 선택은 지운다. */
    fun normalize(data: AppData, e: SpecialEvent): SpecialEvent {
        val overlapping = OccurrenceResolver.overlappingBlocks(data, e).keys
        return e.copy(
            title = e.title.trim(),
            memo = e.memo?.trim()?.ifEmpty { null },
            scheduleIds = e.scheduleIds.distinct().filter { data.schedule(it) != null },
            overlapActions = e.overlapActions.filterKeys { it in overlapping && it in e.scheduleIds },
            startMin = if (e.allDay) 0 else e.startMin,
            endMin = if (e.allDay) 0 else e.endMin,
        )
    }

    fun canSave(data: AppData, e: SpecialEvent): String? =
        validate(e) ?: if (missingChoices(data, e).isNotEmpty()) "겹치는 기존 일정을 유지할지 취소할지 골라 주세요" else null

    /** 새로 넣거나 같은 id를 바꾼다. */
    fun save(data: AppData, e: SpecialEvent): AppData {
        canSave(data, e)?.let { throw IllegalArgumentException(it) }
        val n = normalize(data, e)
        val list = data.specialEvents
        val replaced = if (list.any { it.id == n.id }) list.map { if (it.id == n.id) n else it } else list + n
        return data.copy(specialEvents = replaced.sortedWith(compareBy({ it.date }, { !it.allDay }, { it.startMin })))
    }

    fun delete(data: AppData, id: String): AppData = data.copy(specialEvents = data.specialEvents.filterNot { it.id == id })

    fun on(data: AppData, date: LocalDate): List<SpecialEvent> =
        data.specialEvents.filter { it.date == date }.sortedWith(compareBy({ !it.allDay }, { it.startMin }))
}

object VacationOps {

    fun validate(v: Vacation): String? = when {
        v.title.isBlank() -> "제목을 입력해 주세요"
        v.to.isBefore(v.from) -> "끝나는 날이 시작하는 날보다 앞이에요"
        v.scheduleIds.isEmpty() -> "적용할 스케줄(아이)을 하나 이상 골라 주세요"
        v.blockIds != null && v.blockIds.isEmpty() -> "취소할 일정을 하나 이상 골라 주세요"
        else -> null
    }

    /** 고른 스케줄에 없는 블록 지정은 뺀다. */
    fun normalize(data: AppData, v: Vacation): Vacation {
        val validBlocks = v.scheduleIds.flatMap { id -> data.schedule(id)?.blocks.orEmpty().map { it.id } }.toSet()
        return v.copy(
            title = v.title.trim(),
            scheduleIds = v.scheduleIds.distinct().filter { data.schedule(it) != null },
            blockIds = v.blockIds?.filter { it in validBlocks },
        )
    }

    fun save(data: AppData, v: Vacation): AppData {
        validate(v)?.let { throw IllegalArgumentException(it) }
        val n = normalize(data, v)
        validate(n)?.let { throw IllegalArgumentException(it) }
        val list = data.vacations
        val replaced = if (list.any { it.id == n.id }) list.map { if (it.id == n.id) n else it } else list + n
        return data.copy(vacations = replaced.sortedBy { it.from })
    }

    fun delete(data: AppData, id: String): AppData = data.copy(vacations = data.vacations.filterNot { it.id == id })

    fun covering(data: AppData, date: LocalDate): List<Vacation> = data.vacations.filter { it.covers(date) }
}

/** 사용자 공휴일: 기본 공휴일 숨기기/다시 보이기, 직접 추가/삭제. 날짜마다 항목 하나. */
object HolidayOps {

    fun hideBuiltIn(data: AppData, date: LocalDate): AppData =
        data.copy(customHolidays = data.customHolidays.filterNot { it.date == date } + CustomHoliday(date, hidden = true))

    /** 숨긴 기본 공휴일 다시 보이기, 또는 직접 추가한 공휴일 삭제. */
    fun removeCustom(data: AppData, date: LocalDate): AppData =
        data.copy(customHolidays = data.customHolidays.filterNot { it.date == date })

    fun add(data: AppData, date: LocalDate, name: String): AppData {
        require(name.isNotBlank()) { "이름을 입력해 주세요" }
        return data.copy(
            customHolidays = (data.customHolidays.filterNot { it.date == date } + CustomHoliday(date, name.trim()))
                .sortedBy { it.date },
        )
    }

    fun isHidden(data: AppData, date: LocalDate): Boolean = data.customHolidays.any { it.date == date && it.hidden }
}

/** 월 달력 한 칸. */
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val holiday: Holiday?,
    val events: List<SpecialEvent>,
    val vacations: List<Vacation>,
)

/** 월 달력 (월요일 시작, 6주 고정 — 달마다 높이가 바뀌지 않게). */
object CalendarMonth {

    fun weeks(month: YearMonth, data: AppData, holidays: HolidayCalendar, today: LocalDate): List<List<CalendarDay>> {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val eventsByDate = data.specialEvents.groupBy { it.date }
        return (0 until 6).map { w ->
            (0 until 7).map { d ->
                val date = first.plusDays((w * 7 + d).toLong())
                CalendarDay(
                    date = date,
                    inMonth = YearMonth.from(date) == month,
                    isToday = date == today,
                    holiday = holidays.holidayOn(date),
                    events = eventsByDate[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.startMin })),
                    vacations = data.vacations.filter { it.covers(date) },
                )
            }
        }
    }
}

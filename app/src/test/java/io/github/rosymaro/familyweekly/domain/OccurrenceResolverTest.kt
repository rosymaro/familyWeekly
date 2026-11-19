package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.domain.TestData.MONDAY
import io.github.rosymaro.familyweekly.domain.TestData.WEEK
import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import io.github.rosymaro.familyweekly.domain.TestData.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY as MON
import java.time.DayOfWeek.SATURDAY as SAT
import java.time.DayOfWeek.WEDNESDAY as WED
import java.time.LocalDate
import java.time.LocalDateTime

class OccurrenceResolverTest {

    private fun resolve(
        d: AppData = data,
        scheduleId: String = "harin",
        week: WeekRange = WEEK,
        holidays: HolidayCalendar = HolidayCalendar.EMPTY,
        now: LocalDateTime? = null,
    ) = OccurrenceResolver.resolve(d, scheduleId, week, holidays, now)!!

    private fun ResolvedWeek.occ(blockId: String, date: LocalDate? = null) =
        occurrences.single { it.block.id == blockId && (date == null || it.date == date) }

    private fun event(
        id: String,
        date: LocalDate,
        start: String,
        end: String,
        schedules: List<String>,
        actions: Map<String, OverlapAction> = emptyMap(),
        allDay: Boolean = false,
    ) = SpecialEvent(
        id = id, title = id, date = date, scheduleIds = schedules,
        startMin = min(start), endMin = min(end), overlapActions = actions, allDay = allDay,
    )

    // ── 기본 펼치기 ──

    @Test
    fun weeklyBlocksAppearOnTheirDayAndAreActive() {
        val r = resolve()
        assertEquals(listOf("piano", "math", "tkd"), r.occurrences.map { it.block.id })
        assertEquals(day(MON), r.occ("piano").date)
        assertEquals(day(WED), r.occ("tkd").date)
        assertTrue(r.occurrences.all { it.status == OccurrenceStatus.ACTIVE && it.reasons.isEmpty() })
    }

    @Test
    fun daysModeLimitsShownDates() {
        assertEquals(5, resolve(scheduleId = "harin").days.size)
        val doyun = resolve(scheduleId = "doyun")
        assertEquals(7, doyun.days.size)
        assertEquals(day(SAT), doyun.occ("soccer").date)
    }

    @Test
    fun weekendBlockHiddenWhenScheduleIsMonFri() {
        val d = data.mapSchedule("harin") {
            it.copy(blocks = it.blocks + TestData.block("sat", SAT, "10:00", "11:00"))
        }
        assertTrue(resolve(d).occurrences.none { it.block.id == "sat" })
    }

    @Test
    fun validFromAndUntilAreInclusive() {
        val d = data.mapSchedule("harin") { s ->
            s.copy(blocks = s.blocks.map {
                when (it.id) {
                    "piano" -> it.copy(validFrom = day(MON))          // 이번 월요일부터 → 보임
                    "math" -> it.copy(validUntil = day(MON).minusDays(1)) // 지난 일요일까지 → 안 보임
                    "tkd" -> it.copy(validUntil = day(WED))           // 이번 수요일까지 → 보임
                    else -> it
                }
            })
        }
        assertEquals(listOf("piano", "tkd"), resolve(d).occurrences.map { it.block.id })
    }

    @Test
    fun unknownScheduleReturnsNull() {
        assertNull(OccurrenceResolver.resolve(data, "nobody", WEEK))
    }

    // ── 휴가 ──

    @Test
    fun vacationBoundariesAreInclusive() {
        // 수요일 하루만 휴가 → 수요일 태권도만 취소, 월요일은 그대로
        val v = Vacation("v1", "할머니댁", from = day(WED), to = day(WED), scheduleIds = listOf("harin"))
        val r = resolve(data.copy(vacations = listOf(v)))
        assertEquals(OccurrenceStatus.CANCELLED, r.occ("tkd").status)
        assertEquals(setOf(CancelReason.VACATION), r.occ("tkd").reasons)
        assertEquals(listOf("v1"), r.occ("tkd").vacationIds)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("piano").status)
        assertEquals(listOf(v), r.days.single { it.date == day(WED) }.vacations)
    }

    @Test
    fun vacationEndingTheDayBeforeDoesNotCancel() {
        val v = Vacation("v1", "방학", from = MONDAY.minusDays(10), to = MONDAY.minusDays(1), scheduleIds = listOf("harin"))
        assertTrue(resolve(data.copy(vacations = listOf(v))).occurrences.all { it.status == OccurrenceStatus.ACTIVE })
    }

    @Test
    fun vacationStartingOnMondayCancelsMonday() {
        val v = Vacation("v1", "방학", from = MONDAY, to = MONDAY.plusDays(30), scheduleIds = listOf("harin"))
        assertTrue(resolve(data.copy(vacations = listOf(v))).occurrences.all { it.status == OccurrenceStatus.CANCELLED })
    }

    @Test
    fun vacationOnlyForChosenBlocksAndSchedules() {
        val v = Vacation(
            "v1", "학원 방학", from = MONDAY, to = MONDAY.plusDays(6),
            scheduleIds = listOf("harin"), blockIds = listOf("math"),
        )
        val d = data.copy(vacations = listOf(v))
        val r = resolve(d)
        assertEquals(OccurrenceStatus.CANCELLED, r.occ("math").status)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("piano").status)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("tkd").status)
        // 다른 스케줄에는 영향 없음
        assertTrue(resolve(d, scheduleId = "doyun").occurrences.all { it.status == OccurrenceStatus.ACTIVE })
    }

    // ── 특이 일정 (포스트잇) ──

    @Test
    fun specialEventKeepLeavesBlockActive() {
        val ev = event("dentist", day(MON), "15:30", "16:15", listOf("harin"), mapOf("harin" to OverlapAction.KEEP))
        val r = resolve(data.copy(specialEvents = listOf(ev)))
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("piano").status)
        assertEquals(listOf("dentist"), r.occ("piano").overlappingEventIds)
        assertTrue(r.occ("piano").cancellingEventIds.isEmpty())
        val e = r.events.single()
        assertEquals(UnderState.KEEP, e.underState)
        assertEquals(listOf("piano"), e.overlappedBlockIds) // 16:15 < 16:30이라 수학과는 안 겹침
    }

    @Test
    fun specialEventCancelCancelsOnlyOverlappingBlocks() {
        val ev = event("dentist", day(MON), "15:30", "16:45", listOf("harin"), mapOf("harin" to OverlapAction.CANCEL))
        val r = resolve(data.copy(specialEvents = listOf(ev)))
        assertEquals(setOf(CancelReason.EVENT), r.occ("piano").reasons)
        assertEquals(setOf(CancelReason.EVENT), r.occ("math").reasons)
        assertEquals(listOf("dentist"), r.occ("math").cancellingEventIds)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("tkd").status)
        assertEquals(UnderState.CANCEL, r.events.single().underState)
    }

    @Test
    fun touchingEdgesDoNotOverlap() {
        // 16:00~16:30 은 피아노(~16:00)와 수학(16:30~) 사이 — 겹치지 않음
        val ev = event("call", day(MON), "16:00", "16:30", listOf("harin"), mapOf("harin" to OverlapAction.CANCEL))
        val r = resolve(data.copy(specialEvents = listOf(ev)))
        assertTrue(r.occurrences.all { it.status == OccurrenceStatus.ACTIVE })
        assertEquals(UnderState.NONE, r.events.single().underState)
    }

    @Test
    fun oneEventOnSeveralSchedulesWithDifferentActions() {
        // 15:00~16:00 가족 행사: 하린은 피아노 취소, 도윤은 수영 유지
        val ev = event(
            "family", day(MON), "15:00", "16:00", listOf("harin", "doyun"),
            mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP),
        )
        val d = data.copy(specialEvents = listOf(ev))
        assertEquals(OccurrenceStatus.CANCELLED, resolve(d, "harin").occ("piano").status)
        val doyun = resolve(d, "doyun")
        assertEquals(OccurrenceStatus.ACTIVE, doyun.occ("swim").status)
        assertEquals(UnderState.KEEP, doyun.events.single().underState)
    }

    @Test
    fun missingOverlapActionMeansKeep() {
        val ev = event("x", day(MON), "15:00", "16:00", listOf("harin"))
        assertEquals(OccurrenceStatus.ACTIVE, resolve(data.copy(specialEvents = listOf(ev))).occ("piano").status)
    }

    @Test
    fun eventNotTargetingScheduleIsIgnored() {
        val ev = event("x", day(MON), "15:00", "16:00", listOf("doyun"), mapOf("doyun" to OverlapAction.CANCEL))
        val r = resolve(data.copy(specialEvents = listOf(ev)))
        assertTrue(r.events.isEmpty())
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("piano").status)
    }

    @Test
    fun allDayEventOverlapsEveryBlockThatDay() {
        val ev = event("trip", day(MON), "0:00", "0:00", listOf("harin"), mapOf("harin" to OverlapAction.CANCEL), allDay = true)
        val r = resolve(data.copy(specialEvents = listOf(ev)))
        assertEquals(OccurrenceStatus.CANCELLED, r.occ("piano").status)
        assertEquals(OccurrenceStatus.CANCELLED, r.occ("math").status)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("tkd").status)
    }

    @Test
    fun removingEventRestoresBlock() {
        val ev = event("x", day(MON), "15:00", "16:00", listOf("harin"), mapOf("harin" to OverlapAction.CANCEL))
        val withEvent = data.copy(specialEvents = listOf(ev))
        assertEquals(OccurrenceStatus.CANCELLED, resolve(withEvent).occ("piano").status)
        val removed = withEvent.copy(specialEvents = emptyList())
        assertEquals(OccurrenceStatus.ACTIVE, resolve(removed).occ("piano").status)
    }

    @Test
    fun eventsOutsideWeekOrShownDaysAreExcluded() {
        val nextWeek = event("a", day(MON).plusWeeks(1), "15:00", "16:00", listOf("harin"))
        val saturday = event("b", day(SAT), "10:00", "11:00", listOf("harin")) // 하린은 월~금
        val r = resolve(data.copy(specialEvents = listOf(nextWeek, saturday)))
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun overlappingBlocksForSaveDialog() {
        val ev = event("x", day(MON), "15:45", "16:45", listOf("harin", "doyun"))
        val map = OccurrenceResolver.overlappingBlocks(data, ev)
        assertEquals(listOf("piano", "math"), map.getValue("harin").map { it.id })
        assertEquals(listOf("swim"), map.getValue("doyun").map { it.id })

        val noOverlap = event("y", day(WED), "9:00", "10:00", listOf("harin"))
        assertTrue(OccurrenceResolver.overlappingBlocks(data, noOverlap).isEmpty())
    }

    // ── 개별 취소 / 우선순위 ──

    @Test
    fun manualCancellationOnlyThatDate() {
        val d = data.mapSchedule("harin") {
            it.copy(cancellations = listOf(ManualCancellation("piano", day(MON))))
        }
        assertEquals(setOf(CancelReason.MANUAL), resolve(d).occ("piano").reasons)
        // 다음 주 월요일은 그대로
        assertEquals(OccurrenceStatus.ACTIVE, resolve(d, week = WEEK.shift(1)).occ("piano").status)
    }

    @Test
    fun cancelReasonPriority() {
        val holidays = HolidayCalendar(listOf(Holiday(day(MON), "테스트 공휴일")))
        val vacation = Vacation("v", "방학", day(MON), day(MON), listOf("harin"))
        val ev = event("e", day(MON), "15:00", "16:00", listOf("harin"), mapOf("harin" to OverlapAction.CANCEL))
        var d = data.mapSchedule("harin") { it.copy(holidayPolicy = HolidayPolicy.CANCEL_BLOCKS) }
            .copy(vacations = listOf(vacation))

        // 공휴일 + 휴가 → 휴가
        var occ = resolve(d, holidays = holidays).occ("piano")
        assertEquals(setOf(CancelReason.VACATION, CancelReason.HOLIDAY), occ.reasons)
        assertEquals(CancelReason.VACATION, occ.displayReason)

        // + 특이 일정 → 특이 일정
        d = d.copy(specialEvents = listOf(ev))
        occ = resolve(d, holidays = holidays).occ("piano")
        assertEquals(CancelReason.EVENT, occ.displayReason)

        // + 개별 취소 → 개별 취소
        d = d.mapSchedule("harin") { it.copy(cancellations = listOf(ManualCancellation("piano", day(MON)))) }
        occ = resolve(d, holidays = holidays).occ("piano")
        assertEquals(4, occ.reasons.size)
        assertEquals(CancelReason.MANUAL, occ.displayReason)
    }

    // ── 공휴일 ──

    @Test
    fun holidayShowOnlyKeepsBlocksActive() {
        val holidays = HolidayCalendar(listOf(Holiday(day(MON), "테스트 공휴일")))
        val r = resolve(holidays = holidays)
        assertEquals("테스트 공휴일", r.days.first().holiday?.name)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("piano").status)
    }

    @Test
    fun holidayCancelPolicyCancelsBlocksThatDay() {
        val holidays = HolidayCalendar(listOf(Holiday(day(MON), "테스트 공휴일")))
        val d = data.mapSchedule("harin") { it.copy(holidayPolicy = HolidayPolicy.CANCEL_BLOCKS) }
        val r = resolve(d, holidays = holidays)
        assertEquals(setOf(CancelReason.HOLIDAY), r.occ("piano").reasons)
        assertEquals(OccurrenceStatus.ACTIVE, r.occ("tkd").status)
    }

    @Test
    fun customHolidaysAddAndHide() {
        val cal = HolidayCalendar(
            builtIn = listOf(Holiday(day(MON), "기본 공휴일")),
            custom = listOf(
                CustomHoliday(day(MON), hidden = true),
                CustomHoliday(day(WED), "재량휴업일"),
            ),
        )
        assertNull(cal.holidayOn(day(MON)))
        assertEquals("재량휴업일", cal.holidayOn(day(WED))?.name)
    }

    // ── 오늘 / 지난 일정 ──

    @Test
    fun todayAndPast() {
        val now = day(MON).atTime(16, 0)
        val r = resolve(now = now)
        assertTrue(r.days.first().isToday)
        assertFalse(r.days[1].isToday)
        assertTrue(r.occ("piano").isPast)   // 16:00에 끝남
        assertFalse(r.occ("math").isPast)
        assertFalse(r.occ("tkd").isPast)
    }

    // ── 표시 시간 범위 ──

    @Test
    fun autoRangeRoundsToHours() {
        val d = data.mapSchedule("harin") {
            it.copy(blocks = it.blocks + TestData.block("early", WED, "8:15", "8:40"))
        }
        val r = resolve(d)
        assertEquals(min("8:00"), r.displayStartMin)
        assertEquals(min("18:00"), r.displayEndMin)
    }

    @Test
    fun autoRangeIncludesTimedEventsAndCancelledBlocks() {
        val ev = event("late", day(WED), "19:10", "19:40", listOf("harin"))
        val d = data.mapSchedule("harin") { it.copy(cancellations = listOf(ManualCancellation("piano", day(MON)))) }
            .copy(specialEvents = listOf(ev))
        val r = resolve(d)
        assertEquals(min("15:00"), r.displayStartMin) // 취소된 피아노도 포함
        assertEquals(min("20:00"), r.displayEndMin)
    }

    @Test
    fun autoRangeDefaultWhenEmpty() {
        val r = resolve(data.mapSchedule("harin") { it.copy(blocks = emptyList()) })
        assertEquals(9 * 60, r.displayStartMin)
        assertEquals(18 * 60, r.displayEndMin)
    }

    @Test
    fun fixedRangeIsUsedAsIs() {
        val d = data.mapSchedule("harin") {
            it.copy(timeRange = TimeRange(TimeRangeMode.FIXED, startMin = 7 * 60, endMin = 22 * 60))
        }
        val r = resolve(d)
        assertEquals(7 * 60, r.displayStartMin)
        assertEquals(22 * 60, r.displayEndMin)
    }
}

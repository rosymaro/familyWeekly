package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.data.HolidayRepository
import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarOpsTest {

    private val mon = day(DayOfWeek.MONDAY)

    private fun ev(
        schedules: List<String> = listOf("harin", "doyun"),
        start: Int = 15 * 60 + 30,
        end: Int = 16 * 60,
        actions: Map<String, OverlapAction> = emptyMap(),
        allDay: Boolean = false,
        id: String = "e",
    ) = SpecialEvent(id, " 치과 ", mon, schedules, start, end, allDay, actions, memo = "  ")

    // ── 특이 일정 ──

    @Test
    fun mustChooseKeepOrCancelForEachOverlappingSchedule() {
        // 월 15:30~16:00 → 하린 피아노(15~16), 도윤 수영(15:30~16:30) 둘 다 겹침
        val e = ev()
        assertEquals(listOf("harin", "doyun"), EventOps.missingChoices(data, e))
        assertNotNull(EventOps.canSave(data, e))

        val half = ev(actions = mapOf("harin" to OverlapAction.CANCEL))
        assertEquals(listOf("doyun"), EventOps.missingChoices(data, half))

        val both = ev(actions = mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP))
        assertNull(EventOps.canSave(data, both))
    }

    @Test
    fun noChoiceNeededWithoutOverlap() {
        val e = ev(start = 9 * 60, end = 10 * 60)
        assertTrue(EventOps.missingChoices(data, e).isEmpty())
        assertNull(EventOps.canSave(data, e))
    }

    @Test
    fun allDayNeedsChoiceForEveryScheduleWithBlocksThatDay() {
        val e = ev(allDay = true)
        assertEquals(listOf("harin", "doyun"), EventOps.missingChoices(data, e))
        val sat = ev(allDay = true).copy(date = day(DayOfWeek.SATURDAY))
        assertEquals(listOf("doyun"), EventOps.missingChoices(data, sat)) // 토요일엔 도윤 축구만
    }

    @Test
    fun saveNormalizesAndCancellationShowsInWeek() {
        val e = ev(
            actions = mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP, "ghost" to OverlapAction.CANCEL),
        )
        val d = EventOps.save(data, e)
        val saved = d.specialEvents.single()
        assertEquals("치과", saved.title)
        assertNull(saved.memo)
        assertEquals(setOf("harin", "doyun"), saved.overlapActions.keys) // 없는 스케줄 선택은 지움

        val harin = OccurrenceResolver.resolve(d, "harin", TestData.WEEK)!!
        assertEquals(OccurrenceStatus.CANCELLED, harin.occurrences.single { it.block.id == "piano" }.status)
        val doyun = OccurrenceResolver.resolve(d, "doyun", TestData.WEEK)!!
        assertEquals(OccurrenceStatus.ACTIVE, doyun.occurrences.single { it.block.id == "swim" }.status)
    }

    @Test
    fun movingEventDropsStaleChoices() {
        val saved = EventOps.save(data, ev(actions = mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP)))
        // 같은 일정을 오전으로 옮기면 겹침이 없어져 선택이 정리됨
        val moved = saved.specialEvents.single().copy(startMin = 9 * 60, endMin = 10 * 60)
        val d = EventOps.save(saved, moved)
        assertEquals(1, d.specialEvents.size)
        assertTrue(d.specialEvents.single().overlapActions.isEmpty())
        val harin = OccurrenceResolver.resolve(d, "harin", TestData.WEEK)!!
        assertEquals(OccurrenceStatus.ACTIVE, harin.occurrences.single { it.block.id == "piano" }.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRefusesMissingChoice() {
        EventOps.save(data, ev())
    }

    @Test
    fun validation() {
        assertNotNull(EventOps.validate(ev().copy(title = "")))
        assertNotNull(EventOps.validate(ev(schedules = emptyList())))
        assertNotNull(EventOps.validate(ev(start = 600, end = 600)))
        assertNotNull(EventOps.validate(ev(start = 601, end = 660)))
        assertNull(EventOps.validate(ev(start = 0, end = 0, allDay = true)))
    }

    @Test
    fun deleteAndListByDate() {
        val a = ev(id = "a", start = 9 * 60, end = 10 * 60)
        val b = ev(id = "b", start = 8 * 60, end = 9 * 60)
        val c = ev(id = "c", allDay = true, actions = mapOf("harin" to OverlapAction.KEEP, "doyun" to OverlapAction.KEEP))
        var d = EventOps.save(EventOps.save(EventOps.save(data, a), b), c)
        assertEquals(listOf("c", "b", "a"), EventOps.on(d, mon).map { it.id }) // 하루 종일 먼저, 그다음 시각순
        d = EventOps.delete(d, "b")
        assertEquals(listOf("c", "a"), EventOps.on(d, mon).map { it.id })
    }

    // ── 휴가 ──

    @Test
    fun vacationValidationAndNormalize() {
        val v = Vacation("v", " 여름방학 ", mon, mon.plusDays(10), listOf("harin", "ghost"), blockIds = listOf("piano", "swim"))
        assertNull(VacationOps.validate(v))
        assertNotNull(VacationOps.validate(v.copy(to = mon.minusDays(1))))
        assertNotNull(VacationOps.validate(v.copy(scheduleIds = emptyList())))
        assertNotNull(VacationOps.validate(v.copy(blockIds = emptyList())))

        val d = VacationOps.save(data, v)
        val saved = d.vacations.single()
        assertEquals("여름방학", saved.title)
        assertEquals(listOf("harin"), saved.scheduleIds)
        assertEquals(listOf("piano"), saved.blockIds) // 도윤 블록(swim)은 고른 스케줄이 아니라 빠짐
        assertEquals(listOf(saved), VacationOps.covering(d, mon.plusDays(10)))
        assertTrue(VacationOps.covering(d, mon.plusDays(11)).isEmpty())
        assertTrue(VacationOps.delete(d, "v").vacations.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun vacationWithOnlyForeignBlocksIsRejected() {
        VacationOps.save(data, Vacation("v", "방학", mon, mon, listOf("harin"), blockIds = listOf("swim")))
    }

    // ── 공휴일 ──

    @Test
    fun customHolidays() {
        val repo = HolidayRepository { File("src/main/assets/data/holidays_kr.json").readText() }
        val chuseok = LocalDate.of(2026, 9, 25)
        var d = HolidayOps.hideBuiltIn(data, chuseok)
        assertTrue(HolidayOps.isHidden(d, chuseok))
        assertNull(repo.calendar(d.customHolidays).holidayOn(chuseok))
        d = HolidayOps.removeCustom(d, chuseok)
        assertEquals("추석", repo.calendar(d.customHolidays).holidayOn(chuseok)?.name)

        val schoolDay = LocalDate.of(2026, 11, 20)
        d = HolidayOps.add(d, schoolDay, " 재량휴업일 ")
        assertEquals("재량휴업일", repo.calendar(d.customHolidays).holidayOn(schoolDay)?.name)
        assertEquals(1, d.customHolidays.size)
    }

    // ── 달력 ──

    @Test
    fun monthGridStartsMondayAndHasSixWeeks() {
        val e = EventOps.save(data, ev(start = 9 * 60, end = 10 * 60))
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 11), e, HolidayCalendar.EMPTY, LocalDate.of(2026, 11, 19))
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 && it.first().date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first().date) // 11/1은 일요일
        assertFalse(weeks.first().first().inMonth)
        val cells = weeks.flatten()
        assertTrue(cells.single { it.date == LocalDate.of(2026, 11, 19) }.isToday)
        assertEquals(1, cells.single { it.date == mon }.events.size)
    }

    // ── 공휴일 파일 ──

    @Test
    fun holidayFileCoversFiveYearsWithKnownDates() {
        val repo = HolidayRepository { File("src/main/assets/data/holidays_kr.json").readText() }
        val all = repo.builtIn
        assertEquals((2026..2030).toList(), all.map { it.date.year }.distinct())
        assertEquals(all.size, all.map { it.date }.distinct().size) // 날짜 중복 없음
        val cal = repo.calendar(emptyList())
        fun name(y: Int, m: Int, d: Int) = cal.holidayOn(LocalDate.of(y, m, d))?.name
        // 양력 고정 공휴일
        for (y in 2026..2030) {
            assertEquals("신정", name(y, 1, 1))
            assertEquals("삼일절", name(y, 3, 1))
            assertEquals("어린이날", name(y, 5, 5))
            assertEquals("현충일", name(y, 6, 6))
            assertEquals("광복절", name(y, 8, 15))
            assertNotNull(name(y, 10, 3))
            assertEquals("한글날", name(y, 10, 9))
            assertEquals("기독탄신일", name(y, 12, 25))
        }
        // 음력 (한국천문연구원 음양력 기준)
        assertEquals("설날", name(2026, 2, 17))
        assertEquals("설날", name(2027, 2, 7))
        assertEquals("설날", name(2028, 1, 27))
        assertEquals("추석", name(2026, 9, 25))
        assertEquals("추석 · 개천절", name(2028, 10, 3))
        assertEquals("부처님오신날", name(2026, 5, 24))
        // 대체공휴일 예
        assertEquals("설날 대체공휴일", name(2027, 2, 9))
        assertEquals("추석 대체공휴일", name(2028, 10, 5))
        assertEquals("어린이날 대체공휴일", name(2029, 5, 7))
    }
}

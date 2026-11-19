package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import io.github.rosymaro.familyweekly.widget.WidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY

class WeekDescriptionTest {

    private val mon = day(MONDAY)

    @Test
    fun describesCancelReasonAndPostItEffectInWords() {
        var d = EventOps.save(data, SpecialEvent("e", "치과", mon, listOf("harin"), 15 * 60 + 30, 16 * 60, overlapActions = mapOf("harin" to OverlapAction.CANCEL)))
        d = d.mapSchedule("harin") { s -> s.copy(blocks = s.blocks.map { if (it.id == "math") it.copy(place = "2층") else it }) }
        val week = OccurrenceResolver.resolve(d, "harin", TestData.WEEK, HolidayCalendar(listOf(Holiday(day(TUESDAY), "테스트 공휴일"))), mon.atTime(9, 0))!!
        assertEquals(
            "월요일 (11월 16일), 오늘. 특이 일정 치과 15:30부터 16:00까지, 기존 일정 취소. " +
                "피아노 15:00부터 16:00까지, 특이 일정으로 취소. 수학 16:30부터 17:30까지, 2층.",
            WeekDescription.day(week, mon),
        )
        assertEquals("화요일 (11월 17일), 공휴일 테스트 공휴일. 일정 없음.", WeekDescription.day(week, day(TUESDAY)))
        assertTrue(WeekDescription.week(week).startsWith("하린 주간 시간표. 월요일"))
    }

    @Test
    fun widgetDescriptionFocusesOnToday() {
        val week = OccurrenceResolver.resolve(data, "harin", TestData.WEEK)!!
        assertTrue(WeekDescription.widget(week, mon).startsWith("하린 이번 주 시간표. 월요일"))
        assertTrue(WeekDescription.widget(week, day(SATURDAY)).contains("오늘은 시간표가 없는 날"))
    }

    @Test
    fun allReasonsHaveWords() {
        CancelReason.entries.forEach { assertTrue(WeekDescription.reasonLabel(it).isNotBlank()) }
    }

    // ── 위젯 다시 그리기 판단 ──

    @Test
    fun nowKeyOnlyChangesWhileNowLineIsVisible() {
        val week = OccurrenceResolver.resolve(data, "harin", TestData.WEEK)!! // 15:00~18:00
        assertEquals(15 * 60 + 30, WidgetLogic.nowKey(week, mon.atTime(15, 30)))
        // 밤에는 같은 키 → 5분 틱마다 다시 그리지 않음
        assertEquals(-1, WidgetLogic.nowKey(week, mon.atTime(21, 0)))
        assertEquals(WidgetLogic.nowKey(week, mon.atTime(21, 0)), WidgetLogic.nowKey(week, mon.atTime(21, 5)))
        // 표시 요일이 아닌 토요일
        assertEquals(-1, WidgetLogic.nowKey(week, day(SATURDAY).atTime(16, 0)))
        assertEquals(WidgetLogic.renderKey(1, "a", null), WidgetLogic.renderKey(1, "a", null))
        assertNotEquals(WidgetLogic.renderKey(1, "a"), WidgetLogic.renderKey(1, "b"))
    }
}

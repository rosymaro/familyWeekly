package io.github.rosymaro.familyweekly.layout

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.Block
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.OccurrenceStatus
import io.github.rosymaro.familyweekly.domain.ResolvedWeek
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TimeRange
import io.github.rosymaro.familyweekly.domain.TimeRangeMode
import io.github.rosymaro.familyweekly.domain.UnderState
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.render.SampleData
import io.github.rosymaro.familyweekly.theme.FloatRange
import io.github.rosymaro.familyweekly.theme.Insets
import io.github.rosymaro.familyweekly.theme.NowSpan
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.Visibility
import io.github.rosymaro.familyweekly.theme.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class LayoutTest {

    private val week = WeekRange.of(LocalDate.of(2026, 11, 16))

    /** 계산하기 쉬운 테마: 여백 0, 머리말 없음, 요일 머리말 20dp, 시간 축 20dp, 열 간격 0, 블록 inset 0 */
    private val plain = WidgetTheme().let {
        it.copy(
            canvas = it.canvas.copy(padding = Insets()),
            header = it.header.copy(visible = false),
            dayHeader = it.dayHeader.copy(height = 20f),
            timeAxis = it.timeAxis.copy(width = 20f),
            grid = it.grid.copy(columnGap = 0f),
            block = it.block.copy(inset = 0f),
            vacation = it.vacation.copy(headerBand = it.vacation.headerBand.copy(visible = false)),
        )
    }

    private fun schedule(blocks: List<Block>, range: TimeRange = TimeRange(TimeRangeMode.FIXED, 9 * 60, 19 * 60)) =
        Schedule("s", "테스트", days = DaysMode.MON_FRI, timeRange = range, blocks = blocks)

    private fun resolve(s: Schedule, events: List<SpecialEvent> = emptyList(), now: java.time.LocalDateTime? = null): ResolvedWeek =
        OccurrenceResolver.resolve(AppData(schedules = listOf(s), specialEvents = events), "s", week, now = now)!!

    // 1dp = 1px, 가로 220px (시간 축 20 + 열 5개 × 40), 세로 20 + 600 → 10시간 × 60px = 1분 1px
    private val spec = RenderSpec(widthPx = 220, heightPx = 620, density = 1f)

    private fun block(id: String, day: DayOfWeek, start: Int, end: Int) = Block(id, id, day, start, end)

    // ── 좌표 ──

    @Test
    fun fiveMinutesIsExactPixels() {
        val l = WeekLayout.compute(resolve(schedule(emptyList())), plain, spec, sizeClass = SizeClass.MEDIUM)
        assertEquals(20f, l.grid.top)
        assertEquals(620f, l.grid.bottom)
        assertEquals(1f, l.pxPerMinute)
        assertEquals(20f, l.yOf(9 * 60))
        assertEquals(25f, l.yOf(9 * 60 + 5))
        assertEquals(20f + 65f, l.yOf(10 * 60 + 5))
        // 2배 밀도에서도 5분 = 정확히 10px
        val l2 = WeekLayout.compute(resolve(schedule(emptyList())), plain, RenderSpec(440, 1240, 2f), sizeClass = SizeClass.MEDIUM)
        assertEquals(10f, l2.yOf(9 * 60 + 5) - l2.yOf(9 * 60))
    }

    @Test
    fun blockBoxMatchesTimesAndColumn() {
        val s = schedule(listOf(block("a", DayOfWeek.WEDNESDAY, 10 * 60 + 15, 11 * 60 + 40)))
        val l = WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM)
        val b = l.blocks.single().box
        assertEquals(20f + 75f, b.top)
        assertEquals(20f + 160f, b.bottom)
        assertEquals(20f + 2 * 40f, b.left) // 수요일 = 세 번째 열
        assertEquals(20f + 3 * 40f, b.right)
        assertEquals(5, l.columns.size)
        assertEquals(5, l.dayHeaders.size)
    }

    @Test
    fun hourLinesAndLabelsEveryHour() {
        val l = WeekLayout.compute(resolve(schedule(emptyList())), plain, spec, sizeClass = SizeClass.MEDIUM)
        assertEquals(11, l.hourLines.size) // 9..19
        assertEquals(20f, l.hourLines.first())
        assertEquals(620f, l.hourLines.last())
        assertEquals((9..18).map { "$it" }, l.hourLabels.map { it.text }) // 위쪽 정렬이면 끝 시각 라벨 없음
    }

    @Test
    fun labelsEveryTwoHoursInCompact() {
        val t = ThemeResolver.resolve(listOf(ThemeResolver.encode(plain)), SizeClass.COMPACT, false).theme
        val l = WeekLayout.compute(resolve(schedule(emptyList())), t, spec, sizeClass = SizeClass.COMPACT)
        assertEquals(listOf("9", "11", "13", "15", "17"), l.hourLabels.map { it.text })
        assertEquals(11, l.hourLines.size)
    }

    @Test
    fun fixedHourHeight() {
        val t = plain.copy(grid = plain.grid.copy(hourHeight = 30f))
        val l = WeekLayout.compute(resolve(schedule(emptyList())), t, spec, sizeClass = SizeClass.MEDIUM)
        assertEquals(0.5f, l.pxPerMinute)
        assertEquals(20f + 300f, l.grid.bottom)
    }

    @Test
    fun autoRangeUsesDomainRange() {
        val s = schedule(listOf(block("a", DayOfWeek.MONDAY, 15 * 60 + 10, 16 * 60 + 50)), TimeRange(TimeRangeMode.AUTO))
        val l = WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM)
        assertEquals(15 * 60, l.displayStartMin)
        assertEquals(17 * 60, l.displayEndMin)
        assertEquals(5f, l.pxPerMinute) // 600px / 120분
    }

    // ── 범위 밖 잘림 ──

    @Test
    fun blocksOutsideRangeAreClippedOrHidden() {
        val s = schedule(
            listOf(
                block("early", DayOfWeek.MONDAY, 8 * 60, 9 * 60 + 30),   // 위로 잘림
                block("late", DayOfWeek.MONDAY, 18 * 60 + 30, 20 * 60),  // 아래로 잘림
                block("gone", DayOfWeek.TUESDAY, 6 * 60, 8 * 60),        // 완전히 밖
            ),
        )
        val l = WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM)
        val early = l.blocks.single { it.occurrence.block.id == "early" }
        val late = l.blocks.single { it.occurrence.block.id == "late" }
        assertTrue(early.clippedTop); assertFalse(early.clippedBottom)
        assertEquals(l.grid.top, early.box.top)
        assertTrue(late.clippedBottom); assertFalse(late.clippedTop)
        assertEquals(l.grid.bottom, late.box.bottom)
        assertTrue(l.blocks.none { it.occurrence.block.id == "gone" })
    }

    // ── 겹침 lane ──

    @Test
    fun laneAssignment() {
        // a 10-12, b 11-13, c 12-14, d 15-16
        val lanes = LaneAssigner.assign(listOf(600 to 720, 660 to 780, 720 to 840, 900 to 960))
        assertEquals(Lane(0, 2), lanes[0])
        assertEquals(Lane(1, 2), lanes[1])
        assertEquals(Lane(0, 2), lanes[2]) // a가 끝난 lane 재사용
        assertEquals(Lane(0, 1), lanes[3]) // 겹치지 않으면 폭 전체
    }

    @Test
    fun laneThreeWay() {
        val lanes = LaneAssigner.assign(listOf(600 to 700, 610 to 650, 620 to 640))
        assertEquals(listOf(0, 1, 2), lanes.map { it.index })
        assertTrue(lanes.all { it.count == 3 })
    }

    @Test
    fun touchingBlocksDoNotShareLanes() {
        val lanes = LaneAssigner.assign(listOf(600 to 660, 660 to 720))
        assertEquals(listOf(Lane(0, 1), Lane(0, 1)), lanes)
    }

    @Test
    fun overlappingBlocksSideBySideInLayout() {
        val s = schedule(listOf(block("a", DayOfWeek.MONDAY, 600, 720), block("b", DayOfWeek.MONDAY, 660, 780)))
        val l = WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM)
        val a = l.blocks.single { it.occurrence.block.id == "a" }.box
        val b = l.blocks.single { it.occurrence.block.id == "b" }.box
        assertEquals(20f, a.width)
        assertEquals(20f, b.width)
        assertEquals(a.right, b.left)
    }

    // ── 글자 표시 ──

    @Test
    fun textVisibilityByHeight() {
        val s = schedule(
            listOf(
                block("tiny", DayOfWeek.MONDAY, 600, 605),    // 5px — 제목도 안 보임
                block("short", DayOfWeek.TUESDAY, 600, 620),  // 20px — 제목만
                block("tall", DayOfWeek.WEDNESDAY, 600, 690), // 90px — 시각까지
            ),
        )
        val l = WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM)
        fun b(id: String) = l.blocks.single { it.occurrence.block.id == id }
        assertFalse(b("tiny").showTitle)
        assertTrue(b("short").showTitle); assertFalse(b("short").showTime)
        assertTrue(b("tall").showTitle); assertTrue(b("tall").showTime)
        assertEquals(1, b("short").titleMaxLines)
        assertEquals(2, b("tall").titleMaxLines)
    }

    @Test
    fun neverShowTime() {
        val t = plain.copy(block = plain.block.copy(showTime = Visibility.NEVER))
        val s = schedule(listOf(block("tall", DayOfWeek.WEDNESDAY, 600, 690)))
        assertFalse(WeekLayout.compute(resolve(s), t, spec, sizeClass = SizeClass.MEDIUM).blocks.single().showTime)
    }

    // ── 포스트잇 ──

    @Test
    fun postItRotationIsSeededAndInRange() {
        val range = FloatRange(-3f, 3f)
        val a1 = PostItPlacer.rotationFor("dentist", range)
        val a2 = PostItPlacer.rotationFor("dentist", range)
        assertEquals(a1, a2)
        val angles = (1..50).map { PostItPlacer.rotationFor("event-$it", range) }
        assertTrue(angles.all { it in -3f..3f })
        assertTrue(angles.distinct().size > 10) // 일정마다 다름
        assertEquals(0f, PostItPlacer.rotationFor("x", FloatRange(0f, 0f)))
    }

    @Test
    fun postItStacking() {
        // 15:10-15:40, 15:20-16:00 겹침 → 0, 1 / 17:00 따로 → 0
        assertEquals(listOf(0, 1, 0), PostItPlacer.stackIndices(listOf(910 to 940, 920 to 960, 1020 to 1080)))
    }

    @Test
    fun postItSitsOnTopNarrowerThanColumn() {
        val s = schedule(listOf(block("a", DayOfWeek.MONDAY, 900, 960)))
        val ev = SpecialEvent("e", "치과", week.dateOf(DayOfWeek.MONDAY), listOf("s"), 900, 960)
        val t = plain.copy(postIt = plain.postIt.copy(offset = io.github.rosymaro.familyweekly.theme.Offset(0f, 0f), widthRatio = 0.8f))
        val l = WeekLayout.compute(resolve(s, listOf(ev)), t, spec, sizeClass = SizeClass.MEDIUM)
        val p = l.postIts.single()
        assertEquals(32f, p.box.width)               // 40 × 0.8
        assertEquals(20f + 4f, p.box.left)           // 가운데 정렬
        assertEquals(l.yOf(900), p.box.top)
        assertEquals(UnderState.KEEP, p.event.underState)
        // 블록은 그대로 있음 (대체가 아니라 덧붙임)
        assertEquals(1, l.blocks.size)
        assertEquals(OccurrenceStatus.ACTIVE, l.blocks.single().occurrence.status)
    }

    @Test
    fun shortPostItHasMinHeight() {
        val ev = SpecialEvent("e", "전화", week.dateOf(DayOfWeek.MONDAY), listOf("s"), 900, 905)
        val l = WeekLayout.compute(resolve(schedule(emptyList()), listOf(ev)), plain, spec, sizeClass = SizeClass.MEDIUM)
        assertEquals(plain.postIt.minHeight, l.postIts.single().box.height)
    }

    @Test
    fun allDayGoesToHeaderStrip() {
        val ev = SpecialEvent("e", "체험학습", week.dateOf(DayOfWeek.MONDAY), listOf("s"), allDay = true)
        val l = WeekLayout.compute(resolve(schedule(emptyList()), listOf(ev)), plain, spec, sizeClass = SizeClass.MEDIUM)
        val strip = assertNotNullAndGet(l.allDayStrip)
        assertTrue(l.grid.top > strip.bottom)
        assertTrue(l.postIts.single().box.top < l.grid.top)
    }

    // ── 오늘 / 현재 시간선 ──

    @Test
    fun nowLineOnTodayColumn() {
        val now = week.dateOf(DayOfWeek.TUESDAY).atTime(14, 35)
        val l = WeekLayout.compute(resolve(schedule(emptyList()), now = now), plain, spec, now, SizeClass.MEDIUM)
        val nl = assertNotNullAndGet(l.nowLine)
        assertEquals(l.yOf(14 * 60 + 35), nl.y)
        assertEquals(l.columns[1].left, nl.startX)
        assertEquals(l.columns[1].right, nl.endX)
        assertTrue(l.dayHeaders[1].isToday)
        assertTrue(l.columnTints.any { it.kind == TintKind.TODAY && it.box == l.columns[1] })

        val all = plain.copy(nowLine = plain.nowLine.copy(span = NowSpan.ALL_COLUMNS))
        val la = WeekLayout.compute(resolve(schedule(emptyList()), now = now), all, spec, now, SizeClass.MEDIUM)
        assertEquals(la.grid.left, la.nowLine!!.startX)
    }

    @Test
    fun noNowLineOutsideRangeOrWeek() {
        val early = week.dateOf(DayOfWeek.TUESDAY).atTime(7, 0)
        assertNull(WeekLayout.compute(resolve(schedule(emptyList()), now = early), plain, spec, early, SizeClass.MEDIUM).nowLine)
        val saturday = week.dateOf(DayOfWeek.SATURDAY).atTime(12, 0) // 월~금 스케줄
        assertNull(WeekLayout.compute(resolve(schedule(emptyList()), now = saturday), plain, spec, saturday, SizeClass.MEDIUM).nowLine)
    }

    @Test
    fun emptyState() {
        assertNotNull(WeekLayout.compute(resolve(schedule(emptyList())), plain, spec, sizeClass = SizeClass.MEDIUM).emptyState)
        val s = schedule(listOf(block("a", DayOfWeek.MONDAY, 600, 660)))
        assertNull(WeekLayout.compute(resolve(s), plain, spec, sizeClass = SizeClass.MEDIUM).emptyState)
    }

    // ── 글자 형식 ──

    @Test
    fun formats() {
        val days = resolve(schedule(emptyList())).days
        assertEquals("11월 16일 ~ 11월 20일", WeekLayout.subtitleText("M월 d일 ~ M월 d일", days))
        assertEquals("11/16 ~ 11/20", WeekLayout.subtitleText("M/d ~ M/d", days))
        assertEquals("월 16", WeekLayout.formatDate("E d", week.monday))
        assertEquals("16\n월", WeekLayout.formatDate("d\nE", week.monday))
        assertEquals("15", WeekLayout.formatHour("H", 900))
        assertEquals("오후 3시", WeekLayout.formatHour("a h시", 900))
        assertEquals("24", WeekLayout.formatHour("H", 1440))
        assertEquals("9:05", WeekLayout.formatTimeRange("H:mm", 545, 600))
        assertEquals("9:05–10:00", WeekLayout.formatTimeRange("H:mm–H:mm", 545, 600))
    }

    // ── 샘플 주 (미리보기) ──

    @Test
    fun sampleWeekHasEveryState() {
        val r = SampleData.resolved(DaysMode.MON_FRI, week)
        val reasons = r.occurrences.flatMap { it.reasons }.toSet()
        assertEquals(4, reasons.size) // 개별·특이·휴가·공휴일 모두
        assertTrue(r.events.any { it.underState == UnderState.KEEP })
        assertTrue(r.events.any { it.underState == UnderState.CANCEL })
        assertTrue(r.events.any { it.event.allDay })
        assertTrue(r.days.any { it.holiday != null })
        assertTrue(r.days.any { it.isToday })

        for (theme in listOf(WidgetTheme(), plain)) for ((w, h) in listOf(300 to 170, 320 to 320, 380 to 420)) {
            val sc = SizeClass.of(w.toFloat(), h.toFloat())
            val l = WeekLayout.compute(r, theme, RenderSpec(w * 2, h * 2, 2f), SampleData.now(week), sc)
            assertNotNull(l.nowLine)
            assertEquals(5, l.postIts.size)
            assertNotEquals(0, l.blocks.size)
            assertTrue(l.blocks.all { it.box.width > 0 && it.box.height > 0 })
        }
        assertEquals(7, SampleData.resolved(DaysMode.MON_SUN, week).days.size)
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}

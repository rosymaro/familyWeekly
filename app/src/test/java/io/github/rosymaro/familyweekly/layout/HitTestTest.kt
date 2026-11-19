package io.github.rosymaro.familyweekly.layout

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.Block
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TimeRange
import io.github.rosymaro.familyweekly.domain.TimeRangeMode
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.theme.Insets
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 1px = 1분, 시간 축 20px, 열 40px × 5, 그리드 위쪽 20px, 9:00~19:00 */
class HitTestTest {

    private val week = WeekRange.of(LocalDate.of(2026, 11, 16))
    private val theme = WidgetTheme().let {
        it.copy(
            canvas = it.canvas.copy(padding = Insets()),
            header = it.header.copy(visible = false),
            dayHeader = it.dayHeader.copy(height = 20f),
            timeAxis = it.timeAxis.copy(width = 20f),
            grid = it.grid.copy(columnGap = 0f),
            block = it.block.copy(inset = 0f),
        )
    }

    private fun layout(blocks: List<Block>, events: List<SpecialEvent> = emptyList()): WeekLayoutResult {
        val s = Schedule("s", "t", days = DaysMode.MON_FRI, timeRange = TimeRange(TimeRangeMode.FIXED, 540, 1140), blocks = blocks)
        val r = OccurrenceResolver.resolve(AppData(schedules = listOf(s), specialEvents = events), "s", week)!!
        return WeekLayout.compute(r, theme, RenderSpec(220, 620, 1f), sizeClass = SizeClass.MEDIUM)
    }

    private val piano = Block("piano", "피아노", DayOfWeek.MONDAY, 600, 660) // y 80..140, x 20..60

    @Test
    fun hitBlockAndPostIt() {
        val ev = SpecialEvent("e", "치과", week.dateOf(DayOfWeek.TUESDAY), listOf("s"), 600, 660)
        val l = layout(listOf(piano), listOf(ev))
        assertEquals("piano", HitTest.blockAt(l, 30f, 100f)?.occurrence?.block?.id)
        assertNull(HitTest.blockAt(l, 30f, 150f))
        assertNull(HitTest.blockAt(l, 70f, 100f))
        assertEquals("e", HitTest.postItAt(l, 80f, 100f)?.event?.event?.id)
    }

    @Test
    fun dayAndMinuteAt() {
        val l = layout(emptyList())
        assertNull(HitTest.dayIndexAt(l, 10f)) // 시간 축
        assertEquals(0, HitTest.dayIndexAt(l, 20f))
        assertEquals(2, HitTest.dayIndexAt(l, 100f))
        assertEquals(4, HitTest.dayIndexAt(l, 219f))
        assertEquals(540, HitTest.minuteAt(l, 20f))
        assertEquals(545, HitTest.minuteAt(l, 24f)) // 4분 → 5분 스냅
        assertEquals(540, HitTest.minuteAt(l, 22f))
        assertNull(HitTest.minuteAt(l, 5f)) // 요일 머리말
    }

    @Test
    fun newBlockPosition() {
        val l = layout(emptyList())
        assertEquals(1 to 600 + 15, HitTest.newBlockAt(l, 70f, 20f + 60f + 13f)) // 화요일 10:13 → 10:15
        assertEquals(0 to 1080, HitTest.newBlockAt(l, 30f, 615f)) // 끝 근처면 1시간이 들어가게 18:00
        assertNull(HitTest.newBlockAt(l, 10f, 100f))
    }

    @Test
    fun dragTargetSnapsToFiveMinutesAndColumns() {
        val l = layout(listOf(piano))
        val origin = l.blocks.single()
        // 아래로 33px(33분) → 35분 스냅, 오른쪽 85px → 수요일(2)
        val t = HitTest.dragTarget(l, origin, dxPx = 85f, dyPx = 33f)
        assertEquals(DragTarget(2, 635, 695), t)
        // 위로 많이 → 표시 범위 시작에서 멈춤
        assertEquals(540, HitTest.dragTarget(l, origin, 0f, -500f).startMin)
        // 아래로 많이 → 끝 - 길이
        assertEquals(1080, HitTest.dragTarget(l, origin, 0f, 5000f).startMin)
        // 왼쪽 밖 → 월요일, 오른쪽 밖 → 금요일
        assertEquals(0, HitTest.dragTarget(l, origin, -500f, 0f).dayIndex)
        assertEquals(4, HitTest.dragTarget(l, origin, 5000f, 0f).dayIndex)
        // 조금만 움직이면 그대로
        assertEquals(DragTarget(0, 600, 660), HitTest.dragTarget(l, origin, 10f, 2f))
    }

    @Test
    fun targetBox() {
        val l = layout(listOf(piano))
        val box = HitTest.targetBox(l, DragTarget(1, 600, 660))
        assertEquals(Box(60f, 80f, 100f, 140f), box)
    }
}

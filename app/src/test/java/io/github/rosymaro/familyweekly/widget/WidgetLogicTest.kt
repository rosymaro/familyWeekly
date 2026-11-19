package io.github.rosymaro.familyweekly.widget

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.ScheduleOps
import io.github.rosymaro.familyweekly.domain.TestData
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.domain.mapSchedule
import io.github.rosymaro.familyweekly.theme.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {

    @Test
    fun currentSizeByOrientation() {
        assertEquals(250f to 400f, WidgetLogic.currentSizeDp(minW = 250, minH = 180, maxW = 320, maxH = 400, landscape = false))
        assertEquals(320f to 180f, WidgetLogic.currentSizeDp(minW = 250, minH = 180, maxW = 320, maxH = 400, landscape = true))
        assertEquals(WidgetLogic.FALLBACK_WIDTH_DP to WidgetLogic.FALLBACK_HEIGHT_DP, WidgetLogic.currentSizeDp(0, 0, 0, 0, false))
    }

    @Test
    fun renderSpecKeepsDensityWhenItFits() {
        val max = WidgetLogic.maxBitmapBytes(1080, 2400) // 약 15.5MB
        val spec = WidgetLogic.renderSpec(320f, 320f, 2.75f, 1f, max)
        assertEquals(2.75f, spec.density)
        assertEquals(880, spec.widthPx)
        assertEquals(SizeClass.MEDIUM, spec.sizeClass) // dp 크기는 밀도와 상관없음
    }

    @Test
    fun renderSpecLowersDensityToFitMemoryLimit() {
        val max = 2_000_000L
        val spec = WidgetLogic.renderSpec(400f, 600f, 3.5f, 1.2f, max)
        assertTrue(WidgetLogic.bytes(spec) <= (max * 0.8).toLong())
        assertTrue(spec.density < 3.5f)
        // 보이는 크기(dp)는 그대로
        assertEquals(400f, spec.widthDp, 1f)
        assertEquals(600f, spec.heightDp, 1f)
        assertEquals(1.2f, spec.fontScale)
    }

    @Test
    fun themeChoice() {
        val data = TestData.data.mapSchedule("harin") { it.copy(themeId = "playful") }
        assertEquals("playful", WidgetLogic.themeIdFor(data, WidgetBinding(1, "harin")))
        assertEquals("clean-grid", WidgetLogic.themeIdFor(data, WidgetBinding(1, "harin", themeId = "clean-grid")))
        assertNull(WidgetLogic.themeIdFor(data, WidgetBinding(1, "doyun")))
        assertNull(WidgetLogic.themeIdFor(data, null))
    }

    @Test
    fun darkSelection() {
        assertTrue(WidgetLogic.isDark(DarkMode.SYSTEM, true))
        assertFalse(WidgetLogic.isDark(DarkMode.LIGHT, true))
        assertTrue(WidgetLogic.isDark(DarkMode.DARK, false))
    }

    @Test
    fun bindRebindRemoveAndPrune() {
        var d: AppData = TestData.data
        d = WidgetLogic.bind(d, WidgetBinding(7, "harin"))
        d = WidgetLogic.bind(d, WidgetBinding(8, "doyun", darkMode = DarkMode.DARK))
        d = WidgetLogic.bind(d, WidgetBinding(7, "doyun", themeId = "playful")) // 재구성
        assertEquals(2, d.widgets.size)
        assertEquals(WidgetBinding(7, "doyun", themeId = "playful"), WidgetLogic.binding(d, 7))
        assertEquals(2, WidgetLogic.countFor(d, "doyun"))
        assertEquals(1, WidgetLogic.remove(d, listOf(8)).widgets.size)
        assertEquals(listOf(8), WidgetLogic.keepOnly(d, setOf(8, 99)).widgets.map { it.appWidgetId })
    }

    @Test
    fun deletingScheduleLeavesUnboundWidget() {
        val d = WidgetLogic.bind(TestData.data, WidgetBinding(7, "harin"))
        val after = ScheduleOps.delete(d, "harin", removeOrphans = true)
        assertEquals(WidgetBinding(7, null), WidgetLogic.binding(after, 7))
        assertNull(WidgetLogic.themeIdFor(after, WidgetLogic.binding(after, 7)))
    }

    @Test
    fun tickAlignsToFiveMinutes() {
        val t = 1_700_000_123_456L
        val wait = WidgetLogic.millisToNextTick(t)
        assertTrue(wait in 1..300_000)
        assertEquals(0L, (t + wait) % 300_000)
        assertEquals(300_000L, WidgetLogic.millisToNextTick(1_700_000_100_000L - 1_700_000_100_000L % 300_000))
    }
}

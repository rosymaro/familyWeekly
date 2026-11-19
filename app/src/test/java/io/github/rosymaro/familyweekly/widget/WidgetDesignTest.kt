package io.github.rosymaro.familyweekly.widget

import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.TokenSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 위젯 설정: 디자인 꾸미기에서 바꾼 값은 "완료" 전까지 이 안에만 있고, 되돌리기는 처음 값으로. */
class WidgetDesignTest {

    private val saved = WidgetBinding(7, "harin", themeId = "playful", darkMode = DarkMode.DARK,
        themeOverride = ThemeResolver.parse("""{"block":{"cornerRadius":3}}"""))

    @Test
    fun startsFromSavedBindingOrDefaults() {
        assertEquals(WidgetDesign("playful", DarkMode.DARK, saved.themeOverride!!), WidgetDesign.of(saved))
        assertEquals(WidgetDesign(), WidgetDesign.of(null)) // 새 위젯
    }

    @Test
    fun editsThenRevertAndComplete() {
        val initial = WidgetDesign.of(saved)
        var d = initial.copy(themeId = null, darkMode = DarkMode.SYSTEM)
        d = d.copy(override = TokenSchema.set(d.override, listOf("postIt", "widthRatio"), JsonPrimitive(0.7)))
        assertNotEquals(initial, d)
        assertEquals(2, d.changedTokens)

        // 되돌리기
        assertEquals(initial, initial.copy())

        // 완료 → 저장할 위젯 설정
        val b = d.toBinding(7, "doyun")
        assertEquals("doyun", b.scheduleId)
        assertNull(b.themeId)
        assertEquals(DarkMode.SYSTEM, b.darkMode)
        assertEquals(JsonPrimitive(0.7), TokenSchema.get(b.themeOverride, listOf("postIt", "widthRatio")))
    }

    @Test
    fun emptyOverrideIsNotStored() {
        assertNull(WidgetDesign(override = JsonObject(emptyMap())).toBinding(1, "a").themeOverride)
    }

    @Test
    fun survivesRotation() {
        val d = WidgetDesign.of(saved)
        assertEquals(d, WidgetDesign.decode(d.encode()))
        assertEquals(WidgetDesign(), WidgetDesign.decode("깨진 값"))
    }

    @Test
    fun summary() {
        assertEquals("스케줄 디자인 그대로 (따뜻한 종이) · 다크 모드: 폰 설정 따라", WidgetDesign().summary("따뜻한 종이", usesScheduleTheme = true))
        assertEquals("아이 방 · 다크 모드: 다크 · 이 위젯만 바꾼 곳 1개", WidgetDesign.of(saved).summary("아이 방", usesScheduleTheme = false))
    }
}

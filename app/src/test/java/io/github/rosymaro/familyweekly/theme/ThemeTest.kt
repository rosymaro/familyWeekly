package io.github.rosymaro.familyweekly.theme

import io.github.rosymaro.familyweekly.domain.DarkMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ThemeTest {

    private fun obj(text: String) = ThemeResolver.parse(text)

    // ── 색 ──

    @Test
    fun parseColors() {
        assertEquals(0xFFFFAA00.toInt(), ColorMath.parse("#FFAA00"))
        assertEquals(0x80FFAA00.toInt(), ColorMath.parse("#80FFAA00"))
        assertEquals(0xFFFFAA00.toInt(), ColorMath.parse("#FA0"))
        assertEquals("#80FFAA00", ColorMath.toHex(0x80FFAA00.toInt()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun badColor() {
        ColorMath.parse("#12")
    }

    @Test
    fun themeColorDarkFallsBackToAutoDark() {
        val c = ThemeColor("#FFFBF3E6") // 밝은 아이보리
        val dark = c.resolve(isDark = true)
        assertTrue(ColorMath.luminance(dark) < 0.2) // 어두워짐
        assertEquals(ColorMath.parse("#FFFBF3E6"), c.resolve(isDark = false))
        assertEquals(ColorMath.parse("#FF111111"), ThemeColor("#FFFFFFFF", "#FF111111").resolve(true))
    }

    @Test
    fun readableTextColor() {
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.readableOn(ColorMath.parse("#FF222222")))
        assertTrue(ColorMath.readableOn(ColorMath.parse("#FFFFF5D0")) != 0xFFFFFFFF.toInt())
    }

    @Test
    fun hslRoundTrip() {
        for (hex in listOf("#FFF2A65A", "#FF6FC2A5", "#FF3E6FB0", "#FF808080")) {
            val c = ColorMath.parse(hex)
            val (h, s, l) = ColorMath.toHsl(c)
            val back = ColorMath.fromHsl(h, s, l)
            for (ch in listOf(ColorMath::red, ColorMath::green, ColorMath::blue)) {
                assertTrue(hex, kotlin.math.abs(ch(c) - ch(back)) <= 1)
            }
        }
    }

    @Test
    fun colorAcceptsStringOrObject() {
        val t = ThemeResolver.decode(ThemeResolver.merge(ThemeResolver.defaultsJson, obj(
            """{ "canvas": { "background": "#FF000000" }, "nowLine": { "color": { "light": "#FF111111", "dark": "#FF222222" } } }""",
        )))
        assertEquals(ThemeColor("#FF000000"), t.canvas.background)
        assertEquals(ThemeColor("#FF111111", "#FF222222"), t.nowLine.color)
        assertTrue(t.block.textColor.isAuto)
    }

    // ── 병합 ──

    @Test
    fun mergeIsTokenLevel() {
        val base = obj("""{ "block": { "cornerRadius": 6, "tintAlpha": 0.3, "title": { "size": 10, "weight": 600 } } }""")
        val over = obj("""{ "block": { "cornerRadius": 12, "title": { "size": 14 } } }""")
        val merged = ThemeResolver.merge(base, over)
        val t = ThemeResolver.decode(ThemeResolver.merge(ThemeResolver.defaultsJson, merged))
        assertEquals(12f, t.block.cornerRadius)
        assertEquals(0.3f, t.block.tintAlpha)       // 덮어쓰지 않은 토큰은 유지
        assertEquals(14f, t.block.title.size)
        assertEquals(600, t.block.title.weight)
    }

    @Test
    fun colorObjectIsReplacedWhole() {
        val base = obj("""{ "nowLine": { "color": { "light": "#FF111111", "dark": "#FF222222" } } }""")
        val over = obj("""{ "nowLine": { "color": { "light": "#FF333333" } } }""")
        val t = ThemeResolver.decode(ThemeResolver.merge(ThemeResolver.defaultsJson, ThemeResolver.merge(base, over)))
        assertEquals(ThemeColor("#FF333333", null), t.nowLine.color) // 예전 dark가 남지 않음 → 자동 생성
    }

    @Test
    fun nullTurnsTokenOff() {
        val t = ThemeResolver.resolve(listOf(obj("""{ "today": { "columnTint": null } }""")), SizeClass.MEDIUM, false).theme
        assertNull(t.today.columnTint)
    }

    @Test
    fun layersApplyInOrderPresetScheduleWidget() {
        val preset = obj("""{ "block": { "cornerRadius": 4, "tintAlpha": 0.2 } }""")
        val schedule = obj("""{ "block": { "cornerRadius": 8 } }""")
        val widget = obj("""{ "block": { "tintAlpha": 0.9 } }""")
        val t = ThemeResolver.resolve(listOf(preset, schedule, widget), SizeClass.MEDIUM, false).theme
        assertEquals(8f, t.block.cornerRadius)
        assertEquals(0.9f, t.block.tintAlpha)
    }

    @Test
    fun sizeClassDefaultsAndThemeOverrides() {
        val compact = ThemeResolver.resolve(emptyList(), SizeClass.COMPACT, false).theme
        assertEquals(Visibility.NEVER, compact.block.showTime)
        assertEquals(2, compact.timeAxis.showEveryNHours)
        assertFalse(compact.header.subtitle.visible)

        val medium = ThemeResolver.resolve(emptyList(), SizeClass.MEDIUM, false).theme
        assertEquals(Visibility.IF_FITS, medium.block.showTime)

        // 테마의 sizeClass 덮어쓰기가 크기 기본값보다 우선
        val custom = obj("""{ "sizeClass": { "compact": { "block": { "showTime": "ALWAYS" } } } }""")
        assertEquals(Visibility.ALWAYS, ThemeResolver.resolve(listOf(custom), SizeClass.COMPACT, false).theme.block.showTime)
        assertEquals(Visibility.IF_FITS, ThemeResolver.resolve(listOf(custom), SizeClass.MEDIUM, false).theme.block.showTime)
    }

    @Test
    fun unknownTokensAreIgnored() {
        val t = ThemeResolver.resolve(listOf(obj("""{ "futureToken": 1, "block": { "sparkle": true, "cornerRadius": 3 } }""")), SizeClass.MEDIUM, false)
        assertEquals(3f, t.theme.block.cornerRadius)
    }

    @Test
    fun darkModeSelection() {
        assertTrue(ThemeResolver.isDark(DarkMode.SYSTEM, systemDark = true))
        assertFalse(ThemeResolver.isDark(DarkMode.SYSTEM, systemDark = false))
        assertTrue(ThemeResolver.isDark(DarkMode.DARK, systemDark = false))
        assertFalse(ThemeResolver.isDark(DarkMode.LIGHT, systemDark = true))
    }

    @Test
    fun sizeClassFromDp() {
        assertEquals(SizeClass.COMPACT, SizeClass.of(300f, 170f))
        assertEquals(SizeClass.COMPACT, SizeClass.of(180f, 300f))
        assertEquals(SizeClass.MEDIUM, SizeClass.of(320f, 320f))
        assertEquals(SizeClass.LARGE, SizeClass.of(380f, 420f))
    }

    // ── 프리셋 파일 ──

    private val presets = ThemePresets { path -> File("src/main/assets/$path").takeIf { it.exists() }?.readText() }

    @Test
    fun allPresetsParseAndResolve() {
        assertEquals(ThemePresets.IDS.toSet(), presets.all.keys)
        for (id in ThemePresets.IDS) for (size in SizeClass.entries) for (dark in listOf(false, true)) {
            val rt = ThemeResolver.resolve(listOf(presets.get(id)), size, dark)
            assertEquals(id, rt.theme.id)
            // 모든 색이 실제로 풀리는지
            allColors(ThemeResolver.encode(rt.theme)).forEach { (path, c) ->
                if (!c.isAuto) try {
                    c.resolve(dark)
                } catch (e: Exception) {
                    fail("$id $path: ${e.message}")
                }
            }
        }
    }

    @Test
    fun presetsAndDefaultsSpecifyDarkColorsExplicitly() {
        // DESIGN.md 4절: 프리셋은 라이트/다크를 직접 지정 (자동 생성에 맡기지 않음)
        for (id in ThemePresets.IDS) {
            val t = ThemeResolver.resolve(listOf(presets.get(id)), SizeClass.MEDIUM, false).theme
            val missing = allColors(ThemeResolver.encode(t)).filter { (_, c) -> !c.isAuto && c.dark == null }.map { it.first }
            assertTrue("$id 다크 색이 없는 토큰: $missing", missing.isEmpty())
        }
    }

    @Test
    fun presetsDifferFromEachOther() {
        val names = ThemePresets.IDS.map { presets.name(it) }
        assertEquals(listOf("따뜻한 종이", "깔끔한 표", "아이 방"), names)
        val radii = ThemePresets.IDS.map { ThemeResolver.resolve(listOf(presets.get(it)), SizeClass.MEDIUM, false).theme.canvas.cornerRadius }
        assertEquals(3, radii.distinct().size)
    }

    @Test
    fun unknownPresetFallsBackToDefault() {
        assertEquals(presets.get(ThemePresets.DEFAULT_ID), presets.get("nope"))
    }

    /** JSON 안의 모든 {light, dark} 색과 경로. */
    private fun allColors(el: JsonElement, path: String = ""): List<Pair<String, ThemeColor>> = when (el) {
        is JsonObject ->
            if ("light" in el) listOf(path to ThemeResolver.json.decodeFromJsonElement(ThemeColor.serializer(), el))
            else el.flatMap { (k, v) -> if (k == "sizeClass") emptyList() else allColors(v, "$path.$k") }
        is JsonArray -> el.flatMapIndexed { i, v -> allColors(v, "$path[$i]") }
        else -> emptyList()
    }
}

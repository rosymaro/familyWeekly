package io.github.rosymaro.familyweekly.theme

import io.github.rosymaro.familyweekly.data.AppStore
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.TestData
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.domain.mapSchedule
import io.github.rosymaro.familyweekly.layout.RenderSpec
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.render.SampleData
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class DesignEditorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val presets = ThemePresets { path -> File("src/main/assets/$path").takeIf { it.exists() }?.readText() }
    private val lib = ThemeLibrary(presets)
    private fun o(text: String) = ThemeResolver.parse(text)
    private fun resolve(d: AppData, scheduleId: String? = "harin", binding: WidgetBinding? = null) =
        lib.resolve(d, scheduleId?.let { d.schedule(it) }, SizeClass.MEDIUM, false, binding).theme

    // ── 토큰 목록 (설명자에서 자동) ──

    @Test
    fun schemaCoversDesignTokens() {
        val keys = TokenSchema.all.associateBy { it.key }
        assertTrue(keys["canvas.background"]!!.kind is TokenKind.Color)
        assertTrue(keys["block.fillStyle"]!!.kind is TokenKind.Choice)
        assertEquals(listOf("SOLID", "TINT", "OUTLINE", "LEFT_BAR", "GRADIENT"), (keys["block.fillStyle"]!!.kind as TokenKind.Choice).options)
        assertTrue(keys["block.title.font"]!!.kind is TokenKind.Font)
        assertEquals(TokenKind.Number(100f, 900f, 100f, true), keys["block.title.weight"]!!.kind)
        assertTrue(keys["block.palette"]!!.kind == TokenKind.MapOf(TokenKind.Color))
        assertTrue(keys["postIt.papers"]!!.kind == TokenKind.MapOf(TokenKind.Color))
        assertTrue(keys["cancelled.reasonIcons"]!!.kind == TokenKind.MapOf(TokenKind.Text))
        assertTrue(keys["canvas.gradient.colors"]!!.kind is TokenKind.ColorList)
        assertTrue(keys["today.columnTint"]!!.nullable)
        assertTrue(keys["grid.hourHeight"]!!.nullable)
        assertEquals(TokenKind.Presence, keys["block.shadow"]!!.kind)
        assertTrue(keys["block.shadow.blur"]!!.kind is TokenKind.Number)
        assertTrue(keys["postIt.rotation.min"]!!.kind.let { it is TokenKind.Number && it.min == -45f })
        assertTrue(keys["canvas.opacity"]!!.kind.let { it is TokenKind.Number && it.max == 1f })
        assertTrue("id" !in keys && "name" !in keys && "sizeClass" !in keys)
        // 그룹이 모두 한국어 이름을 가짐
        assertTrue(TokenSchema.groups.all { it in TokenLabels.groups })
        assertEquals("제목 글자 · 크기", TokenLabels.token(keys["block.title.size"]!!))
    }

    @Test
    fun everyTokenHasKoreanLabels() {
        // 토큰을 추가하면 TokenLabels에도 이름을 넣어야 편집기에 한국어로 보인다
        assertEquals(emptySet<String>(), TokenLabels.missingWords())
        assertEquals(emptySet<String>(), TokenLabels.missingChoices())
    }

    @Test
    fun everyTokenPathExistsInDefaultThemeJsonOrIsNullable() {
        val json = ThemeResolver.defaultsJson
        val missing = TokenSchema.all
            .filter { !it.nullable && TokenSchema.presenceParent(it) == null && TokenSchema.get(json, it.path) == null }
            .map { it.key }
        assertEquals("block.shadow", TokenSchema.presenceParent(TokenSchema.all.single { it.key == "block.shadow.blur" })?.key)
        assertTrue("$missing", missing.isEmpty())
    }

    // ── 경로 편집 ──

    @Test
    fun setGetRemoveWithPruning() {
        var obj = JsonObject(emptyMap())
        obj = TokenSchema.set(obj, listOf("block", "title", "size"), JsonPrimitive(14))
        obj = TokenSchema.set(obj, listOf("block", "cornerRadius"), JsonPrimitive(9))
        assertEquals(JsonPrimitive(14), TokenSchema.get(obj, listOf("block", "title", "size")))
        assertTrue(TokenSchema.isOverridden(obj, listOf("block", "title")))
        assertEquals(2, TokenSchema.overriddenCount(obj, "block")) // title.size, cornerRadius
        obj = TokenSchema.remove(obj, listOf("block", "title", "size"))
        assertNull(TokenSchema.get(obj, listOf("block", "title"))) // 빈 묶음 정리
        obj = TokenSchema.remove(obj, listOf("block", "cornerRadius"))
        assertTrue(obj.isEmpty())
        // 없는 경로 지우기는 그대로
        assertEquals(obj, TokenSchema.remove(obj, listOf("x", "y")))
    }

    // ── 저장 대상 3가지 + 레이어 순서 ──

    @Test
    fun editPresetScheduleAndWidgetLayers() {
        var d = TestData.data.mapSchedule("harin") { it.copy(themeId = "clean-grid") }
        d = d.copy(widgets = listOf(WidgetBinding(7, "harin")))

        // 프리셋 자체 고치기
        d = ThemeOps.saveOverride(d, DesignTarget.Theme("clean-grid"), o("""{"block":{"cornerRadius":11}}"""), presets)
        assertEquals(11f, resolve(d).block.cornerRadius)
        assertTrue(lib.entries(d).single { it.id == "clean-grid" }.isEdited)
        // 이 스케줄만
        d = ThemeOps.saveOverride(d, DesignTarget.ScheduleOnly("harin"), o("""{"block":{"tintAlpha":0.5}}"""))
        assertEquals(0.5f, resolve(d).block.tintAlpha)
        assertEquals(11f, resolve(d).block.cornerRadius) // 아래 단계 유지
        // 이 위젯만
        d = ThemeOps.saveOverride(d, DesignTarget.WidgetOnly(7), o("""{"block":{"cornerRadius":2}}"""))
        val b = d.widgets.single()
        assertEquals(2f, resolve(d, binding = b).block.cornerRadius)
        assertEquals(0.5f, resolve(d, binding = b).block.tintAlpha)
        assertEquals(11f, resolve(d).block.cornerRadius) // 위젯 덮어쓰기는 앱 화면에 영향 없음

        // 편집기가 보는 "아래 레이어 + 지금 덮어쓰기"
        val (base, ov) = lib.baseAndOverride(d, DesignTarget.WidgetOnly(7))
        assertEquals(o("""{"block":{"cornerRadius":2}}"""), ov)
        assertEquals(0.5f, ThemeResolver.resolve(base, SizeClass.MEDIUM, false).theme.block.tintAlpha)

        // 비우면 기록이 사라짐
        d = ThemeOps.saveOverride(d, DesignTarget.WidgetOnly(7), JsonObject(emptyMap()))
        assertNull(d.widgets.single().themeOverride)
        d = ThemeOps.saveOverride(d, DesignTarget.Theme("clean-grid"), JsonObject(emptyMap()), presets)
        assertTrue(d.themes.isEmpty())
    }

    @Test
    fun otherScheduleUnaffectedByScheduleOverride() {
        val d = ThemeOps.saveOverride(TestData.data, DesignTarget.ScheduleOnly("harin"), o("""{"block":{"tintAlpha":0.9}}"""))
        assertEquals(0.9f, resolve(d, "harin").block.tintAlpha)
        assertEquals(WidgetTheme().block.tintAlpha, resolve(d, "doyun").block.tintAlpha)
    }

    @Test
    fun nullTurnsDecorationOffAndPresenceOn() {
        var d = ThemeOps.saveOverride(TestData.data, DesignTarget.ScheduleOnly("harin"), o("""{"today":{"columnTint":null},"block":{"shadow":{}}}"""))
        assertNull(resolve(d).today.columnTint)
        assertEquals(Shadow(), resolve(d).block.shadow)
        d = ThemeOps.saveOverride(d, DesignTarget.ScheduleOnly("harin"), TokenSchema.set(JsonObject(emptyMap()), listOf("postIt", "shadow"), JsonNull))
        assertNull(resolve(d).postIt.shadow)
    }

    // ── 복제·이름·삭제·처음 상태로 ──

    @Test
    fun duplicateRenameDeleteReset() {
        var d = ThemeOps.saveOverride(TestData.data, DesignTarget.Theme("playful"), o("""{"canvas":{"cornerRadius":5}}"""), presets)
        val (d2, copy) = ThemeOps.duplicate(d, "playful", " 우리 집 ", TestData.ids("t"))
        d = d2
        assertEquals("우리 집", copy.name)
        assertEquals("playful", copy.baseId)
        assertEquals(5f, lib.resolve(d, TestData.harin.copy(themeId = copy.id), SizeClass.MEDIUM, false).theme.canvas.cornerRadius)
        assertEquals(listOf("따뜻한 종이", "깔끔한 표", "아이 방", "우리 집"), lib.entries(d).map { it.name })

        d = ThemeOps.rename(d, copy.id, "우리 집 2")
        d = ThemeOps.rename(d, "soft-paper", "우리 종이", presets)
        assertEquals("우리 종이", lib.name(d, "soft-paper"))

        // 쓰는 곳이 있으면 개수, 지우면 기본 디자인으로
        d = d.mapSchedule("harin") { it.copy(themeId = copy.id) }.copy(widgets = listOf(WidgetBinding(1, "doyun", themeId = copy.id)))
        assertEquals(2, ThemeOps.usage(d, copy.id))
        d = ThemeOps.delete(d, copy.id)
        assertNull(d.schedule("harin")!!.themeId)
        assertNull(d.widgets.single().themeId)
        assertTrue(lib.entries(d).none { it.id == copy.id })

        // 프리셋은 지울 수 없고 처음 상태로
        d = ThemeOps.delete(d, "playful")
        assertFalse(lib.entries(d).single { it.id == "playful" }.isEdited)
        assertEquals(presets.name("playful"), lib.name(d, "playful"))
    }

    @Test
    fun unknownThemeIdFallsBackToDefault() {
        val d = TestData.data.mapSchedule("harin") { it.copy(themeId = "gone") }
        assertEquals("soft-paper", resolve(d).id)
    }

    @Test
    fun savedAndReloaded() {
        var d = ThemeOps.saveOverride(TestData.data, DesignTarget.Theme("soft-paper"), o("""{"postIt":{"rotation":{"min":-8,"max":8}}}"""), presets)
        d = ThemeOps.saveOverride(d, DesignTarget.ScheduleOnly("harin"), o("""{"nowLine":{"color":"#FF00FF00"}}"""))
        val store = AppStore(tmp.root)
        store.update { d }
        val back = AppStore(tmp.root).data.value
        assertEquals(d, back)
        assertEquals(8f, resolve(back).postIt.rotation.max)
        assertEquals(ThemeColor("#FF00FF00"), resolve(back).nowLine.color)
    }

    // ── 미리보기 누른 곳 → 그룹 ──

    @Test
    fun previewTapFindsGroup() {
        val week = WeekRange.of(LocalDate.of(2026, 11, 16))
        val r = SampleData.resolved(DaysMode.MON_FRI, week)
        val rt = ThemeResolver.resolve(emptyList(), SizeClass.MEDIUM, false)
        val l = WeekLayout.compute(r, rt.theme, RenderSpec(640, 640, 2f), SampleData.now(week), SizeClass.MEDIUM)
        val post = l.postIts.first { !it.event.event.allDay }
        assertEquals("postIt", DesignHitTest.groupAt(l, post.box.centerX, post.box.centerY))
        val active = l.blocks.first { it.occurrence.status.name == "ACTIVE" && l.postIts.none { p -> p.box.let { b -> it.box.centerX in b.left..b.right && it.box.centerY in b.top..b.bottom } } }
        assertEquals("block", DesignHitTest.groupAt(l, active.box.centerX, active.box.centerY))
        val today = l.dayHeaders.first { it.isToday }
        assertEquals("today", DesignHitTest.groupAt(l, today.box.centerX, today.box.centerY))
        val other = l.dayHeaders.first { !it.isToday }
        assertEquals("dayHeader", DesignHitTest.groupAt(l, other.box.centerX, other.box.centerY))
        assertEquals("header", DesignHitTest.groupAt(l, l.header!!.box.left + 2, l.header!!.box.centerY))
        assertEquals("timeAxis", DesignHitTest.groupAt(l, l.timeAxis.centerX, l.timeAxis.bottom - 2))
        assertEquals("nowLine", DesignHitTest.groupAt(l, l.nowLine!!.startX + 4, l.nowLine!!.y))
        assertEquals("canvas", DesignHitTest.groupAt(l, 1f, 639f))
    }
}

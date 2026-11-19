package io.github.rosymaro.familyweekly.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.theme.DesignTarget
import io.github.rosymaro.familyweekly.theme.ThemeOps
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.TokenSchema
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import io.github.rosymaro.familyweekly.ui.navigation.DesignEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 미리보기 위젯 크기 (dp). SizeClass.of 기준에 맞춘 대표 크기. */
enum class PreviewSize(val label: String, val widthDp: Int, val heightDp: Int) {
    COMPACT("작게 4×2", 300, 170),
    MEDIUM("보통 4×4", 320, 320),
    LARGE("크게 5×5", 380, 420),
}

/**
 * 디자인 편집기. 바꾼 토큰만 [draft](덮어쓰기 JSON)에 모으고, "저장"할 때 대상(디자인 / 이 아이만 / 이 위젯만)에 넣는다.
 * "되돌리기" = 직전 저장본으로.
 */
class DesignEditorViewModel(app: FamilyWeeklyApp, handle: SavedStateHandle) : StoreViewModel(app) {

    val library = app.themes
    val presets = app.presets
    val target: DesignTarget = handle.toRoute<DesignEditRoute>().let {
        when (it.kind) {
            DesignEditRoute.SCHEDULE -> DesignTarget.ScheduleOnly(it.id)
            DesignEditRoute.WIDGET -> DesignTarget.WidgetOnly(it.id.toInt())
            else -> DesignTarget.Theme(it.id)
        }
    }

    private var saved: JsonObject = library.baseAndOverride(data.value, target).second
    private val _draft = MutableStateFlow(saved)
    val draft: StateFlow<JsonObject> = _draft.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    /** 편집 중인 덮어쓰기 바로 밑 레이어 (지금 저장된 데이터 기준) */
    fun baseLayers(): List<JsonObject?> = library.baseAndOverride(data.value, target).first

    /** 지금 보이는 값 (기본값 + 아래 레이어 + 편집 중 덮어쓰기, 크기 단계 전) */
    fun effective(): JsonObject {
        val merged = (baseLayers() + _draft.value).fold(ThemeResolver.defaultsJson) { acc, l -> ThemeResolver.merge(acc, l) }
        return ThemeResolver.encode(ThemeResolver.decode(merged))
    }

    fun set(path: List<String>, value: JsonElement) {
        _draft.value = TokenSchema.set(_draft.value, path, value)
        _dirty.value = _draft.value != saved
    }

    /** 이 토큰만 "기본값으로" (덮어쓰기에서 빼기 → 아래 레이어 값) */
    fun reset(path: List<String>) {
        _draft.value = TokenSchema.remove(_draft.value, path)
        _dirty.value = _draft.value != saved
    }

    /** 그룹 전체 기본값으로 */
    fun resetGroup(group: String) {
        _draft.value = JsonObject(_draft.value - group)
        _dirty.value = _draft.value != saved
    }

    fun revertAll() {
        _draft.value = saved
        _dirty.value = false
    }

    fun save() {
        val d = _draft.value
        saved = d
        _dirty.value = false
        update { ThemeOps.saveOverride(it, target, d, presets) }
    }

    /** 화면 제목 아래 "어디에 저장되는지" */
    fun targetLabel(): String {
        val data = data.value
        return when (val t = target) {
            is DesignTarget.Theme -> "디자인 '${library.name(data, t.themeId)}' 자체 — 이 디자인을 쓰는 모든 아이·위젯에 적용"
            is DesignTarget.ScheduleOnly -> "${data.schedule(t.scheduleId)?.name ?: "?"} 시간표에만 (앱 화면·이 아이 위젯)"
            is DesignTarget.WidgetOnly -> {
                val b = data.widgets.firstOrNull { it.appWidgetId == t.appWidgetId }
                "이 위젯에만 (${b?.scheduleId?.let { data.schedule(it)?.name } ?: "연결 없음"})"
            }
        }
    }

    fun holidays() = app.holidays.calendar(data.value.customHolidays)

    /** 미리보기에 쓸 스케줄 (실제 데이터 보기) */
    fun previewScheduleId(): String? = when (val t = target) {
        is DesignTarget.ScheduleOnly -> t.scheduleId
        is DesignTarget.WidgetOnly -> data.value.widgets.firstOrNull { it.appWidgetId == t.appWidgetId }?.scheduleId
        is DesignTarget.Theme -> data.value.schedules.firstOrNull { it.themeId == t.themeId }?.id ?: data.value.schedules.firstOrNull()?.id
    }

    companion object {
        val Factory = appViewModelFactory { app, handle -> DesignEditorViewModel(app, handle) }
    }
}

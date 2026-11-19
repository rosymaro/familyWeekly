package io.github.rosymaro.familyweekly.theme

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.IdGenerator
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.UserTheme
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import kotlinx.serialization.json.JsonObject

/** 디자인 목록 한 줄. */
data class ThemeEntry(
    val id: String,
    val name: String,
    /** 앱에 들어 있는 프리셋인지 (지울 수 없음, "처음 상태로"만) */
    val isPreset: Boolean,
    /** 프리셋을 고친 적 있는지 */
    val isEdited: Boolean,
    val baseId: String,
)

/** 편집 대상: 디자인 자체 / 이 스케줄만 / 이 위젯만 (DESIGN.md 5절). */
sealed interface DesignTarget {
    data class Theme(val themeId: String) : DesignTarget
    data class ScheduleOnly(val scheduleId: String) : DesignTarget
    data class WidgetOnly(val appWidgetId: Int) : DesignTarget
}

/**
 * 프리셋(assets) + 사용자 디자인(AppData.themes) + 스케줄·위젯 덮어쓰기를 합쳐 최종 테마를 만든다.
 * 레이어 순서: 코드 기본값 ← 프리셋 ← 사용자 디자인 고친 값 ← 스케줄 덮어쓰기 ← 위젯 덮어쓰기 ← (화면 전용) ← 크기 단계.
 */
class ThemeLibrary(private val presets: ThemePresets) {

    fun entries(data: AppData): List<ThemeEntry> {
        val preset = ThemePresets.IDS.map { id ->
            val user = data.themes.firstOrNull { it.id == id }
            ThemeEntry(id, user?.name ?: presets.name(id), isPreset = true, isEdited = user != null && user.overrides.isNotEmpty(), baseId = id)
        }
        val custom = data.themes.filter { it.id !in ThemePresets.IDS }.map {
            ThemeEntry(it.id, it.name, isPreset = false, isEdited = true, baseId = it.baseId)
        }
        return preset + custom
    }

    fun name(data: AppData, themeId: String?): String {
        val id = themeId ?: ThemePresets.DEFAULT_ID
        return entries(data).firstOrNull { it.id == id }?.name ?: presets.name(ThemePresets.DEFAULT_ID)
    }

    /** 디자인 하나의 레이어 (없는 id면 기본 프리셋). */
    fun themeLayers(data: AppData, themeId: String?): List<JsonObject?> {
        val id = themeId ?: ThemePresets.DEFAULT_ID
        val user = data.themes.firstOrNull { it.id == id }
        return when {
            id in ThemePresets.IDS -> listOf(presets.get(id), user?.overrides)
            user != null -> listOf(presets.get(user.baseId), user.overrides)
            else -> listOf(presets.get(ThemePresets.DEFAULT_ID))
        }
    }

    /** 스케줄(+위젯)이 실제로 쓰는 레이어. */
    fun layersFor(data: AppData, schedule: Schedule?, binding: WidgetBinding? = null): List<JsonObject?> {
        val themeId = binding?.themeId ?: schedule?.themeId
        return themeLayers(data, themeId) + schedule?.themeOverride + binding?.themeOverride
    }

    fun resolve(
        data: AppData,
        schedule: Schedule?,
        sizeClass: SizeClass,
        dark: Boolean,
        binding: WidgetBinding? = null,
        vararg extra: JsonObject?,
    ): ResolvedTheme = ThemeResolver.resolve(layersFor(data, schedule, binding) + extra.toList(), sizeClass, dark)

    /** 편집 대상의 "아래 깔린" 레이어 (편집 중인 덮어쓰기 바로 밑까지)와 지금 저장된 덮어쓰기. */
    fun baseAndOverride(data: AppData, target: DesignTarget): Pair<List<JsonObject?>, JsonObject> = when (target) {
        is DesignTarget.Theme -> {
            val layers = themeLayers(data, target.themeId)
            layers.dropLast(1) to (layers.last() ?: EMPTY)
        }
        is DesignTarget.ScheduleOnly -> {
            val s = data.schedule(target.scheduleId)
            themeLayers(data, s?.themeId) to (s?.themeOverride ?: EMPTY)
        }
        is DesignTarget.WidgetOnly -> {
            val b = data.widgets.firstOrNull { it.appWidgetId == target.appWidgetId }
            val s = b?.scheduleId?.let { data.schedule(it) }
            (themeLayers(data, b?.themeId ?: s?.themeId) + s?.themeOverride) to (b?.themeOverride ?: EMPTY)
        }
    }

    companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}

/** 디자인 목록 조작 (순수 함수). */
object ThemeOps {

    /** 편집 대상에 덮어쓰기 저장. 비어 있으면 지운다. */
    fun saveOverride(data: AppData, target: DesignTarget, override: JsonObject, presets: ThemePresets? = null): AppData {
        val clean = override.takeIf { it.isNotEmpty() }
        return when (target) {
            is DesignTarget.Theme -> {
                val existing = data.themes.firstOrNull { it.id == target.themeId }
                val updated = when {
                    existing != null -> existing.copy(overrides = clean ?: ThemeLibrary.EMPTY)
                    target.themeId in ThemePresets.IDS -> UserTheme(target.themeId, presets?.name(target.themeId) ?: target.themeId, target.themeId, clean ?: ThemeLibrary.EMPTY)
                    else -> return data
                }
                // 고친 게 없는 프리셋 기록은 지워서 "처음 상태"로
                val drop = updated.id in ThemePresets.IDS && updated.overrides.isEmpty() && (presets == null || updated.name == presets.name(updated.id))
                data.copy(themes = data.themes.filterNot { it.id == updated.id } + if (drop) emptyList() else listOf(updated))
            }
            is DesignTarget.ScheduleOnly -> data.copy(schedules = data.schedules.map { if (it.id == target.scheduleId) it.copy(themeOverride = clean) else it })
            is DesignTarget.WidgetOnly -> data.copy(widgets = data.widgets.map { if (it.appWidgetId == target.appWidgetId) it.copy(themeOverride = clean) else it })
        }
    }

    /** 복제: 지금 보이는 모양 그대로(프리셋 + 고친 값) 새 디자인. */
    fun duplicate(data: AppData, themeId: String, newName: String, ids: IdGenerator = IdGenerator.UUIDS): Pair<AppData, UserTheme> {
        val src = data.themes.firstOrNull { it.id == themeId }
        val base = if (themeId in ThemePresets.IDS) themeId else src?.baseId ?: ThemePresets.DEFAULT_ID
        val copy = UserTheme(ids.next(), newName.trim(), base, src?.overrides ?: ThemeLibrary.EMPTY)
        return data.copy(themes = data.themes + copy) to copy
    }

    fun rename(data: AppData, themeId: String, newName: String, presets: ThemePresets? = null): AppData {
        val name = newName.trim()
        val existing = data.themes.firstOrNull { it.id == themeId }
        return when {
            existing != null -> data.copy(themes = data.themes.map { if (it.id == themeId) it.copy(name = name) else it })
            themeId in ThemePresets.IDS -> data.copy(themes = data.themes + UserTheme(themeId, name, themeId))
            else -> data
        }
    }

    /** 이 디자인을 쓰는 스케줄·위젯 수 (삭제 경고). */
    fun usage(data: AppData, themeId: String): Int =
        data.schedules.count { it.themeId == themeId } + data.widgets.count { it.themeId == themeId }

    /** 사용자 디자인 삭제. 쓰던 스케줄·위젯은 기본 디자인으로 (프리셋은 지울 수 없어 "처음 상태로"). */
    fun delete(data: AppData, themeId: String): AppData {
        if (themeId in ThemePresets.IDS) return resetPreset(data, themeId)
        return data.copy(
            themes = data.themes.filterNot { it.id == themeId },
            schedules = data.schedules.map { if (it.themeId == themeId) it.copy(themeId = null) else it },
            widgets = data.widgets.map { if (it.themeId == themeId) it.copy(themeId = null) else it },
        )
    }

    /** 프리셋을 앱에 들어 있던 처음 상태로. */
    fun resetPreset(data: AppData, presetId: String): AppData = data.copy(themes = data.themes.filterNot { it.id == presetId })
}

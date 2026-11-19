package io.github.rosymaro.familyweekly.theme

import kotlinx.serialization.json.JsonObject

/**
 * 기본 제공 테마 (assets/themes/<id>.json). 파일은 부분 JSON이고 코드 기본값 위에 덮어쓴다.
 * 사용자 테마 저장은 Phase 7.
 */
class ThemePresets(private val readAsset: (String) -> String?) {

    /** id → 부분 JSON. 못 읽은 파일은 빠진다. */
    val all: Map<String, JsonObject> by lazy {
        IDS.mapNotNull { id ->
            readAsset("themes/$id.json")?.let { text -> id to ThemeResolver.parse(text) }
        }.toMap()
    }

    fun get(id: String?): JsonObject? = all[id ?: DEFAULT_ID] ?: all[DEFAULT_ID]

    fun name(id: String): String =
        all[id]?.let { ThemeResolver.decode(ThemeResolver.merge(ThemeResolver.defaultsJson, it)).name } ?: id

    companion object {
        const val DEFAULT_ID = "soft-paper"
        val IDS = listOf("soft-paper", "clean-grid", "playful")
    }
}

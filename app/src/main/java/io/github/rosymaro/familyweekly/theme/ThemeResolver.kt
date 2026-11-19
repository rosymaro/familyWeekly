package io.github.rosymaro.familyweekly.theme

import io.github.rosymaro.familyweekly.domain.DarkMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** 위젯 크기 단계 (DESIGN.md 2절). */
enum class SizeClass(val key: String) {
    COMPACT("compact"), MEDIUM("medium"), LARGE("large");

    companion object {
        /**
         * dp 크기로 판정. 대략 4×2 이하 = compact, 5×4 이상 = large, 그 사이 = medium.
         * (런처마다 칸 크기가 달라서 칸 수가 아니라 dp로 본다)
         */
        fun of(widthDp: Float, heightDp: Float): SizeClass = when {
            heightDp < 200f || widthDp < 220f -> COMPACT
            widthDp >= 340f && heightDp >= 330f -> LARGE
            else -> MEDIUM
        }
    }
}

/** 최종 테마 + 라이트/다크. 그리는 쪽은 이것만 받는다. */
data class ResolvedTheme(
    val theme: WidgetTheme,
    val isDark: Boolean,
    val sizeClass: SizeClass,
) {
    fun color(c: ThemeColor): Int = c.resolve(isDark)
}

/**
 * 테마 병합: 코드 기본값 ← 프리셋 ← 스케줄 테마 덮어쓰기 ← 위젯 덮어쓰기 ← 크기 단계 기본 ← 테마의 sizeClass 덮어쓰기.
 * 병합은 **토큰(JSON 키) 단위**로 깊게 한다: 덮어쓰기 JSON에 있는 키만 바뀐다.
 */
object ThemeResolver {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
        isLenient = true
    }

    val defaultsJson: JsonObject by lazy { json.encodeToJsonElement(WidgetTheme.serializer(), WidgetTheme()).jsonObject }

    /**
     * 깊은 병합. 둘 다 객체면 키별로 다시 병합, 아니면 [override]가 이김.
     * override 값이 JSON null이면 그 토큰을 null로 (예: 오늘 열 틴트 끄기).
     */
    fun merge(base: JsonObject, override: JsonObject?): JsonObject {
        if (override == null || override.isEmpty()) return base
        val out = base.toMutableMap()
        for ((k, v) in override) {
            val b = out[k]
            out[k] = if (b is JsonObject && v is JsonObject && !isColorObject(v)) merge(b, v) else v
        }
        return JsonObject(out)
    }

    /** {"light":..,"dark":..} 색 객체는 통째로 바꾼다 (light만 바꾸고 예전 dark가 남는 일 방지). */
    private fun isColorObject(v: JsonObject): Boolean = "light" in v

    fun decode(obj: JsonObject): WidgetTheme = json.decodeFromJsonElement(WidgetTheme.serializer(), obj)

    fun encode(theme: WidgetTheme): JsonObject = json.encodeToJsonElement(WidgetTheme.serializer(), theme).jsonObject

    fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    fun resolve(
        layers: List<JsonObject?>,
        sizeClass: SizeClass,
        isDark: Boolean,
    ): ResolvedTheme {
        var merged = layers.fold(defaultsJson) { acc, layer -> merge(acc, layer) }
        val ownSizeOverrides = decode(merged).sizeClass[sizeClass.key]
        merged = merge(merged, BuiltInSizeDefaults.of(sizeClass))
        merged = merge(merged, ownSizeOverrides)
        return ResolvedTheme(decode(merged), isDark, sizeClass)
    }

    fun isDark(mode: DarkMode, systemDark: Boolean): Boolean = when (mode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
}

/** 크기 단계 기본 동작 (DESIGN.md 2절). 테마의 sizeClass 덮어쓰기가 이것보다 우선. */
object BuiltInSizeDefaults {
    private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    private fun s(v: String) = JsonPrimitive(v)

    val compact: JsonObject = obj(
        "header" to obj("subtitle" to obj("visible" to JsonPrimitive(false))),
        "timeAxis" to obj("showEveryNHours" to JsonPrimitive(2)),
        "block" to obj("showTime" to s("NEVER"), "showPlace" to s("NEVER"), "titleMaxLines" to JsonPrimitive(1)),
        "postIt" to obj("showTime" to s("NEVER")),
    )

    val large: JsonObject = obj(
        "block" to obj("showPlace" to s("IF_FITS")),
        "postIt" to obj("showTime" to s("ALWAYS")),
    )

    fun of(sizeClass: SizeClass): JsonObject = when (sizeClass) {
        SizeClass.COMPACT -> compact
        SizeClass.MEDIUM -> buildJsonObject { }
        SizeClass.LARGE -> large
    }
}

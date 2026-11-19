package io.github.rosymaro.familyweekly.widget

import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.TokenSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 위젯 설정 화면에서 "완료" 전까지 들고 있는 디자인 (디자인 · 다크 모드 · 이 위젯만 바꾼 토큰).
 * 되돌리기 = 처음 열었을 때 값으로. Android 의존성 없음.
 */
@Serializable
data class WidgetDesign(
    /** null = 스케줄 디자인 그대로 */
    val themeId: String? = null,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val override: JsonObject = JsonObject(emptyMap()),
) {
    /** 이 위젯만 바꾼 토큰 수 */
    val changedTokens: Int get() = TokenSchema.all.count { TokenSchema.isOverridden(override, it.path) }

    fun toBinding(appWidgetId: Int, scheduleId: String?) = WidgetBinding(
        appWidgetId = appWidgetId,
        scheduleId = scheduleId,
        themeId = themeId,
        darkMode = darkMode,
        themeOverride = override.takeIf { it.isNotEmpty() },
    )

    fun summary(themeName: String, usesScheduleTheme: Boolean): String = buildString {
        append(if (usesScheduleTheme) "스케줄 디자인 그대로 ($themeName)" else themeName)
        append(" · 다크 모드: ")
        append(
            when (darkMode) {
                DarkMode.SYSTEM -> "폰 설정 따라"
                DarkMode.LIGHT -> "라이트"
                DarkMode.DARK -> "다크"
            },
        )
        if (changedTokens > 0) append(" · 이 위젯만 바꾼 곳 ${changedTokens}개")
    }

    /** 화면 회전에도 남도록 문자열로 */
    fun encode(): String = ThemeResolver.json.encodeToString(serializer(), this)

    companion object {
        fun of(binding: WidgetBinding?) = WidgetDesign(
            themeId = binding?.themeId,
            darkMode = binding?.darkMode ?: DarkMode.SYSTEM,
            override = binding?.themeOverride ?: JsonObject(emptyMap()),
        )

        fun decode(text: String): WidgetDesign = runCatching { ThemeResolver.json.decodeFromString(serializer(), text) }.getOrDefault(WidgetDesign())
    }
}

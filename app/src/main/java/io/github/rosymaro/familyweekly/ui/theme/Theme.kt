package io.github.rosymaro.familyweekly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 앱 화면(Material) 테마. 위젯 디자인은 이 값을 쓰지 않고 WidgetTheme 토큰(agent/DESIGN.md)을 쓴다.
// 폰 배경화면 색을 따라가는 동적 색은 쓰지 않는다 (스케줄 색이 어떤 폰에서도 똑같이 보이게).
private val LightColors = lightColorScheme(
    primary = Color(0xFF8B5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF56633B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDAE9B6),
    onTertiaryContainer = Color(0xFF141F01),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),
    outline = Color(0xFF837469),
    outlineVariant = Color(0xFFD5C3B5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFBECD9C),
    onTertiary = Color(0xFF293411),
    tertiaryContainer = Color(0xFF3F4B25),
    onTertiaryContainer = Color(0xFFDAE9B6),
    background = Color(0xFF1E1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1E1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9E8E81),
    outlineVariant = Color(0xFF51443A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** 폰의 다크 모드 설정을 따른다. (앱 설정의 시스템/라이트/다크 선택은 이후 Phase에서 darkTheme 인자로 넘긴다) */
@Composable
fun FamilyWeeklyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

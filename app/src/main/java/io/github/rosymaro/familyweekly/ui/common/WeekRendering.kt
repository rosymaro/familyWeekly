package io.github.rosymaro.familyweekly.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.rosymaro.familyweekly.domain.ResolvedWeek
import io.github.rosymaro.familyweekly.layout.RenderSpec
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.render.WeekPainter
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemePresets
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import java.time.LocalDateTime

/** 스케줄 테마 + 크기 단계 + 다크로 최종 테마. */
fun resolveScheduleTheme(presets: ThemePresets, themeId: String?, sizeClass: SizeClass, dark: Boolean, vararg extra: kotlinx.serialization.json.JsonObject?) =
    ThemeResolver.resolve(listOf(presets.get(themeId)) + extra.toList(), sizeClass, dark)

/**
 * 위젯과 같은 렌더러로 그린 주간 그림 (홈 카드 미리보기 등). 탭 처리 없음.
 * 폭은 화면에 맞추고 높이는 [height].
 */
@Composable
fun WeekPreviewImage(
    week: ResolvedWeek,
    presets: ThemePresets,
    dark: Boolean,
    now: LocalDateTime,
    height: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current.density
        val fontScale = LocalConfiguration.current.fontScale
        val wDp = maxWidth.value
        val hDp = height.value
        val bitmap = remember(week, dark, now, wDp, hDp, density, fontScale, week.schedule.themeId) {
            val sizeClass = SizeClass.of(wDp, hDp)
            val rt = resolveScheduleTheme(presets, week.schedule.themeId, sizeClass, dark)
            val spec = RenderSpec((wDp * density).toInt().coerceAtLeast(1), (hDp * density).toInt().coerceAtLeast(1), density, fontScale)
            val layout = WeekLayout.compute(week, rt.theme, spec, now, sizeClass)
            WeekPainter().render(layout, rt).asImageBitmap()
        }
        Image(bitmap = bitmap, contentDescription = contentDescription, modifier = Modifier.fillMaxWidth().height(height))
    }
}

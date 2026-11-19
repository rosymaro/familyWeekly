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
import io.github.rosymaro.familyweekly.theme.ThemeLibrary
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import kotlinx.serialization.json.JsonObject
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import java.time.LocalDateTime


/**
 * 위젯과 같은 렌더러로 그린 주간 그림 (홈 카드 미리보기 등). 탭 처리 없음.
 * 폭은 화면에 맞추고 높이는 [height].
 */
@Composable
fun WeekPreviewImage(
    week: ResolvedWeek,
    library: ThemeLibrary,
    data: AppData,
    dark: Boolean,
    now: LocalDateTime,
    height: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** 위젯 미리보기면 그 위젯 설정 (위젯 디자인·덮어쓰기) */
    binding: WidgetBinding? = null,
    /** 디자인 편집기처럼 레이어를 직접 줄 때 */
    layers: List<JsonObject?>? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current.density
        val fontScale = LocalConfiguration.current.fontScale
        val wDp = maxWidth.value
        val hDp = height.value
        val bitmap = remember(week, dark, now, wDp, hDp, density, fontScale, data.themes, binding, layers) {
            val sizeClass = SizeClass.of(wDp, hDp)
            val rt = if (layers != null) ThemeResolver.resolve(layers, sizeClass, dark)
            else library.resolve(data, week.schedule, sizeClass, dark, binding)
            val spec = RenderSpec((wDp * density).toInt().coerceAtLeast(1), (hDp * density).toInt().coerceAtLeast(1), density, fontScale)
            val layout = WeekLayout.compute(week, rt.theme, spec, now, sizeClass)
            WeekPainter().render(layout, rt).asImageBitmap()
        }
        Image(bitmap = bitmap, contentDescription = contentDescription, modifier = Modifier.fillMaxWidth().height(height))
    }
}

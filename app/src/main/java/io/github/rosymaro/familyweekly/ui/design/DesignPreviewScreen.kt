package io.github.rosymaro.familyweekly.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.layout.RenderSpec
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.render.PainterResources
import io.github.rosymaro.familyweekly.render.SampleData
import io.github.rosymaro.familyweekly.render.WeekPainter
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemePresets
import io.github.rosymaro.familyweekly.theme.ThemeResolver

/** 미리보기 위젯 크기 (dp). SizeClass.of 기준에 맞춘 대표 크기. */
enum class PreviewSize(val label: String, val widthDp: Int, val heightDp: Int) {
    COMPACT("작게 4×2", 300, 170),
    MEDIUM("보통 4×4", 320, 320),
    LARGE("크게 5×5", 380, 420),
}

/**
 * 개발용 디자인 미리보기 (DESIGN.md 5절 샘플 주). 위젯과 같은 렌더러로 그린다.
 * Phase 7 디자인 편집기의 미리보기 부분이 될 화면.
 */
@Composable
fun DesignPreviewScreen(
    presets: ThemePresets,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetId by rememberSaveable { mutableStateOf(ThemePresets.DEFAULT_ID) }
    var size by rememberSaveable { mutableStateOf(PreviewSize.MEDIUM) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf(DaysMode.MON_FRI) }

    val density = LocalDensity.current
    val fontScale = LocalConfiguration.current.fontScale
    val painter = remember { WeekPainter(PainterResources()) }

    val bitmap = remember(presetId, size, dark, days, density.density, fontScale) {
        val spec = RenderSpec(
            widthPx = (size.widthDp * density.density).toInt(),
            heightPx = (size.heightDp * density.density).toInt(),
            density = density.density,
            fontScale = fontScale,
        )
        val sizeClass = SizeClass.of(size.widthDp.toFloat(), size.heightDp.toFloat())
        val rt = ThemeResolver.resolve(listOf(presets.get(presetId)), sizeClass, dark)
        val week = SampleData.week()
        val resolved = SampleData.resolved(days, week)
        val layout = WeekLayout.compute(resolved, rt.theme, spec, SampleData.now(week), sizeClass)
        painter.render(layout, rt).asImageBitmap()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text("디자인 미리보기 (개발용)", style = MaterialTheme.typography.titleMedium)
        }
        ChipRow {
            ThemePresets.IDS.forEach { id ->
                FilterChip(selected = presetId == id, onClick = { presetId = id }, label = { Text(presets.name(id)) })
            }
        }
        ChipRow {
            PreviewSize.entries.forEach { s ->
                FilterChip(selected = size == s, onClick = { size = s }, label = { Text(s.label) })
            }
        }
        ChipRow {
            FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("라이트") })
            FilterChip(selected = dark, onClick = { dark = true }, label = { Text("다크") })
            FilterChip(selected = days == DaysMode.MON_FRI, onClick = { days = DaysMode.MON_FRI }, label = { Text("월~금") })
            FilterChip(selected = days == DaysMode.MON_SUN, onClick = { days = DaysMode.MON_SUN }, label = { Text("월~일") })
        }
        Image(
            bitmap = bitmap,
            contentDescription = "위젯 미리보기",
            modifier = Modifier.size(size.widthDp.dp, size.heightDp.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            "샘플: 월 치과(기존 취소) · 화 보강(기존 유지)·미술 방학 · 수 체험학습(하루 종일)·현재 시간 16:20 · " +
                "목 공휴일(취소) · 금 겹친 블록·포스트잇 2개·학원 개별 취소 · 토일 가족여행",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

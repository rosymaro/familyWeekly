package io.github.rosymaro.familyweekly.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.layout.RenderSpec
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.render.SampleData
import io.github.rosymaro.familyweekly.render.WeekPainter
import io.github.rosymaro.familyweekly.theme.DesignHitTest
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.TokenLabels
import io.github.rosymaro.familyweekly.theme.TokenSchema
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 디자인 편집기 (DESIGN.md 5절): 위 = 실시간 미리보기(크기 3단계 × 라이트/다크 × 샘플/실제),
 * 아래 = 그룹별 접이식 토큰 편집. 미리보기를 누르면 그 부분 그룹으로 이동.
 * 홈·디자인 목록에서 열 때 쓰는 화면 (저장·되돌리기 버튼이 여기 있음).
 * 위젯 설정에서 열 때는 위젯 설정 화면이 [DesignEditorBody]를 직접 쓰고, 저장은 위젯 설정의 "완료"에서 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignEditorScreen(onBack: () -> Unit, vm: DesignEditorViewModel = viewModel(factory = DesignEditorViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    fun leave() = if (dirty) confirmLeave = true else onBack()
    BackHandler(onBack = ::leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("디자인 꾸미기")
                        Text(vm.targetLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    TextButton(enabled = dirty, onClick = vm::revertAll) { Text("되돌리기") }
                    TextButton(enabled = dirty, onClick = vm::save) { Text("저장") }
                },
            )
        },
    ) { padding ->
        DesignEditorBody(
            data = data,
            baseLayers = vm.baseLayers(),
            draft = draft,
            previewScheduleId = vm.previewScheduleId(),
            holidays = vm.holidays(),
            onSet = vm::set,
            onReset = vm::reset,
            onResetGroup = vm::resetGroup,
            hint = "미리보기를 누르면 그 부분을 꾸미는 곳으로 가요. 굵은 파란 글씨 = 바꾼 값 (저장하면 적용)",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("저장하지 않은 변경") },
            text = { Text("바꾼 디자인을 저장할까요?") },
            confirmButton = { TextButton(onClick = { vm.save(); confirmLeave = false; onBack() }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("버리기") } },
        )
    }
}

/**
 * 편집기 본문 (미리보기 + 토큰 패널). 저장 방법은 부르는 쪽이 정한다.
 * [baseLayers] = 편집 중인 덮어쓰기 바로 밑 레이어, [draft] = 바꾼 토큰만 모은 덮어쓰기.
 * [header] = 토큰 패널 위에 붙일 내용 (위젯 설정의 디자인 고르기·다크 모드 등).
 */
@Composable
fun DesignEditorBody(
    data: AppData,
    baseLayers: List<JsonObject?>,
    draft: JsonObject,
    previewScheduleId: String?,
    holidays: HolidayCalendar,
    onSet: (List<String>, JsonElement) -> Unit,
    onReset: (List<String>) -> Unit,
    onResetGroup: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    initialDark: Boolean = false,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var size by rememberSaveable { mutableStateOf(PreviewSize.MEDIUM) }
    var dark by rememberSaveable(initialDark) { mutableStateOf(initialDark) }
    var sample by rememberSaveable { mutableStateOf(previewScheduleId == null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val effective = remember(baseLayers, draft) { ThemeResolver.effective(baseLayers + draft) }
    val groups = TokenSchema.groups
    val firstGroupIndex = (if (header != null) 1 else 0) + 1

    Column(modifier) {
        // ── 미리보기 ──
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewSize.entries.forEach { s -> FilterChip(size == s, { size = s }, label = { Text(s.label) }) }
            FilterChip(!dark, { dark = false }, label = { Text("라이트") })
            FilterChip(dark, { dark = true }, label = { Text("다크") })
            FilterChip(sample, { sample = true }, label = { Text("샘플 주") })
            if (previewScheduleId != null) FilterChip(!sample, { sample = false }, label = { Text("실제 일정") })
        }
        Preview(baseLayers + draft, size, dark, sample, previewScheduleId, data, holidays) { group ->
            expanded = expanded + group
            val index = groups.indexOf(group)
            if (index >= 0) scope.launch { listState.animateScrollToItem(firstGroupIndex + index) }
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 4.dp))

        // ── 토큰 ──
        LazyColumn(state = listState, contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (header != null) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = header) }
            item { Text("꾸밀 부분", style = MaterialTheme.typography.titleSmall) }
            items(groups, key = { it }) { g ->
                val open = g in expanded
                val changed = TokenSchema.overriddenCount(draft, g)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (open) expanded - g else expanded + g }, verticalAlignment = Alignment.CenterVertically) {
                            Text(TokenLabels.group(g), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (changed > 0) Badge { Text("$changed") }
                            Text(if (open) "  ▲" else "  ▼")
                        }
                        if (open) {
                            if (changed > 0) TextButton(onClick = { onResetGroup(g) }) { Text("이 부분 전부 기본값으로") }
                            TokenSchema.inGroup(g).filter { visible(it, effective) }.forEach { spec ->
                                TokenRow(
                                    spec = spec,
                                    value = TokenSchema.get(effective, spec.path),
                                    overridden = TokenSchema.isOverridden(draft, spec.path),
                                    onSet = onSet,
                                    onReset = onReset,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(
    layers: List<JsonObject?>,
    size: PreviewSize,
    dark: Boolean,
    sample: Boolean,
    previewScheduleId: String?,
    data: AppData,
    holidays: HolidayCalendar,
    onTapGroup: (String) -> Unit,
) {
    val density = LocalDensity.current.density
    val fontScale = LocalConfiguration.current.fontScale
    val painter = remember { WeekPainter() }
    // 화면에 넣기 위해 미리보기 크기를 줄여서 보여 줌 (모양은 같은 비율)
    val shownScale = if (size == PreviewSize.LARGE) 0.75f else if (size == PreviewSize.MEDIUM) 0.85f else 1f
    val result = remember(size, dark, sample, layers, data, previewScheduleId, density, fontScale) {
        val sc = SizeClass.of(size.widthDp.toFloat(), size.heightDp.toFloat())
        val rt = ThemeResolver.resolve(layers, sc, dark)
        val now = LocalDateTime.now()
        val real = if (!sample && previewScheduleId != null) {
            OccurrenceResolver.resolve(data, previewScheduleId, WeekRange.of(now.toLocalDate()), holidays, now)
        } else null
        val week = if (real != null) real to now else {
            val w = SampleData.week()
            SampleData.resolved(DaysMode.MON_FRI, w) to SampleData.now(w)
        }
        val spec = RenderSpec((size.widthDp * density).toInt(), (size.heightDp * density).toInt(), density, fontScale)
        val layout = WeekLayout.compute(week.first, rt.theme, spec, week.second, sc)
        layout to painter.render(layout, rt).asImageBitmap()
    }
    val (layout, bitmap) = result
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = "디자인 미리보기 (누르면 그 부분 꾸미기)",
            modifier = Modifier
                .size((size.widthDp * shownScale).dp, (size.heightDp * shownScale).dp)
                .pointerInput(layout) {
                    detectTapGestures { pos ->
                        // 화면에 보이는 크기 → 그림 크기
                        val sx = layout.spec.widthPx / this.size.width.toFloat()
                        val sy = layout.spec.heightPx / this.size.height.toFloat()
                        onTapGroup(DesignHitTest.groupAt(layout, pos.x * sx, pos.y * sy))
                    }
                },
        )
    }
}

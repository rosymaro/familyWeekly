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
import io.github.rosymaro.familyweekly.domain.DaysMode
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignEditorScreen(onBack: () -> Unit, vm: DesignEditorViewModel = viewModel(factory = DesignEditorViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    var size by rememberSaveable { mutableStateOf(PreviewSize.MEDIUM) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var sample by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirmLeave by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val effective = remember(draft, data.themes, data.schedules, data.widgets) { vm.effective() }
    val groups = TokenSchema.groups

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
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── 미리보기 ──
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewSize.entries.forEach { s -> FilterChip(size == s, { size = s }, label = { Text(s.label) }) }
                FilterChip(!dark, { dark = false }, label = { Text("라이트") })
                FilterChip(dark, { dark = true }, label = { Text("다크") })
                FilterChip(sample, { sample = true }, label = { Text("샘플 주") })
                FilterChip(!sample, { sample = false }, label = { Text("실제 일정") })
            }
            Preview(vm, size, dark, sample, draft, data) { group ->
                expanded = expanded + group
                val index = groups.indexOf(group)
                if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
            }
            Text(
                "미리보기를 누르면 그 부분을 꾸미는 곳으로 가요. 굵은 파란 글씨 = 바꾼 값 (저장하면 적용)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(Modifier.padding(top = 4.dp))

            // ── 토큰 ──
            LazyColumn(state = listState, contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                if (changed > 0) TextButton(onClick = { vm.resetGroup(g) }) { Text("이 부분 전부 기본값으로") }
                                TokenSchema.inGroup(g).filter { visible(it, effective) }.forEach { spec ->
                                    TokenRow(
                                        spec = spec,
                                        value = TokenSchema.get(effective, spec.path),
                                        overridden = TokenSchema.isOverridden(draft, spec.path),
                                        onSet = vm::set,
                                        onReset = vm::reset,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun Preview(
    vm: DesignEditorViewModel,
    size: PreviewSize,
    dark: Boolean,
    sample: Boolean,
    draft: kotlinx.serialization.json.JsonObject,
    data: io.github.rosymaro.familyweekly.domain.AppData,
    onTapGroup: (String) -> Unit,
) {
    val density = LocalDensity.current.density
    val fontScale = LocalConfiguration.current.fontScale
    val painter = remember { WeekPainter() }
    // 화면에 넣기 위해 미리보기 크기를 줄여서 보여 줌 (모양은 같은 비율)
    val shownScale = if (size == PreviewSize.LARGE) 0.75f else if (size == PreviewSize.MEDIUM) 0.85f else 1f
    val result = remember(size, dark, sample, draft, data, density, fontScale) {
        val sc = SizeClass.of(size.widthDp.toFloat(), size.heightDp.toFloat())
        val rt = ThemeResolver.resolve(vm.baseLayers() + draft, sc, dark)
        val now = LocalDateTime.now()
        val week = if (sample || vm.previewScheduleId() == null) {
            val w = SampleData.week()
            SampleData.resolved(DaysMode.MON_FRI, w) to SampleData.now(w)
        } else {
            val id = vm.previewScheduleId()!!
            OccurrenceResolver.resolve(data, id, WeekRange.of(now.toLocalDate()), vm.holidays(), now)!! to now
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

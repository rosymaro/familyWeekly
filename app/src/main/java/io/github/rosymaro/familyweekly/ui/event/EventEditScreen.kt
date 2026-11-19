package io.github.rosymaro.familyweekly.ui.event

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.EventOps
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.OverlapAction
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.domain.VacationOps
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.common.ColorKeyChips
import io.github.rosymaro.familyweekly.ui.common.DateField
import io.github.rosymaro.familyweekly.ui.common.TimeStepper
import io.github.rosymaro.familyweekly.ui.common.korean

/** 특이 일정 추가/수정. 겹치는 기존 일정은 스케줄마다 "유지/취소"를 꼭 고르게 한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(onDone: () -> Unit, vm: EventEditViewModel = viewModel(factory = EventEditViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    val e by vm.draft.collectAsStateWithLifecycle()
    val dark = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())
    val papers = remember(dark) {
        ThemeResolver.resolve(listOf(vm.presets.get(null)), SizeClass.MEDIUM, dark).let { rt -> rt.theme.postIt.papers.mapValues { rt.color(it.value) } }
    }
    val overlaps = remember(data, e) { OccurrenceResolver.overlappingBlocks(data, e) }
    val problem = EventOps.canSave(data, e)
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "특이 일정 추가" else "특이 일정 수정") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = { TextButton(enabled = problem == null, onClick = { vm.save(onDone) }) { Text("저장") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(e.title, { t -> vm.edit { it.copy(title = t) } }, label = { Text("제목 (예: 치과, 체험학습)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("날짜", e.date, { d -> vm.edit { it.copy(date = d) } })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하루 종일", modifier = Modifier.weight(1f))
                Switch(e.allDay, { on ->
                    vm.edit {
                        // 하루 종일을 끌 때 시각이 비어 있으면 15:00~16:00으로
                        if (!on && it.endMin <= it.startMin) it.copy(allDay = false, startMin = 15 * 60, endMin = 16 * 60)
                        else it.copy(allDay = on)
                    }
                })
            }
            if (!e.allDay) {
                TimeStepper("시작", e.startMin, { s ->
                    vm.edit { val dur = (it.endMin - it.startMin).coerceAtLeast(TimeStep.STEP_MIN); it.copy(startMin = s, endMin = (s + dur).coerceAtMost(TimeStep.MINUTES_PER_DAY)) }
                }, max = TimeStep.MINUTES_PER_DAY - TimeStep.STEP_MIN)
                TimeStepper("끝", e.endMin, { x -> vm.edit { it.copy(endMin = x) } }, min = TimeStep.STEP_MIN)
            }

            Text("붙일 스케줄 (여러 명 가능)", style = MaterialTheme.typography.labelLarge)
            ScheduleChips(data, e.scheduleIds) { ids -> vm.edit { it.copy(scheduleIds = ids) } }

            if (!vm.isNew && e.scheduleIds.isNotEmpty()) {
                Text("시간표에 포스트잇 보이기", style = MaterialTheme.typography.labelLarge)
                Text(
                    "끄면 그 아이 시간표·위젯에서만 포스트잇을 떼요. 특이 일정은 캘린더에 남아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                e.scheduleIds.mapNotNull { data.schedule(it) }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, modifier = Modifier.weight(1f))
                        Switch(!e.isDetachedFrom(s.id), { on ->
                            vm.edit { it.copy(detachedFrom = if (on) it.detachedFrom - s.id else it.detachedFrom + s.id) }
                        })
                    }
                }
            }

            if (overlaps.isNotEmpty()) {
                Text("겹치는 기존 일정", style = MaterialTheme.typography.labelLarge)
                Text(
                    "포스트잇은 기존 일정 위에 붙어요. 기존 일정을 그대로 할지(유지) 빠질지(취소) 아이마다 골라 주세요. 취소하면 알림도 울리지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                overlaps.forEach { (sid, blocks) ->
                    val choice = e.overlapActions[sid]
                    Card(
                        colors = if (choice == null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(data.schedule(sid)?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                blocks.joinToString { "${it.title} ${TimeStep.format(it.startMin)}~${TimeStep.format(it.endMin)}" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(choice == OverlapAction.KEEP, { vm.edit { it.copy(overlapActions = it.overlapActions + (sid to OverlapAction.KEEP)) } }, label = { Text("기존 유지") })
                                FilterChip(choice == OverlapAction.CANCEL, { vm.edit { it.copy(overlapActions = it.overlapActions + (sid to OverlapAction.CANCEL)) } }, label = { Text("기존 취소") })
                            }
                            if (choice == null) Text("아직 고르지 않았어요", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text("포스트잇 색", style = MaterialTheme.typography.labelLarge)
            ColorKeyChips(papers, e.paperKey) { k -> vm.edit { it.copy(paperKey = k) } }
            OutlinedTextField(e.memo.orEmpty(), { m -> vm.edit { it.copy(memo = m) } }, label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
            Text("알림 설정은 다음 업데이트(Phase 6)에서 추가돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vm.isNew) TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f)) {}
                Button(enabled = problem == null, onClick = { vm.save(onDone) }) { Text("저장") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${e.title}' 삭제") },
            text = { Text("이 특이 일정 때문에 취소됐던 기존 일정은 다시 활성으로 돌아가요.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/** 스케줄(아이) 여러 명 고르기. */
@Composable
fun ScheduleChips(data: AppData, selected: List<String>, onChange: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        data.schedules.forEach { s ->
            FilterChip(
                selected = s.id in selected,
                onClick = { onChange(if (s.id in selected) selected - s.id else selected + s.id) },
                label = { Text(listOfNotNull(s.emoji, s.name).joinToString(" ")) },
            )
        }
    }
}

/** 휴가 추가/수정: 기간, 대상 스케줄, 전체 일정 / 일부 일정. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationEditScreen(onDone: () -> Unit, vm: VacationEditViewModel = viewModel(factory = VacationEditViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    val v by vm.draft.collectAsStateWithLifecycle()
    val problem = VacationOps.validate(VacationOps.normalize(data, v))
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "휴가 추가" else "휴가 수정") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = { TextButton(enabled = problem == null, onClick = { vm.save(onDone) }) { Text("저장") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "휴가 동안 반복 일정은 지워지지 않고 '취소'로 보이며, 알림이 울리지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(v.title, { t -> vm.edit { it.copy(title = t) } }, label = { Text("제목 (예: 여름방학, 할머니댁)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField("시작", v.from, { d -> vm.edit { it.copy(from = d, to = if (it.to.isBefore(d)) d else it.to) } })
            DateField("끝", v.to, { d -> vm.edit { it.copy(to = d) } })

            Text("적용할 스케줄", style = MaterialTheme.typography.labelLarge)
            ScheduleChips(data, v.scheduleIds) { ids -> vm.edit { it.copy(scheduleIds = ids) } }

            Text("취소할 일정", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(v.blockIds == null, { vm.edit { it.copy(blockIds = null) } }, label = { Text("모든 일정") })
                FilterChip(v.blockIds != null, { vm.edit { it.copy(blockIds = it.blockIds ?: emptyList()) } }, label = { Text("일부 일정만 (예: 학원 방학)") })
            }
            if (v.blockIds != null) {
                v.scheduleIds.mapNotNull { data.schedule(it) }.forEach { s ->
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    s.blocks.sortedWith(compareBy({ it.day }, { it.startMin })).forEach { b ->
                        val checked = b.id in v.blockIds.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { on ->
                                vm.edit { it.copy(blockIds = if (on) it.blockIds.orEmpty() + b.id else it.blockIds.orEmpty() - b.id) }
                            })
                            Text("${b.day.korean()} ${TimeStep.format(b.startMin)} ${b.title}")
                        }
                    }
                    if (s.blocks.isEmpty()) Text("반복 일정이 없어요", style = MaterialTheme.typography.bodySmall)
                }
            }

            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vm.isNew) TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f)) {}
                Button(enabled = problem == null, onClick = { vm.save(onDone) }) { Text("저장") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${v.title}' 삭제") },
            text = { Text("이 휴가로 취소됐던 반복 일정이 다시 활성으로 돌아가요.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

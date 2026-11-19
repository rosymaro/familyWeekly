package io.github.rosymaro.familyweekly.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.data.LoadIssue
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.TodaySummary
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.theme.ThemePresets
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.common.WeekPreviewImage
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private sealed interface HomeDialog {
    data object Add : HomeDialog
    data class Rename(val id: String) : HomeDialog
    data class Duplicate(val id: String) : HomeDialog
    data class Delete(val id: String) : HomeDialog
    data class Theme(val id: String) : HomeDialog
}

/** 홈: 아이별 스케줄 카드 목록. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSchedule: (String) -> Unit,
    onOpenDesignPreview: () -> Unit,
    onOpenCalendar: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val loadIssue by vm.loadIssue.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val now = rememberMinuteTicker()
    val dark = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())
    val holidays = remember(data.customHolidays) { vm.holidays() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("familyWeekly") },
                actions = {
                    IconButton(onClick = onOpenCalendar) { Icon(Icons.Default.DateRange, contentDescription = "캘린더 (특이 일정·휴가·공휴일)") }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "메뉴") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("캘린더") }, onClick = { menu = false; onOpenCalendar() })
                        DropdownMenuItem(text = { Text("디자인 미리보기 (개발용)") }, onClick = { menu = false; onOpenDesignPreview() })
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = HomeDialog.Add }) { Icon(Icons.Default.Add, contentDescription = "스케줄 추가") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            loadIssue?.let { issue -> item { LoadIssueCard(issue, vm::clearLoadIssue) } }
            if (data.schedules.isEmpty()) {
                item { EmptyHome(onAdd = { dialog = HomeDialog.Add }) }
            }
            itemsIndexed(data.schedules, key = { _, s -> s.id }) { index, schedule ->
                val week = remember(data, schedule.id, now.toLocalDate(), holidays) {
                    OccurrenceResolver.resolve(data, schedule.id, WeekRange.of(now.toLocalDate()), holidays, now)
                }
                ScheduleCard(
                    schedule = schedule,
                    summary = week?.let { TodaySummary.of(it, now).text() } ?: "",
                    preview = {
                        week?.let {
                            WeekPreviewImage(it, vm.presets, dark, now, height = 170.dp, contentDescription = "${schedule.name} 이번 주 미리보기")
                        }
                    },
                    isFirst = index == 0,
                    isLast = index == data.schedules.lastIndex,
                    onOpen = { onOpenSchedule(schedule.id) },
                    onRename = { dialog = HomeDialog.Rename(schedule.id) },
                    onDuplicate = { dialog = HomeDialog.Duplicate(schedule.id) },
                    onDelete = { dialog = HomeDialog.Delete(schedule.id) },
                    onTheme = { dialog = HomeDialog.Theme(schedule.id) },
                    onToggleAlarms = { vm.toggleAlarms(schedule.id) },
                    onMoveUp = { vm.move(schedule.id, index - 1) },
                    onMoveDown = { vm.move(schedule.id, index + 1) },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        HomeDialog.Add -> AddScheduleDialog({ dialog = null }) { name, emoji, days ->
            vm.add(name, emoji, days); dialog = null
        }
        is HomeDialog.Rename -> data.schedule(d.id)?.let { s ->
            RenameDialog(s.name, { dialog = null }) { vm.rename(s.id, it); dialog = null }
        }
        is HomeDialog.Duplicate -> data.schedule(d.id)?.let { s ->
            DuplicateDialog(s.name, { dialog = null }) { name, attach -> vm.duplicate(s.id, name, attach); dialog = null }
        }
        is HomeDialog.Delete -> data.schedule(d.id)?.let { s ->
            DeleteScheduleDialog(s.name, vm.orphansIfDeleted(s.id), { dialog = null }) { remove -> vm.delete(s.id, remove); dialog = null }
        }
        is HomeDialog.Theme -> data.schedule(d.id)?.let { s ->
            ThemePickerDialog(
                options = ThemePresets.IDS.map { it to vm.presets.name(it) },
                current = s.themeId ?: ThemePresets.DEFAULT_ID,
                onDismiss = { dialog = null },
            ) { vm.setTheme(s.id, it); dialog = null }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    summary: String,
    preview: @Composable () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onTheme: () -> Unit,
    onToggleAlarms: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(schedule.emoji, schedule.name).joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!schedule.alarmsEnabled) {
                        Text("알림 꺼짐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "${schedule.name} 메뉴") }
                    DropdownMenu(menu, { menu = false }) {
                        val items = buildList {
                            add("열기" to onOpen)
                            add("이름 변경" to onRename)
                            add("복제" to onDuplicate)
                            add("디자인" to onTheme)
                            add((if (schedule.alarmsEnabled) "알림 끄기" else "알림 켜기") to onToggleAlarms)
                            if (!isFirst) add("위로 옮기기" to onMoveUp)
                            if (!isLast) add("아래로 옮기기" to onMoveDown)
                            add("삭제" to onDelete)
                        }
                        items.forEach { (text, action) ->
                            DropdownMenuItem(text = { Text(text) }, onClick = { menu = false; action() })
                        }
                    }
                }
            }
            preview()
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("아직 스케줄이 없어요", style = MaterialTheme.typography.titleMedium)
        Text(
            "아이마다 주간 시간표를 하나씩 만들어 보세요.\n만든 시간표는 홈 화면 위젯으로 볼 수 있어요.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(4.dp))
        Button(onClick = onAdd) { Text("스케줄 추가") }
    }
}

@Composable
private fun LoadIssueCard(issue: LoadIssue, onDismiss: () -> Unit) {
    val message = when (issue) {
        is LoadIssue.Corrupted ->
            "저장된 일정 파일을 읽지 못해 빈 상태로 시작했어요.\n원래 파일은 ${issue.backup.name}(으)로 보관해 두었어요."
        is LoadIssue.NewerVersion ->
            "더 새 버전 앱에서 만든 일정 파일이에요(형식 ${issue.fileVersion}).\n앱을 업데이트해 주세요. 그전까지는 저장하지 않아요."
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (issue is LoadIssue.Corrupted) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("확인") }
            }
        }
    }
}

/** 1분마다 바뀌는 지금 시각 (오늘 요약·현재 시간선). */
@Composable
fun rememberMinuteTicker(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalDateTime.now()
            delay(((60 - t.second) * 1000L - t.nano / 1_000_000).coerceAtLeast(200))
            now = LocalDateTime.now().withSecond(0).withNano(0)
        }
    }
    return now
}

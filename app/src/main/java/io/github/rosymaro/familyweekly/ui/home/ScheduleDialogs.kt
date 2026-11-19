package io.github.rosymaro.familyweekly.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.Orphans

/** 스케줄 추가: 이름, 이모지, 요일(월~금 / 월~일). */
@Composable
fun AddScheduleDialog(onDismiss: () -> Unit, onAdd: (name: String, emoji: String, days: DaysMode) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf(DaysMode.MON_FRI) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("스케줄 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 첫째 하린)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("이모지 (선택)") }, singleLine = true, modifier = Modifier.width(140.dp))
                DaysModeChips(days) { days = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name, emoji, days) }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun DaysModeChips(value: DaysMode, onChange: (DaysMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = value == DaysMode.MON_FRI, onClick = { onChange(DaysMode.MON_FRI) }, label = { Text("월~금") })
        FilterChip(selected = value == DaysMode.MON_SUN, onClick = { onChange(DaysMode.MON_SUN) }, label = { Text("월~일") })
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 변경") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onRename(name) }) { Text("변경") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 복제: 새 이름 + 특이 일정·휴가도 붙일지. */
@Composable
fun DuplicateDialog(sourceName: String, onDismiss: () -> Unit, onDuplicate: (name: String, attach: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("$sourceName 복사본") }
    var attach by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("스케줄 복제") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("반복 일정·이번 주만 취소·알림·디자인을 그대로 복사해요.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(name, { name = it }, label = { Text("새 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(attach, { attach = it })
                    Text("특이 일정·휴가도 같이 붙이기")
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onDuplicate(name, attach) }) { Text("복제") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 삭제 확인. 대상이 없어지는 특이 일정·휴가가 있으면 같이 지울지 묻는다. */
@Composable
fun DeleteScheduleDialog(name: String, orphans: Orphans, onDismiss: () -> Unit, onDelete: (removeOrphans: Boolean) -> Unit) {
    var removeOrphans by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'$name' 삭제") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("반복 일정과 알림이 모두 지워져요. 되돌릴 수 없어요.\n이 스케줄을 보여 주던 위젯은 '연결된 스케줄 없음'으로 바뀌어요.")
                if (!orphans.isEmpty) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(removeOrphans, { removeOrphans = it })
                        Text("이 스케줄에만 붙은 특이 일정 ${orphans.eventIds.size}개 · 휴가 ${orphans.vacationIds.size}개도 삭제")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(removeOrphans) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 디자인(테마 프리셋) 고르기. 세부 편집은 Phase 7. */
@Composable
fun ThemePickerDialog(
    options: List<Pair<String, String>>,
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("디자인") },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = selected == id, onClick = { selected = id }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == id, onClick = { selected = id })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

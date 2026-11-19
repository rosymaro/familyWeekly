package io.github.rosymaro.familyweekly.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.domain.AlarmRule
import io.github.rosymaro.familyweekly.domain.BlockDraft
import io.github.rosymaro.familyweekly.domain.BlockOps
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.ui.common.ColorKeyChips
import io.github.rosymaro.familyweekly.ui.common.DayChips
import io.github.rosymaro.familyweekly.ui.common.TimeStepper
import io.github.rosymaro.familyweekly.ui.settings.AlarmOverrideRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 수정 시트를 연 상태. blockId가 null이면 새 블록. */
data class BlockEditState(
    val blockId: String?,
    /** 이번 주 그 블록의 날짜 (이번 주만 취소용). 새 블록이면 null. */
    val date: LocalDate?,
    val draft: BlockDraft,
    val groupSize: Int,
    val cancelledThisWeek: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditSheet(
    state: BlockEditState,
    availableDays: List<DayOfWeek>,
    palette: Map<String, Int>,
    /** 이 블록에 따로 정하지 않으면 쓰는 알림 (스케줄 기본값 또는 앱 기본값) */
    inheritedAlarm: AlarmRule,
    onDismiss: () -> Unit,
    onSave: (BlockDraft, applyToGroup: Boolean) -> Unit,
    onDelete: (wholeGroup: Boolean) -> Unit,
    onToggleCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(state) { mutableStateOf(state.draft) }
    var applyToGroup by remember(state) { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = state.blockId == null
    val grouped = state.groupSize > 1
    val error = BlockOps.validate(draft)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) "새 일정" else "일정 수정", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text("제목") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (grouped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("같은 일정(${state.groupSize}개 요일) 모두 수정", modifier = Modifier.weight(1f))
                    Switch(applyToGroup, { on ->
                        applyToGroup = on
                        if (!on) draft = draft.copy(days = setOf(state.draft.days.first()))
                    })
                }
            }
            Text("요일 (매주 반복)", style = MaterialTheme.typography.labelLarge)
            DayChips(
                available = availableDays,
                selected = draft.days,
                onChange = { draft = draft.copy(days = it) },
                single = !isNew && !applyToGroup,
            )

            TimeStepper("시작", draft.startMin, { s ->
                val dur = draft.endMin - draft.startMin
                draft = draft.copy(startMin = s, endMin = (s + dur).coerceAtMost(TimeStep.MINUTES_PER_DAY))
            }, max = TimeStep.MINUTES_PER_DAY - TimeStep.STEP_MIN)
            TimeStepper("끝", draft.endMin, { draft = draft.copy(endMin = it) }, min = TimeStep.STEP_MIN)

            Text("색", style = MaterialTheme.typography.labelLarge)
            ColorKeyChips(palette, draft.colorKey) { draft = draft.copy(colorKey = it) }

            OutlinedTextField(draft.place.orEmpty(), { draft = draft.copy(place = it) }, label = { Text("장소 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.memo.orEmpty(), { draft = draft.copy(memo = it) }, label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
            AlarmOverrideRow(
                label = "이 일정만 알림 따로",
                rule = draft.alarm,
                inherited = inheritedAlarm,
                inheritedLabel = "아이 기본 알림",
                onChange = { draft = draft.copy(alarm = it) },
            )

            if (!isNew && state.date != null) {
                HorizontalDivider()
                val label = state.date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
                OutlinedButton(onClick = onToggleCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.cancelledThisWeek) "$label 취소 해제" else "이번 주만 취소 ($label)")
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("닫기") }
                Button(enabled = error == null, onClick = { onSave(draft, applyToGroup || isNew) }) { Text("저장") }
            }
            Spacer(Modifier.padding(4.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${state.draft.title}' 삭제") },
            text = { Text(if (grouped) "매주 반복되는 이 일정을 지울까요?" else "매주 반복되는 이 일정을 지울까요? 되돌릴 수 없어요.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (grouped) TextButton(onClick = { confirmDelete = false; onDelete(true) }) { Text("모든 요일 삭제", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmDelete = false; onDelete(false) }) {
                        Text(if (grouped) "이 요일만 삭제" else "삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

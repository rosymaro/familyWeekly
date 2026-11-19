package io.github.rosymaro.familyweekly.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.domain.HolidayPolicy
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.TimeRange
import io.github.rosymaro.familyweekly.domain.TimeRangeMode
import io.github.rosymaro.familyweekly.ui.common.TimeStepper
import io.github.rosymaro.familyweekly.ui.home.DaysModeChips

/** 스케줄 설정: 이름·이모지, 요일, 위젯 시간 범위, 공휴일 처리. */
@Composable
fun ScheduleSettingsDialog(schedule: Schedule, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    var s by remember(schedule) { mutableStateOf(schedule) }
    val range = s.timeRange
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("스케줄 설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.emoji.orEmpty(), { s = s.copy(emoji = it.take(4).ifEmpty { null }) }, label = { Text("이모지") }, singleLine = true)

                Text("요일", style = MaterialTheme.typography.labelLarge)
                DaysModeChips(s.days) { s = s.copy(days = it) }

                Text("위젯에 보일 시간 범위", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(range.mode == TimeRangeMode.AUTO, { s = s.copy(timeRange = range.copy(mode = TimeRangeMode.AUTO)) }, label = { Text("자동 (첫 일정~마지막 일정)") })
                    FilterChip(range.mode == TimeRangeMode.FIXED, { s = s.copy(timeRange = range.copy(mode = TimeRangeMode.FIXED)) }, label = { Text("직접") })
                }
                if (range.mode == TimeRangeMode.FIXED) {
                    TimeStepper("시작", range.startMin, { v -> s = s.copy(timeRange = fixRange(range, start = v)) }, max = 23 * 60)
                    TimeStepper("끝", range.endMin, { v -> s = s.copy(timeRange = fixRange(range, end = v)) }, min = 60)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("공휴일엔 반복 일정 취소")
                        Text("켜면 공휴일의 반복 일정이 취소로 보이고 알림이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.holidayPolicy == HolidayPolicy.CANCEL_BLOCKS, { on ->
                        s = s.copy(holidayPolicy = if (on) HolidayPolicy.CANCEL_BLOCKS else HolidayPolicy.SHOW_ONLY)
                    })
                }
            }
        },
        confirmButton = { TextButton(enabled = s.name.isNotBlank(), onClick = { onSave(s.copy(name = s.name.trim())) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 시작 < 끝을 지키며 바꾸기 (최소 1시간). */
private fun fixRange(r: TimeRange, start: Int = r.startMin, end: Int = r.endMin): TimeRange {
    var s = start
    var e = end
    if (start != r.startMin && e - s < 60) e = (s + 60).coerceAtMost(24 * 60)
    if (end != r.endMin && e - s < 60) s = (e - 60).coerceAtLeast(0)
    return r.copy(startMin = s, endMin = e)
}

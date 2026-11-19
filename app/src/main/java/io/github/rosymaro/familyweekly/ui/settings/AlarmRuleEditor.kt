package io.github.rosymaro.familyweekly.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.alarm.planner.AlarmRuleText
import io.github.rosymaro.familyweekly.domain.AlarmRule
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.AlarmTrigger
import io.github.rosymaro.familyweekly.domain.SoundKind
import io.github.rosymaro.familyweekly.domain.TriggerType
import io.github.rosymaro.familyweekly.domain.VibrationPattern

/**
 * "이 일정만 알림 따로" 같은 덮어쓰기 줄: 스위치 + 요약 + 편집.
 * [rule]이 null이면 [inheritedLabel](위 단계 규칙)을 따른다.
 */
@Composable
fun AlarmOverrideRow(
    label: String,
    rule: AlarmRule?,
    inherited: AlarmRule,
    inheritedLabel: String,
    onChange: (AlarmRule?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(rule != null, { on -> onChange(if (on) inherited else null) })
        }
        Text(
            if (rule == null) "$inheritedLabel: ${AlarmRuleText.summary(inherited)}" else AlarmRuleText.summary(rule),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rule != null) TextButton(onClick = { editing = true }) { Text("알림 편집") }
    }
    if (editing && rule != null) {
        AlarmRuleDialog(rule, onDismiss = { editing = false }) { onChange(it); editing = false }
    }
}

private val BEFORE_CHOICES = listOf(5, 10, 15, 30, 60, 120)
private val END_CHOICES = listOf(5, 10, 15)

/** 알림 규칙 편집 (SPEC 5절: 사전 여러 개, 정시, 끝나기 전, 끝, 스타일, 소리, 진동, 끌 때까지, 다시 알림). */
@Composable
fun AlarmRuleDialog(initial: AlarmRule, onDismiss: () -> Unit, onSave: (AlarmRule) -> Unit) {
    var r by remember { mutableStateOf(initial) }
    val context = LocalContext.current

    fun has(type: TriggerType, m: Int = 0) = r.triggers.any { it.type == type && (it.minutes == m || (type == TriggerType.AT_START || type == TriggerType.AT_END)) }
    fun toggle(type: TriggerType, m: Int = 0) {
        val t = AlarmTrigger(type, m)
        r = r.copy(triggers = if (has(type, m)) r.triggers.filterNot { it.type == type && (it.minutes == m || type == TriggerType.AT_START || type == TriggerType.AT_END) } else (r.triggers + t))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            r = r.copy(sound = r.sound.copy(uri = uri?.toString()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AlarmRuleText.summary(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                Text("시작 전 (여러 개 가능)", style = MaterialTheme.typography.labelLarge)
                val customBefore = r.triggers.filter { it.type == TriggerType.BEFORE_START && it.minutes !in BEFORE_CHOICES }.map { it.minutes }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (BEFORE_CHOICES + customBefore).sorted().forEach { m ->
                        FilterChip(has(TriggerType.BEFORE_START, m), { toggle(TriggerType.BEFORE_START, m) }, label = { Text(if (m >= 60 && m % 60 == 0) "${m / 60}시간" else "${m}분") })
                    }
                }
                CustomMinutes { m -> if (!has(TriggerType.BEFORE_START, m)) toggle(TriggerType.BEFORE_START, m) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시작할 때", modifier = Modifier.weight(1f))
                    Switch(has(TriggerType.AT_START), { toggle(TriggerType.AT_START) })
                }
                Text("끝나기 전", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    END_CHOICES.forEach { m -> FilterChip(has(TriggerType.BEFORE_END, m), { toggle(TriggerType.BEFORE_END, m) }, label = { Text("${m}분") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("끝날 때", modifier = Modifier.weight(1f))
                    Switch(has(TriggerType.AT_END), { toggle(TriggerType.AT_END) })
                }

                HorizontalDivider()
                Text("방식", style = MaterialTheme.typography.labelLarge)
                listOf(
                    AlarmStyle.SILENT_NOTIFICATION to "무음 알림 (알림 목록에만)",
                    AlarmStyle.NOTIFICATION to "알림 (소리·진동)",
                    AlarmStyle.HEADS_UP to "팝업 (화면 위에 잠깐 뜸)",
                    AlarmStyle.FULL_SCREEN_ALARM to "알람 (전체 화면, 잠금 화면 위)",
                ).forEach { (style, label) ->
                    Row(Modifier.fillMaxWidth().selectable(r.style == style, onClick = { r = r.copy(style = style) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(r.style == style, { r = r.copy(style = style) })
                        Text(label)
                    }
                }

                if (r.style != AlarmStyle.SILENT_NOTIFICATION) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("소리", modifier = Modifier.weight(1f))
                        Switch(r.sound.enabled, { r = r.copy(sound = r.sound.copy(enabled = it)) })
                    }
                    if (r.sound.enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(r.sound.kind == SoundKind.NOTIFICATION, { r = r.copy(sound = r.sound.copy(kind = SoundKind.NOTIFICATION, uri = null)) }, label = { Text("알림음") })
                            FilterChip(r.sound.kind == SoundKind.ALARM, { r = r.copy(sound = r.sound.copy(kind = SoundKind.ALARM, uri = null)) }, label = { Text("알람음") })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.sound.uri == null) "기본 소리" else soundTitle(context, r.sound.uri!!), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                val type = if (r.sound.kind == SoundKind.ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                                picker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, r.sound.uri?.let(Uri::parse)),
                                )
                            }) { Text("소리 고르기") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("진동", modifier = Modifier.weight(1f))
                        Switch(r.vibration.enabled, { r = r.copy(vibration = r.vibration.copy(enabled = it)) })
                    }
                    if (r.vibration.enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(VibrationPattern.SHORT to "짧게", VibrationPattern.LONG to "길게", VibrationPattern.DOUBLE to "두 번").forEach { (p, l) ->
                                FilterChip(r.vibration.pattern == p, { r = r.copy(vibration = r.vibration.copy(pattern = p)) }, label = { Text(l) })
                            }
                        }
                    }
                }
                if (r.style == AlarmStyle.FULL_SCREEN_ALARM) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("끌 때까지 계속 울리기", modifier = Modifier.weight(1f))
                        Switch(r.repeatUntilDismissed, { r = r.copy(repeatUntilDismissed = it) })
                    }
                }
                Text("다시 알림", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15).forEach { m ->
                        FilterChip(r.snoozeMinutes == m, { r = r.copy(snoozeMinutes = m) }, label = { Text(if (m == 0) "없음" else "${m}분 뒤") })
                    }
                }
                Text("취소된 일정(휴가·공휴일·특이 일정으로 취소, 이번 주만 취소)은 알림이 울리지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(r.copy(triggers = r.triggers.distinct())) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 직접 분 입력: −5 [N분] +5 [추가] (5~180분) */
@Composable
private fun CustomMinutes(onAdd: (Int) -> Unit) {
    var m by remember { mutableIntStateOf(20) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("직접:", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { m = (m - 5).coerceAtLeast(5) }) { Text("−5") }
        Text("${m}분")
        TextButton(onClick = { m = (m + 5).coerceAtMost(180) }) { Text("+5") }
        OutlinedButton(onClick = { onAdd(m) }) { Text("추가") }
    }
}

private fun soundTitle(context: android.content.Context, uri: String): String =
    runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() ?: "고른 소리"

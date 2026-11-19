package io.github.rosymaro.familyweekly.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.alarm.AlarmPermissions
import io.github.rosymaro.familyweekly.alarm.planner.AlarmRuleText
import io.github.rosymaro.familyweekly.alarm.planner.PlannedAlarm
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.QuietHours
import io.github.rosymaro.familyweekly.domain.Settings
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.TimeStepper
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsViewModel(app: FamilyWeeklyApp) : StoreViewModel(app) {

    private val _permissions = MutableStateFlow(app.alarms.permissions())
    val permissions: StateFlow<AlarmPermissions> = _permissions.asStateFlow()
    private val _next = MutableStateFlow<List<PlannedAlarm>>(emptyList())
    val next: StateFlow<List<PlannedAlarm>> = _next.asStateFlow()

    fun refresh() {
        _permissions.value = app.alarms.permissions()
        app.appScope.launch {
            val list = app.alarms.nextAlarms()
            withContext(Dispatchers.Main) { _next.value = list }
        }
    }

    fun edit(transform: (Settings) -> Settings) {
        update { it.copy(settings = transform(it.settings)) }
        app.appScope.launch {
            kotlinx.coroutines.delay(600) // 저장·재등록 뒤에 목록 새로
            val list = app.alarms.nextAlarms()
            withContext(Dispatchers.Main) { _next.value = list }
        }
    }

    /** 지금 바로 기본 규칙으로 알림 하나 (소리·진동·팝업 확인용). */
    fun sendTest() {
        val s = data.value.settings
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val rule = s.defaultAlarm
        app.notifier.show(
            PlannedAlarm(
                key = "TEST|" + now,
                at = now.toString(),
                source = io.github.rosymaro.familyweekly.alarm.planner.AlarmSource.BLOCK,
                sourceId = "test",
                scheduleId = data.value.schedules.firstOrNull()?.id ?: "",
                trigger = io.github.rosymaro.familyweekly.domain.TriggerType.BEFORE_START,
                minutes = 10,
                title = "테스트 · 피아노 10분 전",
                text = "기본 알림: ${AlarmRuleText.summary(rule)}",
                style = rule.style,
                sound = rule.sound,
                vibration = rule.vibration,
                repeatUntilDismissed = rule.style == AlarmStyle.FULL_SCREEN_ALARM && rule.repeatUntilDismissed,
                snoozeMinutes = rule.snoozeMinutes,
            ),
        )
    }

    companion object {
        val Factory = appViewModelFactory { app, _ -> SettingsViewModel(app) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onManageHolidays: () -> Unit, onOpenDesign: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val s = data.settings
    val context = LocalContext.current
    var editDefault by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf(false) }

    // 설정 앱에서 권한을 바꾸고 돌아오면 다시 확인
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("화면", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DarkMode.SYSTEM to "폰 설정 따라", DarkMode.LIGHT to "라이트", DarkMode.DARK to "다크").forEach { (m, l) ->
                    FilterChip(s.darkMode == m, { vm.edit { it.copy(darkMode = m) } }, label = { Text(l) })
                }
            }
            Text("위젯 다크 모드는 위젯마다 따로 정해요 (위젯 길게 눌러 설정).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpenDesign) { Text("디자인 (색·글꼴·포스트잇 모양 꾸미기)") }

            HorizontalDivider()
            Text("알림", style = MaterialTheme.typography.titleSmall)

            PermissionCard(perms, s.alarmsEnabled,
                onAllowNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openAppNotificationSettings(context)
                },
                onOpenAppNotifications = { openAppNotificationSettings(context) },
                onOpenExact = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) }
                    }
                },
                onOpenFullScreen = {
                    if (Build.VERSION.SDK_INT >= 34) {
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))) }
                    }
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("모든 알림", modifier = Modifier.weight(1f))
                Switch(s.alarmsEnabled, { on ->
                    vm.edit { it.copy(alarmsEnabled = on) }
                    if (on && !perms.notifications && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            Text("아이마다 알림 켜기/끄기는 홈 카드 메뉴, 아이별 기본값은 시간표 ⚙에서 정해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            RuleRow("반복 일정 기본 알림", AlarmRuleText.summary(s.defaultAlarm)) { editDefault = true }
            RuleRow("특이 일정 기본 알림", AlarmRuleText.summary(s.eventDefaultAlarm)) { editEvent = true }
            TimeStepper("하루 종일 특이 일정 알림 기준 시각", s.allDayAlarmMin, { v -> vm.edit { it.copy(allDayAlarmMin = v) } }, max = 23 * 60 + 55)

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("방해 금지 시간")
                    Text("이 시간엔 소리·진동 없이 조용히 알려요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(s.quietHours != null, { on -> vm.edit { it.copy(quietHours = if (on) QuietHours(22 * 60, 7 * 60) else null) } })
            }
            s.quietHours?.let { q ->
                TimeStepper("시작", q.startMin, { v -> vm.edit { it.copy(quietHours = q.copy(startMin = v)) } }, max = 23 * 60 + 55)
                TimeStepper("끝", q.endMin, { v -> vm.edit { it.copy(quietHours = q.copy(endMin = v)) } }, max = 23 * 60 + 55)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("알람(전체 화면)은 방해 금지 시간에도 울리기", modifier = Modifier.weight(1f))
                    Switch(q.allowFullScreenAlarm, { on -> vm.edit { it.copy(quietHours = q.copy(allowFullScreenAlarm = on)) } })
                }
            }

            HorizontalDivider()
            var format by remember(s.notificationFormat) { mutableStateOf(s.notificationFormat) }
            OutlinedTextField(
                format, { format = it },
                label = { Text("알림 문구 (사전 알림)") },
                supportingText = { Text("{아이} {일정} {N}(분) {장소} — 장소가 없으면 ({장소})는 빠져요") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (format != s.notificationFormat && format.isNotBlank()) {
                TextButton(onClick = { vm.edit { it.copy(notificationFormat = format.trim()) } }) { Text("문구 저장") }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("앞으로 48시간 알림", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::sendTest) { Text("테스트 알림") }
            }
            if (next.isEmpty()) Text("예정된 알림이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            next.forEach { a -> NextAlarmRow(a) }

            HorizontalDivider()
            TextButton(onClick = onManageHolidays) { Text("공휴일 관리 (자동 받기)") }
        }
    }

    if (editDefault) AlarmRuleDialog(s.defaultAlarm, { editDefault = false }) { r -> vm.edit { it.copy(defaultAlarm = r) }; editDefault = false }
    if (editEvent) AlarmRuleDialog(s.eventDefaultAlarm, { editEvent = false }) { r -> vm.edit { it.copy(eventDefaultAlarm = r) }; editEvent = false }
}

@Composable
private fun RuleRow(title: String, summary: String, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onEdit) { Text("편집") }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("E HH:mm", Locale.KOREAN)

@Composable
private fun NextAlarmRow(a: PlannedAlarm) {
    val t = a.atTime
    val day = when (t.toLocalDate()) {
        LocalDate.now() -> "오늘 " + t.toLocalTime().toString()
        LocalDate.now().plusDays(1) -> "내일 " + t.toLocalTime().toString()
        else -> t.format(timeFmt)
    }
    Column {
        Text("$day  ${a.title}", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildString {
                append(
                    when (a.style) {
                        AlarmStyle.SILENT_NOTIFICATION -> "무음 알림"
                        AlarmStyle.NOTIFICATION -> "알림"
                        AlarmStyle.HEADS_UP -> "팝업"
                        AlarmStyle.FULL_SCREEN_ALARM -> "알람"
                    },
                )
                if (a.quieted) append(" (방해 금지 시간이라 조용히)")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 권한이 하나라도 막혀 있으면 안내. */
@Composable
private fun PermissionCard(
    p: AlarmPermissions,
    alarmsOn: Boolean,
    onAllowNotifications: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onOpenExact: () -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    if (!alarmsOn || (p.notifications && p.exactAlarms && p.fullScreen)) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!p.notifications) {
                Text("알림이 허용되지 않아 알림이 보이지 않아요.", color = MaterialTheme.colorScheme.onErrorContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAllowNotifications) { Text("알림 허용") }
                    TextButton(onClick = onOpenAppNotifications) { Text("앱 알림 설정") }
                }
            }
            if (!p.exactAlarms) {
                Text("'알람 및 리마인더' 권한이 없어 알림이 1분쯤 늦을 수 있어요.", color = MaterialTheme.colorScheme.onErrorContainer)
                OutlinedButton(onClick = onOpenExact) { Text("권한 설정 열기") }
            }
            if (!p.fullScreen) {
                Text("전체 화면 알림이 허용되지 않아 '알람'은 팝업으로 대신 떠요.", color = MaterialTheme.colorScheme.onErrorContainer)
                OutlinedButton(onClick = onOpenFullScreen) { Text("전체 화면 알림 허용") }
            }
            Text("그래도 알림이 늦으면 폰 설정 → 배터리에서 이 앱을 '제한 없음'으로 두면 좋아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private fun openAppNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
    }
}

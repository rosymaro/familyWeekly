package io.github.rosymaro.familyweekly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.MainActivity
import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.theme.ThemePresets
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.common.WeekPreviewImage
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 위젯 추가·재구성: 스케줄 → 디자인(스케줄 테마 그대로 / 다른 프리셋) → 다크 모드 → 미리보기 → 완료.
 * "이 위젯만 꾸미기"(토큰 하나하나 바꾸기)는 Phase 7 디자인 편집기에서.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 완료 전에 나가면 런처가 위젯을 추가하지 않음
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as FamilyWeeklyApp
        setContent {
            val data by app.store.data.collectAsStateWithLifecycle()
            FamilyWeeklyTheme(darkTheme = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())) {
                ConfigScreen(
                    app = app,
                    existing = WidgetLogic.binding(data, appWidgetId),
                    onDone = ::finishWith,
                    onCancel = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    private fun finishWith(binding: WidgetBinding) {
        val app = application as FamilyWeeklyApp
        app.appScope.launch {
            app.store.update { WidgetLogic.bind(it, binding) }
            app.widgets.update(appWidgetId)
            app.widgets.scheduleTicks()
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConfigScreen(
        app: FamilyWeeklyApp,
        existing: WidgetBinding?,
        onDone: (WidgetBinding) -> Unit,
        onCancel: () -> Unit,
        onOpenApp: () -> Unit,
    ) {
        val data by app.store.data.collectAsStateWithLifecycle()
        var scheduleId by rememberSaveable { mutableStateOf(existing?.scheduleId ?: data.schedules.firstOrNull()?.id) }
        /** null = 스케줄 테마 그대로 */
        var themeId by rememberSaveable { mutableStateOf(existing?.themeId) }
        var darkMode by rememberSaveable { mutableStateOf(existing?.darkMode ?: DarkMode.SYSTEM) }
        val now = remember { LocalDateTime.now() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (existing == null) "위젯 추가" else "위젯 설정") },
                    navigationIcon = { TextButton(onClick = onCancel) { Text("취소") } },
                )
            },
        ) { padding ->
            if (data.schedules.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("아직 스케줄이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text("앱에서 아이 스케줄을 먼저 만든 뒤 위젯을 추가해 주세요.")
                    Button(onClick = onOpenApp) { Text("앱 열기") }
                }
                return@Scaffold
            }
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("스케줄", style = MaterialTheme.typography.labelLarge)
                data.schedules.forEach { s ->
                    Row(Modifier.fillMaxWidth().selectable(selected = scheduleId == s.id, onClick = { scheduleId = s.id }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = scheduleId == s.id, onClick = { scheduleId = s.id })
                        Text(listOfNotNull(s.emoji, s.name).joinToString(" "))
                        val n = WidgetLogic.countFor(data, s.id) - if (existing?.scheduleId == s.id) 1 else 0
                        if (n > 0) Text("  · 다른 위젯 ${n}개에서 보는 중", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text("디자인", style = MaterialTheme.typography.labelLarge)
                val scheduleTheme = data.schedule(scheduleId ?: "")?.themeId ?: ThemePresets.DEFAULT_ID
                Row(Modifier.fillMaxWidth().selectable(selected = themeId == null, onClick = { themeId = null }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeId == null, onClick = { themeId = null })
                    Text("스케줄 디자인 그대로 (${app.presets.name(scheduleTheme)})")
                }
                ThemePresets.IDS.forEach { id ->
                    Row(Modifier.fillMaxWidth().selectable(selected = themeId == id, onClick = { themeId = id }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeId == id, onClick = { themeId = id })
                        Text("이 위젯만: ${app.presets.name(id)}")
                    }
                }
                Text("세부 꾸미기(색·글꼴·포스트잇 모양 등)는 다음 업데이트의 디자인 편집기에서 할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("다크 모드", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DarkMode.SYSTEM to "폰 설정 따라", DarkMode.LIGHT to "라이트", DarkMode.DARK to "다크").forEach { (m, label) ->
                        FilterChip(darkMode == m, { darkMode = m }, label = { Text(label) })
                    }
                }

                Text("미리보기", style = MaterialTheme.typography.labelLarge)
                val sid = scheduleId
                if (sid != null) {
                    val week = remember(data, sid) {
                        OccurrenceResolver.resolve(data, sid, WeekRange.of(now.toLocalDate()), app.holidays.calendar(data.customHolidays), now)
                    }
                    val dark = WidgetLogic.isDark(darkMode, isSystemInDarkTheme())
                    // 미리보기는 위젯 테마로 그리도록 스케줄의 themeId를 바꿔 넘긴다
                    week?.let { w ->
                        val shown = w.copy(schedule = w.schedule.copy(themeId = themeId ?: w.schedule.themeId))
                        WeekPreviewImage(shown, app.presets, dark, now, height = 260.dp, contentDescription = "위젯 미리보기")
                    }
                }

                Button(
                    enabled = sid != null,
                    onClick = { onDone(WidgetBinding(appWidgetId, sid, themeId = themeId, darkMode = darkMode)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("완료") }
            }
        }
    }
}

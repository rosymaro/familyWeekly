package io.github.rosymaro.familyweekly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import kotlinx.serialization.json.JsonObject
import io.github.rosymaro.familyweekly.ui.design.DesignEditorBody
import io.github.rosymaro.familyweekly.theme.TokenSchema
import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
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
                    existing = remember { WidgetLogic.binding(app.store.data.value, appWidgetId) },
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

    /** "완료": 스케줄·디자인·다크 모드·따로 꾸민 값을 한 번에 저장. */
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
        // 디자인 꾸미기에서 바꾸는 값 (완료 전까지는 저장하지 않음)
        val initialDesign = WidgetDesign.of(existing)
        var designJson by rememberSaveable { mutableStateOf(initialDesign.encode()) }
        val design = WidgetDesign.decode(designJson)
        fun setDesign(d: WidgetDesign) { designJson = d.encode() }
        var editing by rememberSaveable { mutableStateOf(false) }
        val now = remember { LocalDateTime.now() }
        val schedule = scheduleId?.let { data.schedule(it) }

        if (editing) {
            DesignStep(app, data, scheduleId, design, onChange = ::setDesign, onBack = { editing = false })
            return
        }

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

                // 디자인·다크 모드는 "디자인 꾸미기" 안에서
                Text("디자인", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("디자인 꾸미기") }
                    OutlinedButton(enabled = design != initialDesign, onClick = { setDesign(initialDesign) }) { Text("되돌리기") }
                }
                Text(
                    design.summary(app.themes.name(data, design.themeId ?: schedule?.themeId), usesScheduleTheme = design.themeId == null),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (design != initialDesign) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("미리보기", style = MaterialTheme.typography.labelLarge)
                val sid = scheduleId
                if (sid != null) {
                    val week = remember(data, sid) {
                        OccurrenceResolver.resolve(data, sid, WeekRange.of(now.toLocalDate()), app.holidays.calendar(data.customHolidays), now)
                    }
                    val dark = WidgetLogic.isDark(design.darkMode, isSystemInDarkTheme())
                    week?.let { w ->
                        WeekPreviewImage(w, app.themes, data, dark, now, height = 260.dp, contentDescription = "위젯 미리보기", binding = design.toBinding(appWidgetId, sid))
                    }
                }

                Button(
                    enabled = sid != null,
                    onClick = { onDone(design.toBinding(appWidgetId, sid)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("완료") }
                Text("완료를 눌러야 저장돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    /**
     * 위젯 설정 안의 디자인 꾸미기: 위쪽에 디자인 고르기·다크 모드, 아래는 디자인 편집기 본문.
     * 저장·되돌리기 버튼 없음 — 뒤로(취소) 가면 바꾼 내용을 들고 위젯 설정으로 돌아가고, 거기서 "완료"를 눌러야 저장.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DesignStep(
        app: FamilyWeeklyApp,
        data: io.github.rosymaro.familyweekly.domain.AppData,
        scheduleId: String?,
        design: WidgetDesign,
        onChange: (WidgetDesign) -> Unit,
        onBack: () -> Unit,
    ) {
        BackHandler(onBack = onBack)
        val schedule = scheduleId?.let { data.schedule(it) }
        val base = app.themes.themeLayers(data, design.themeId ?: schedule?.themeId) + schedule?.themeOverride
        val systemDark = isSystemInDarkTheme()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("디자인 꾸미기")
                            Text("이 위젯 · 뒤로 가서 완료를 눌러야 저장돼요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { TextButton(onClick = onBack) { Text("취소") } },
                )
            },
        ) { padding ->
            DesignEditorBody(
                data = data,
                baseLayers = base,
                draft = design.override,
                previewScheduleId = scheduleId,
                holidays = app.holidays.calendar(data.customHolidays),
                onSet = { path, v -> onChange(design.copy(override = TokenSchema.set(design.override, path, v))) },
                onReset = { path -> onChange(design.copy(override = TokenSchema.remove(design.override, path))) },
                onResetGroup = { g -> onChange(design.copy(override = JsonObject(design.override - g))) },
                hint = "미리보기를 누르면 그 부분을 꾸미는 곳으로 가요. 굵은 파란 글씨 = 이 위젯만 바꾼 값",
                initialDark = WidgetLogic.isDark(design.darkMode, systemDark),
                modifier = Modifier.fillMaxSize().padding(padding),
                header = {
                    Text("디자인", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().selectable(selected = design.themeId == null, onClick = { onChange(design.copy(themeId = null)) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = design.themeId == null, onClick = { onChange(design.copy(themeId = null)) })
                        Text("스케줄 디자인 그대로 (${app.themes.name(data, schedule?.themeId)})")
                    }
                    app.themes.entries(data).forEach { entry ->
                        Row(Modifier.fillMaxWidth().selectable(selected = design.themeId == entry.id, onClick = { onChange(design.copy(themeId = entry.id)) }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = design.themeId == entry.id, onClick = { onChange(design.copy(themeId = entry.id)) })
                            Text(entry.name)
                        }
                    }
                    Text("다크 모드", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(DarkMode.SYSTEM to "폰 설정 따라", DarkMode.LIGHT to "라이트", DarkMode.DARK to "다크").forEach { (m, label) ->
                            FilterChip(design.darkMode == m, { onChange(design.copy(darkMode = m)) }, label = { Text(label) })
                        }
                    }
                    HorizontalDivider()
                },
            )
        }
    }
}

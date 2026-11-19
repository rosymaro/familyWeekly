package io.github.rosymaro.familyweekly

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.navigation.AppNavHost
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme

class MainActivity : ComponentActivity() {

    /** 위젯을 눌러 들어오면 열 스케줄 (한 번 열면 비움). */
    private val openSchedule = mutableStateOf<String?>(null)
    /** 위젯 설정에서 "이 위젯만 꾸미기"로 들어오면 그 위젯 id */
    private val editWidget = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 화면 회전 등으로 다시 만들어질 때는 다시 열지 않는다
        if (savedInstanceState == null) {
            openSchedule.value = intent.scheduleIdExtra()
            editWidget.value = intent.editWidgetExtra()
        }
        val app = application as FamilyWeeklyApp
        setContent {
            val data by app.store.data.collectAsStateWithLifecycle()
            val pending by openSchedule
            val pendingWidget by editWidget
            FamilyWeeklyTheme(darkTheme = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())) {
                AppNavHost(
                    app,
                    openScheduleId = pending,
                    onOpenedSchedule = { openSchedule.value = null },
                    editWidgetId = pendingWidget,
                    onOpenedWidgetEditor = { editWidget.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.scheduleIdExtra()?.let { openSchedule.value = it }
        intent.editWidgetExtra()?.let { editWidget.value = it }
    }

    private fun Intent?.scheduleIdExtra(): String? = this?.getStringExtra(EXTRA_SCHEDULE_ID)
    private fun Intent?.editWidgetExtra(): Int? = this?.getIntExtra(EXTRA_EDIT_WIDGET, -1)?.takeIf { it >= 0 }

    companion object {
        const val EXTRA_SCHEDULE_ID = "io.github.rosymaro.familyweekly.SCHEDULE_ID"
        const val EXTRA_EDIT_WIDGET = "io.github.rosymaro.familyweekly.EDIT_WIDGET"
    }
}

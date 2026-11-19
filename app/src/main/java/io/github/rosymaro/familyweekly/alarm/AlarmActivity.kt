package io.github.rosymaro.familyweekly.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.alarm.planner.PlannedAlarm
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme
import kotlinx.coroutines.launch

/** 전체 화면 알람 (잠금 화면 위). 끄기 / N분 뒤 다시. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val app = application as FamilyWeeklyApp
        val alarm = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM)?.let {
            runCatching { AlarmScheduler.json.decodeFromString(PlannedAlarm.serializer(), it) }.getOrNull()
        }
        if (alarm == null) {
            finish()
            return
        }
        setContent {
            FamilyWeeklyTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("⏰", style = MaterialTheme.typography.displayLarge)
                        Text(alarm.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(alarm.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Column(Modifier.padding(top = 48.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                app.notifier.cancel(alarm.key)
                                finish()
                            }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("끄기") }
                            if (alarm.snoozeMinutes > 0) {
                                OutlinedButton(onClick = {
                                    app.notifier.cancel(alarm.key)
                                    app.appScope.launch { app.alarms.snooze(alarm) }
                                    finish()
                                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("${alarm.snoozeMinutes}분 뒤 다시") }
                            }
                        }
                    }
                }
            }
        }
    }
}

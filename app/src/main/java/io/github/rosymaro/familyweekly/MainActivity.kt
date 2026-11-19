package io.github.rosymaro.familyweekly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosymaro.familyweekly.ui.design.DesignPreviewScreen
import io.github.rosymaro.familyweekly.ui.home.HomeScreen
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FamilyWeeklyApp
        val store = app.store
        setContent {
            FamilyWeeklyTheme {
                val loadIssue by store.loadIssue.collectAsStateWithLifecycle()
                // 화면 이동은 Phase 3에서 Navigation Compose로 바꾼다
                var showDesignPreview by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showDesignPreview) { showDesignPreview = false }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showDesignPreview) {
                        DesignPreviewScreen(
                            presets = app.presets,
                            onBack = { showDesignPreview = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        HomeScreen(
                            loadIssue = loadIssue,
                            onDismissIssue = store::clearLoadIssue,
                            onOpenDesignPreview = { showDesignPreview = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

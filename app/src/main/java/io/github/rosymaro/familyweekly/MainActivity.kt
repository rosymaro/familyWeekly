package io.github.rosymaro.familyweekly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.navigation.AppNavHost
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FamilyWeeklyApp
        setContent {
            val data by app.store.data.collectAsStateWithLifecycle()
            FamilyWeeklyTheme(darkTheme = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())) {
                AppNavHost(app)
            }
        }
    }
}

package io.github.rosymaro.familyweekly.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.ui.design.DesignPreviewScreen
import io.github.rosymaro.familyweekly.ui.home.HomeScreen
import io.github.rosymaro.familyweekly.ui.week.WeekScreen

@Composable
fun AppNavHost(app: FamilyWeeklyApp) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSchedule = { nav.navigate(WeekRoute(it)) },
                onOpenDesignPreview = { nav.navigate(DesignPreviewRoute) },
            )
        }
        composable<WeekRoute> {
            WeekScreen(onBack = { nav.popBackStack() })
        }
        composable<DesignPreviewRoute> {
            Scaffold { padding ->
                DesignPreviewScreen(app.presets, onBack = { nav.popBackStack() }, modifier = Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}

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
import io.github.rosymaro.familyweekly.ui.calendar.CalendarScreen
import io.github.rosymaro.familyweekly.ui.calendar.HolidaysScreen
import io.github.rosymaro.familyweekly.ui.design.DesignPreviewScreen
import io.github.rosymaro.familyweekly.ui.event.EventEditScreen
import io.github.rosymaro.familyweekly.ui.event.VacationEditScreen
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
                onOpenCalendar = { nav.navigate(CalendarRoute) },
            )
        }
        composable<WeekRoute> {
            WeekScreen(
                onBack = { nav.popBackStack() },
                onOpenEvent = { nav.navigate(EventEditRoute(eventId = it)) },
            )
        }
        composable<CalendarRoute> {
            CalendarScreen(
                onBack = { nav.popBackStack() },
                onAddEvent = { nav.navigate(EventEditRoute(date = it.toString())) },
                onEditEvent = { nav.navigate(EventEditRoute(eventId = it)) },
                onAddVacation = { nav.navigate(VacationEditRoute(date = it.toString())) },
                onEditVacation = { nav.navigate(VacationEditRoute(vacationId = it)) },
                onManageHolidays = { nav.navigate(HolidaysRoute) },
            )
        }
        composable<EventEditRoute> { EventEditScreen(onDone = { nav.popBackStack() }) }
        composable<VacationEditRoute> { VacationEditScreen(onDone = { nav.popBackStack() }) }
        composable<HolidaysRoute> { HolidaysScreen(onBack = { nav.popBackStack() }) }
        composable<DesignPreviewRoute> {
            Scaffold { padding ->
                DesignPreviewScreen(app.presets, onBack = { nav.popBackStack() }, modifier = Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}

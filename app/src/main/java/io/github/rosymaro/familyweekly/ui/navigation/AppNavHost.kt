package io.github.rosymaro.familyweekly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.ui.calendar.CalendarScreen
import io.github.rosymaro.familyweekly.ui.calendar.HolidaysScreen
import io.github.rosymaro.familyweekly.ui.design.DesignEditorScreen
import io.github.rosymaro.familyweekly.ui.design.DesignLibraryScreen
import io.github.rosymaro.familyweekly.ui.event.EventEditScreen
import io.github.rosymaro.familyweekly.ui.event.VacationEditScreen
import io.github.rosymaro.familyweekly.ui.home.HomeScreen
import io.github.rosymaro.familyweekly.ui.settings.SettingsScreen
import io.github.rosymaro.familyweekly.ui.week.WeekScreen

@Composable
fun AppNavHost(
    app: FamilyWeeklyApp,
    openScheduleId: String? = null,
    onOpenedSchedule: () -> Unit = {},
    editWidgetId: Int? = null,
    onOpenedWidgetEditor: () -> Unit = {},
) {
    val nav = rememberNavController()
    // 위젯 설정에서 "이 위젯만 꾸미기"로 들어오면 편집기로
    LaunchedEffect(editWidgetId) {
        val id = editWidgetId ?: return@LaunchedEffect
        nav.navigate(DesignEditRoute(DesignEditRoute.WIDGET, id.toString())) { launchSingleTop = true }
        onOpenedWidgetEditor()
    }
    // 위젯을 눌러 들어오면 그 스케줄 주간 화면으로 (홈 위에 쌓아서 뒤로 가기는 홈)
    LaunchedEffect(openScheduleId) {
        val id = openScheduleId ?: return@LaunchedEffect
        if (app.store.data.value.schedule(id) != null) {
            nav.navigate(WeekRoute(id)) {
                popUpTo<HomeRoute>()
                launchSingleTop = true
            }
        }
        onOpenedSchedule()
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSchedule = { nav.navigate(WeekRoute(it)) },
                onOpenDesign = { nav.navigate(DesignLibraryRoute) },
                onCustomizeSchedule = { nav.navigate(DesignEditRoute(DesignEditRoute.SCHEDULE, it)) },
                onOpenCalendar = { nav.navigate(CalendarRoute) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
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
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onManageHolidays = { nav.navigate(HolidaysRoute) },
                onOpenDesign = { nav.navigate(DesignLibraryRoute) },
            )
        }
        composable<DesignLibraryRoute> {
            DesignLibraryScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(DesignEditRoute.of(it)) })
        }
        composable<DesignEditRoute> { DesignEditorScreen(onBack = { nav.popBackStack() }) }
    }
}

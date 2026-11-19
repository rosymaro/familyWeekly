package io.github.rosymaro.familyweekly.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

/** 스케줄 하나의 주간 편집 화면. */
@Serializable
data class WeekRoute(val scheduleId: String)

/** 디자인 목록 (프리셋·내 디자인·아이별·위젯별). */
@Serializable
object DesignLibraryRoute

/** 디자인 편집기. kind = theme(id=디자인 id) / schedule(id=스케줄 id) / widget(id=위젯 id) */
@Serializable
data class DesignEditRoute(val kind: String, val id: String) {
    companion object {
        const val THEME = "theme"
        const val SCHEDULE = "schedule"
        const val WIDGET = "widget"

        fun of(t: io.github.rosymaro.familyweekly.theme.DesignTarget) = when (t) {
            is io.github.rosymaro.familyweekly.theme.DesignTarget.Theme -> DesignEditRoute(THEME, t.themeId)
            is io.github.rosymaro.familyweekly.theme.DesignTarget.ScheduleOnly -> DesignEditRoute(SCHEDULE, t.scheduleId)
            is io.github.rosymaro.familyweekly.theme.DesignTarget.WidgetOnly -> DesignEditRoute(WIDGET, t.appWidgetId.toString())
        }
    }
}

/** 월 달력: 특이 일정·휴가·공휴일. */
@Serializable
object CalendarRoute

/** 특이 일정 추가/수정. eventId가 null이면 새로 (date: ISO 날짜, scheduleId: 미리 고를 스케줄). */
@Serializable
data class EventEditRoute(val eventId: String? = null, val date: String? = null, val scheduleId: String? = null)

/** 휴가 추가/수정. */
@Serializable
data class VacationEditRoute(val vacationId: String? = null, val date: String? = null)

/** 설정: 화면, 알림 전역 설정, 권한 안내, 다음 알림. */
@Serializable
object SettingsRoute

/** 공휴일 관리. */
@Serializable
object HolidaysRoute

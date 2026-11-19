package io.github.rosymaro.familyweekly.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

/** 스케줄 하나의 주간 편집 화면. */
@Serializable
data class WeekRoute(val scheduleId: String)

/** 개발용 디자인 미리보기 (Phase 7 편집기로 바뀜). */
@Serializable
object DesignPreviewRoute

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

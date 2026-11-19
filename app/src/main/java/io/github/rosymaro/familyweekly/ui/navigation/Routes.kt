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

package io.github.rosymaro.familyweekly.ui.week

import io.github.rosymaro.familyweekly.domain.ResolvedWeek
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import kotlinx.serialization.json.JsonObject

/**
 * 주간 편집 화면 전용 설정. 위젯 디자인이 아니라 "편집하기 좋은 크기"를 정하는 값이다.
 * 스케줄 테마 위에 이 덮어쓰기만 얹으므로 색·모양은 위젯과 같다.
 */
object EditorTheme {

    /** 1시간 높이 (손가락으로 5분 단위를 맞추기 좋게) */
    const val HOUR_HEIGHT_DP = 64f

    /** 편집 화면은 위젯 표시 범위보다 넓게 보여 준다 (범위 밖에 새 블록을 넣을 수 있게). */
    const val MIN_START_MIN = 7 * 60
    const val MIN_END_MIN = 22 * 60

    val override: JsonObject = ThemeResolver.parse(
        """
        {
          "canvas": { "cornerRadius": 0, "matchSystem": false, "padding": { "start": 4, "top": 4, "end": 8, "bottom": 12 } },
          "header": { "visible": false },
          "dayHeader": { "height": 34 },
          "timeAxis": { "width": 30, "showEveryNHours": 1 },
          "grid": { "hourHeight": $HOUR_HEIGHT_DP, "halfHourLine": { "visible": true } },
          "block": { "showTime": "IF_FITS", "showPlace": "IF_FITS", "titleMaxLines": 3 },
          "postIt": { "showTime": "IF_FITS" }
        }
        """.trimIndent(),
    )

    fun widenRange(week: ResolvedWeek): ResolvedWeek = week.copy(
        displayStartMin = minOf(week.displayStartMin, MIN_START_MIN),
        displayEndMin = maxOf(week.displayEndMin, MIN_END_MIN),
    )
}

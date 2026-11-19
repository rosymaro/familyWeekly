package io.github.rosymaro.familyweekly.domain

import java.time.LocalTime

/** 그리드 이동·편집 단위(5분)와 분 ↔ 시각 변환. */
object TimeStep {
    const val STEP_MIN = 5
    const val MINUTES_PER_DAY = 24 * 60

    /** 가장 가까운 5분으로 맞추고 0..1440 안으로. (0~2분은 내림, 3~4분은 올림) */
    fun snap(minutes: Int): Int {
        val snapped = Math.floorDiv(minutes + STEP_MIN / 2, STEP_MIN) * STEP_MIN
        return snapped.coerceIn(0, MINUTES_PER_DAY)
    }

    /** 소수 분(드래그 좌표 등)을 5분으로. */
    fun snap(minutes: Double): Int = snap(Math.round(minutes).toInt())

    fun isOnStep(minutes: Int): Boolean = minutes % STEP_MIN == 0

    /** 정시로 내림. 615(10:15) → 600 */
    fun floorToHour(minutes: Int): Int = Math.floorDiv(minutes, 60) * 60

    /** 정시로 올림. 615 → 660, 600 → 600 */
    fun ceilToHour(minutes: Int): Int = Math.floorDiv(minutes + 59, 60) * 60

    /** 1440(24:00)은 LocalTime.MAX 대신 자정으로 표현할 수 없으므로 23:59로. */
    fun toLocalTime(minutes: Int): LocalTime =
        if (minutes >= MINUTES_PER_DAY) LocalTime.of(23, 59) else LocalTime.of(minutes / 60, minutes % 60)

    fun fromLocalTime(time: LocalTime): Int = time.hour * 60 + time.minute

    /** "9:05", 1440 → "24:00" */
    fun format(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
}

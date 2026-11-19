package io.github.rosymaro.familyweekly.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** 테스트용 데이터. 기준 주: 2026-11-16(월) ~ 2026-11-22(일). */
object TestData {
    val MONDAY: LocalDate = LocalDate.of(2026, 11, 16)
    val WEEK = WeekRange(MONDAY)

    fun day(dow: DayOfWeek): LocalDate = WEEK.dateOf(dow)

    /** "id-1", "id-2" ... 순서대로 나오는 id 생성기. */
    fun ids(prefix: String = "id"): IdGenerator {
        var n = 0
        return IdGenerator { "$prefix-${++n}" }
    }

    fun block(
        id: String,
        day: DayOfWeek,
        start: String,
        end: String,
        title: String = id,
        groupId: String? = null,
    ) = Block(id = id, title = title, day = day, startMin = min(start), endMin = min(end), groupId = groupId)

    /** "9:30" → 570 */
    fun min(hm: String): Int {
        val (h, m) = hm.split(":").map { it.toInt() }
        return h * 60 + m
    }

    /** 하린: 월~금. 월 피아노 15:00~16:00, 월 수학 16:30~17:30, 수 태권도 17:00~18:00. */
    val harin = Schedule(
        id = "harin",
        name = "하린",
        days = DaysMode.MON_FRI,
        blocks = listOf(
            block("piano", DayOfWeek.MONDAY, "15:00", "16:00", "피아노"),
            block("math", DayOfWeek.MONDAY, "16:30", "17:30", "수학"),
            block("tkd", DayOfWeek.WEDNESDAY, "17:00", "18:00", "태권도"),
        ),
    )

    /** 도윤: 월~일. 월 수영 15:30~16:30, 토 축구 10:00~11:00. */
    val doyun = Schedule(
        id = "doyun",
        name = "도윤",
        days = DaysMode.MON_SUN,
        blocks = listOf(
            block("swim", DayOfWeek.MONDAY, "15:30", "16:30", "수영"),
            block("soccer", DayOfWeek.SATURDAY, "10:00", "11:00", "축구"),
        ),
    )

    /** 알림 계산 테스트를 위해 앱 전체 알림은 켜 둔다 (앱 기본값은 꺼짐). */
    val data = AppData(schedules = listOf(harin, doyun), settings = Settings(alarmsEnabled = true))
}

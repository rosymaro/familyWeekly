package io.github.rosymaro.familyweekly.render

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.Block
import io.github.rosymaro.familyweekly.domain.DaysMode
import io.github.rosymaro.familyweekly.domain.Holiday
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import io.github.rosymaro.familyweekly.domain.HolidayPolicy
import io.github.rosymaro.familyweekly.domain.ManualCancellation
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.OverlapAction
import io.github.rosymaro.familyweekly.domain.ResolvedWeek
import io.github.rosymaro.familyweekly.domain.Schedule
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.Vacation
import io.github.rosymaro.familyweekly.domain.WeekRange
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 디자인 미리보기용 고정 샘플 주 (DESIGN.md 5절·6절).
 * 한 주에 활성 / 짧은 블록 / 긴 제목 / 겹친 블록 / 휴가 취소 / 개별 취소 / 공휴일 /
 * 특이 일정 KEEP·CANCEL·하루 종일·같은 시간 2개 / 오늘 / 현재 시간선이 모두 나온다.
 */
object SampleData {

    const val SCHEDULE_ID = "sample"

    /** 오늘 = 샘플 주 수요일 16:20 (월~금, 월~일 모두에서 보이게). */
    fun now(week: WeekRange): LocalDateTime = week.dateOf(WEDNESDAY).atTime(16, 20)

    fun week(today: LocalDate = LocalDate.now()): WeekRange = WeekRange.of(today)

    fun data(week: WeekRange, days: DaysMode): AppData {
        fun b(id: String, day: DayOfWeek, s: String, e: String, title: String, color: String, place: String? = null) =
            Block(id, title, day, m(s), m(e), colorKey = color, place = place)

        val schedule = Schedule(
            id = SCHEDULE_ID,
            name = "하린",
            emoji = "🎹",
            days = days,
            holidayPolicy = HolidayPolicy.CANCEL_BLOCKS,
            blocks = listOf(
                b("piano", MONDAY, "15:00", "16:00", "피아노", "peach", "음악학원"),
                b("math", MONDAY, "16:30", "17:30", "수학", "sky", "2층 교실"),
                b("eng", TUESDAY, "15:00", "16:15", "영어", "mint"),
                b("art", TUESDAY, "17:00", "17:15", "미술", "lilac"),
                b("tkd", WEDNESDAY, "15:00", "16:00", "태권도", "coral"),
                b("long", WEDNESDAY, "16:30", "18:00", "코딩 로봇 만들기 특강 심화반", "sage", "과학관"),
                b("swim", THURSDAY, "15:00", "16:00", "수영", "sky"),
                b("violin", FRIDAY, "15:00", "16:00", "바이올린", "rose"),
                b("read", FRIDAY, "15:30", "16:30", "독서", "lemon"),
                b("academy", FRIDAY, "16:30", "18:00", "학원", "peach"),
                b("soccer", SATURDAY, "10:00", "11:00", "축구", "mint"),
                b("church", SUNDAY, "11:00", "12:00", "주일학교", "lilac"),
            ),
            cancellations = listOf(ManualCancellation("academy", week.dateOf(FRIDAY), "감기")),
        )

        val events = listOf(
            SpecialEvent("dentist", "치과", week.dateOf(MONDAY), listOf(SCHEDULE_ID), m("15:30"), m("16:20"),
                overlapActions = mapOf(SCHEDULE_ID to OverlapAction.CANCEL), paperKey = "pink"),
            SpecialEvent("makeup", "보강", week.dateOf(TUESDAY), listOf(SCHEDULE_ID), m("15:00"), m("15:45"),
                overlapActions = mapOf(SCHEDULE_ID to OverlapAction.KEEP), paperKey = "yellow"),
            SpecialEvent("trip", "체험학습", week.dateOf(WEDNESDAY), listOf(SCHEDULE_ID), allDay = true, paperKey = "green"),
            SpecialEvent("supplies", "준비물", week.dateOf(FRIDAY), listOf(SCHEDULE_ID), m("15:10"), m("15:40"), paperKey = "blue"),
            SpecialEvent("meeting", "선생님 상담", week.dateOf(FRIDAY), listOf(SCHEDULE_ID), m("15:20"), m("16:00"), paperKey = "green"),
        )

        val vacations = listOf(
            Vacation("artbreak", "미술 방학", week.dateOf(TUESDAY), week.dateOf(TUESDAY), listOf(SCHEDULE_ID), blockIds = listOf("art")),
            Vacation("family", "가족여행", week.dateOf(SATURDAY), week.dateOf(SUNDAY), listOf(SCHEDULE_ID)),
        )

        return AppData(schedules = listOf(schedule), specialEvents = events, vacations = vacations)
    }

    fun holidays(week: WeekRange) = HolidayCalendar(listOf(Holiday(week.dateOf(THURSDAY), "샘플 공휴일")))

    fun resolved(days: DaysMode = DaysMode.MON_FRI, week: WeekRange = week()): ResolvedWeek =
        OccurrenceResolver.resolve(data(week, days), SCHEDULE_ID, week, holidays(week), now(week))!!

    private fun m(hm: String): Int {
        val (h, mm) = hm.split(":").map { it.toInt() }
        return h * 60 + mm
    }
}

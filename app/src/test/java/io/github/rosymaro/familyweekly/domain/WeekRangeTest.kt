package io.github.rosymaro.familyweekly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekRangeTest {

    @Test
    fun anyDayMapsToItsMonday() {
        val monday = LocalDate.of(2026, 11, 16)
        for (offset in 0L..6L) {
            assertEquals(monday, WeekRange.of(monday.plusDays(offset)).monday)
        }
        assertEquals(monday.plusWeeks(1), WeekRange.of(monday.plusDays(7)).monday)
    }

    @Test
    fun weekSpanningNewYear() {
        // 2026-12-28(월) ~ 2027-01-03(일)
        val week = WeekRange.of(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.monday)
        assertEquals(LocalDate.of(2027, 1, 3), week.sunday)
        assertEquals(
            listOf(28, 29, 30, 31, 1),
            week.dates(DaysMode.MON_FRI).map { it.dayOfMonth },
        )
        assertEquals(7, week.dates(DaysMode.MON_SUN).size)
        assertTrue(LocalDate.of(2026, 12, 31) in week)
        assertTrue(LocalDate.of(2027, 1, 3) in week)
        assertFalse(LocalDate.of(2027, 1, 4) in week)
        assertFalse(LocalDate.of(2026, 12, 27) in week)
    }

    @Test
    fun sundayBelongsToPreviousMonday() {
        assertEquals(LocalDate.of(2026, 12, 28), WeekRange.of(LocalDate.of(2027, 1, 3)).monday)
    }

    @Test
    fun shift() {
        val week = WeekRange.of(LocalDate.of(2026, 12, 30))
        assertEquals(LocalDate.of(2027, 1, 4), week.shift(1).monday)
        assertEquals(LocalDate.of(2026, 12, 21), week.shift(-1).monday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mondayRequired() {
        WeekRange(LocalDate.of(2026, 11, 17))
    }
}

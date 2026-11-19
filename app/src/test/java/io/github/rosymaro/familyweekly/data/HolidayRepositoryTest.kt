package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.CustomHoliday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayRepositoryTest {

    @Test
    fun parsesAndSortsHolidays() {
        val repo = HolidayRepository {
            """
            { "version": 1, "holidays": [
              { "date": "2026-12-25", "name": "기독탄신일" },
              { "date": "2026-10-03", "name": "개천절" }
            ] }
            """.trimIndent()
        }
        assertEquals(listOf("개천절", "기독탄신일"), repo.builtIn.map { it.name })
        val cal = repo.calendar(listOf(CustomHoliday(LocalDate.of(2026, 10, 3), hidden = true)))
        assertNull(cal.holidayOn(LocalDate.of(2026, 10, 3)))
        assertEquals("기독탄신일", cal.holidayOn(LocalDate.of(2026, 12, 25))?.name)
    }

    @Test
    fun missingAssetMeansNoHolidays() {
        val repo = HolidayRepository { null }
        assertTrue(repo.builtIn.isEmpty())
        assertTrue(repo.calendar(emptyList()).between(LocalDate.MIN, LocalDate.MAX).isEmpty())
    }
}

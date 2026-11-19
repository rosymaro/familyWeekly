package io.github.rosymaro.familyweekly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeStepTest {

    @Test
    fun snapRoundsToNearestFiveMinutes() {
        assertEquals(600, TimeStep.snap(600))
        assertEquals(600, TimeStep.snap(601))
        assertEquals(600, TimeStep.snap(602))
        assertEquals(605, TimeStep.snap(603))
        assertEquals(605, TimeStep.snap(604))
        assertEquals(605, TimeStep.snap(607))
        assertEquals(610, TimeStep.snap(608))
    }

    @Test
    fun snapClampsToDay() {
        assertEquals(0, TimeStep.snap(-7))
        assertEquals(1440, TimeStep.snap(1442))
        assertEquals(1440, TimeStep.snap(2000))
    }

    @Test
    fun snapDouble() {
        assertEquals(600, TimeStep.snap(602.4))
        assertEquals(605, TimeStep.snap(602.5))
        assertEquals(605, TimeStep.snap(606.9))
    }

    @Test
    fun snappedValuesAreAlwaysOnStep() {
        for (m in -10..1450) assertTrue("$m", TimeStep.isOnStep(TimeStep.snap(m)))
        assertFalse(TimeStep.isOnStep(601))
    }

    @Test
    fun hourRounding() {
        assertEquals(600, TimeStep.floorToHour(615))
        assertEquals(600, TimeStep.floorToHour(600))
        assertEquals(660, TimeStep.ceilToHour(615))
        assertEquals(600, TimeStep.ceilToHour(600))
        assertEquals(660, TimeStep.ceilToHour(605))
    }

    @Test
    fun localTimeConversion() {
        assertEquals(LocalTime.of(9, 5), TimeStep.toLocalTime(545))
        assertEquals(545, TimeStep.fromLocalTime(LocalTime.of(9, 5)))
        assertEquals(LocalTime.of(23, 59), TimeStep.toLocalTime(1440))
    }

    @Test
    fun format() {
        assertEquals("9:05", TimeStep.format(545))
        assertEquals("0:00", TimeStep.format(0))
        assertEquals("24:00", TimeStep.format(1440))
    }
}

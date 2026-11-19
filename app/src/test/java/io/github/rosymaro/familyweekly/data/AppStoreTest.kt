package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.AlarmRule
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.AlarmTrigger
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.CustomHoliday
import io.github.rosymaro.familyweekly.domain.ManualCancellation
import io.github.rosymaro.familyweekly.domain.OverlapAction
import io.github.rosymaro.familyweekly.domain.QuietHours
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TestData
import io.github.rosymaro.familyweekly.domain.TimeRange
import io.github.rosymaro.familyweekly.domain.TimeRangeMode
import io.github.rosymaro.familyweekly.domain.TriggerType
import io.github.rosymaro.familyweekly.domain.Vacation
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.domain.mapSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek

class AppStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(): File = tmp.root

    /** 모든 종류의 항목이 들어간 데이터. */
    private val full: AppData = TestData.data
        .mapSchedule("harin") {
            it.copy(
                emoji = "🎹",
                timeRange = TimeRange(TimeRangeMode.FIXED, 480, 1320),
                alarmDefaults = AlarmRule(
                    triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 15), AlarmTrigger(TriggerType.AT_START)),
                    style = AlarmStyle.FULL_SCREEN_ALARM,
                ),
                cancellations = listOf(ManualCancellation("piano", TestData.day(DayOfWeek.MONDAY), "감기")),
            )
        }
        .copy(
            specialEvents = listOf(
                SpecialEvent(
                    "e", "치과", TestData.day(DayOfWeek.MONDAY), listOf("harin", "doyun"), 900, 960,
                    overlapActions = mapOf("harin" to OverlapAction.CANCEL), memo = "보험증",
                ),
            ),
            vacations = listOf(Vacation("v", "방학", TestData.MONDAY, TestData.MONDAY.plusDays(20), listOf("doyun"), listOf("swim"))),
            customHolidays = listOf(CustomHoliday(TestData.day(DayOfWeek.FRIDAY), "재량휴업일")),
            widgets = listOf(WidgetBinding(7, "harin", themeId = "clean-grid"), WidgetBinding(8, null)),
            settings = TestData.data.settings.copy(quietHours = QuietHours(1320, 420)),
        )

    @Test
    fun emptyWhenNoFile() {
        val store = AppStore(dir())
        assertEquals(AppData(), store.data.value)
        assertNull(store.loadIssue.value)
    }

    @Test
    fun saveAndLoadRoundTrip() {
        AppStore(dir()).update { full }
        val reloaded = AppStore(dir())
        assertEquals(full, reloaded.data.value)
        assertNull(reloaded.loadIssue.value)
    }

    @Test
    fun noTempFileLeftAfterSave() {
        AppStore(dir()).update { full }
        assertTrue(File(dir(), AppStore.FILE_NAME).exists())
        assertFalse(File(dir(), AppStore.FILE_NAME + ".tmp").exists())
    }

    @Test
    fun updatePublishesNewData() {
        val store = AppStore(dir())
        store.update { full }
        assertEquals(full, store.data.value)
        store.update { it.copy(schedules = emptyList()) }
        assertTrue(store.data.value.schedules.isEmpty())
    }

    @Test
    fun corruptedFileIsBackedUpAndStartsEmpty() {
        File(dir(), AppStore.FILE_NAME).writeText("{ 이건 JSON이 아님")
        val store = AppStore(dir())
        assertEquals(AppData(), store.data.value)
        val issue = store.loadIssue.value as LoadIssue.Corrupted
        assertTrue(issue.backup.exists())
        assertEquals("{ 이건 JSON이 아님", issue.backup.readText())
        assertFalse(File(dir(), AppStore.FILE_NAME).exists())
        // 이후 저장은 정상
        store.update { full }
        assertEquals(full, AppStore(dir()).data.value)
    }

    @Test
    fun wrongShapeIsTreatedAsCorrupted() {
        File(dir(), AppStore.FILE_NAME).writeText("""{"version":1,"schedules":[{"name":"id 없음"}]}""")
        assertTrue(AppStore(dir()).loadIssue.value is LoadIssue.Corrupted)
    }

    @Test
    fun newerVersionIsNotOverwritten() {
        val text = """{"version":99,"schedules":[]}"""
        File(dir(), AppStore.FILE_NAME).writeText(text)
        val store = AppStore(dir())
        assertEquals(LoadIssue.NewerVersion(99), store.loadIssue.value)
        val failed = runCatching { store.update { full } }
        assertTrue(failed.isFailure)
        assertEquals(text, File(dir(), AppStore.FILE_NAME).readText())
    }

    @Test
    fun unknownKeysAndMissingDefaultsAreTolerated() {
        File(dir(), AppStore.FILE_NAME).writeText(
            """
            {
              "version": 1,
              "futureThing": 123,
              "schedules": [
                { "id": "a", "name": "첫째",
                  "blocks": [ { "id": "b", "title": "피아노", "day": "MONDAY", "startMin": 900, "endMin": 960, "newField": true } ] }
              ]
            }
            """.trimIndent(),
        )
        val store = AppStore(dir())
        assertNull(store.loadIssue.value)
        val s = store.data.value.schedules.single()
        assertEquals("첫째", s.name)
        assertEquals(900, s.blocks.single().startMin)
        assertTrue(s.alarmsEnabled)
    }

    @Test
    fun missingVersionIsTreatedAsOne() {
        File(dir(), AppStore.FILE_NAME).writeText("""{"schedules":[]}""")
        val store = AppStore(dir())
        assertNull(store.loadIssue.value)
        assertEquals(AppData.CURRENT_VERSION, store.data.value.version)
    }
}

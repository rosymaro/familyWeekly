package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.data.AppStore
import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek

/** 포스트잇 떼기: 그 아이 시간표에서만 안 보이고, 특이 일정·유지/취소는 그대로. */
class DetachTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mon = day(DayOfWeek.MONDAY)
    private val event = SpecialEvent(
        "dentist", "치과", mon, listOf("harin", "doyun"), 15 * 60 + 30, 16 * 60,
        overlapActions = mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP),
    )
    private val base = EventOps.save(data, event)

    private fun week(d: AppData, id: String) = OccurrenceResolver.resolve(d, id, TestData.WEEK)!!

    @Test
    fun detachHidesPostItOnlyForThatSchedule() {
        val d = EventOps.setDetached(base, "dentist", "harin", detached = true)
        val harin = week(d, "harin")
        assertTrue(harin.events.isEmpty())
        assertEquals(listOf("dentist"), harin.detachedEvents.map { it.id })
        // 기존 일정 취소는 그대로 (뗀다고 수업이 되살아나지 않음)
        assertEquals(OccurrenceStatus.CANCELLED, harin.occurrences.single { it.block.id == "piano" }.status)
        // 다른 아이에게는 그대로 붙어 있음
        val doyun = week(d, "doyun")
        assertEquals(listOf("dentist"), doyun.events.map { it.event.id })
        assertTrue(doyun.detachedEvents.isEmpty())
        // 특이 일정은 캘린더에 남음
        assertEquals(listOf("dentist"), EventOps.on(d, mon).map { it.id })
        assertEquals(listOf("harin", "doyun"), d.specialEvents.single().scheduleIds)
    }

    @Test
    fun reattach() {
        val off = EventOps.setDetached(base, "dentist", "harin", true)
        val on = EventOps.setDetached(off, "dentist", "harin", false)
        assertEquals(1, week(on, "harin").events.size)
        assertTrue(on.specialEvents.single().detachedFrom.isEmpty())
        // 두 번 떼도 한 번만 기록
        val twice = EventOps.setDetached(off, "dentist", "harin", true)
        assertEquals(listOf("harin"), twice.specialEvents.single().detachedFrom)
    }

    @Test
    fun cannotDetachFromScheduleNotAttached() {
        val ev = event.copy(id = "solo", scheduleIds = listOf("harin"), overlapActions = mapOf("harin" to OverlapAction.KEEP))
        val d = EventOps.save(data, ev)
        assertEquals(d, EventOps.setDetached(d, "solo", "doyun", true))
    }

    @Test
    fun removingScheduleFromEventDropsDetachMark() {
        val off = EventOps.setDetached(base, "dentist", "harin", true)
        val edited = off.specialEvents.single().copy(scheduleIds = listOf("doyun"))
        val saved = EventOps.save(off, edited)
        assertTrue(saved.specialEvents.single().detachedFrom.isEmpty())
    }

    @Test
    fun detachedEventDoesNotWidenAutoRange() {
        val late = SpecialEvent("late", "늦은 일정", mon, listOf("harin"), 20 * 60, 21 * 60)
        val d = EventOps.setDetached(EventOps.save(data, late), "late", "harin", true)
        assertEquals(18 * 60, week(d, "harin").displayEndMin)
    }

    @Test
    fun savedAsVersion2AndVersion1FileStillLoads() {
        val dir = tmp.root
        File(dir, AppStore.FILE_NAME).writeText(
            """{"version":1,"schedules":[{"id":"a","name":"첫째"}],
               "specialEvents":[{"id":"e","title":"치과","date":"2026-11-16","scheduleIds":["a"],"startMin":900,"endMin":960}]}""",
        )
        val store = AppStore(dir)
        assertNull(store.loadIssue.value)
        assertTrue(store.data.value.specialEvents.single().detachedFrom.isEmpty())
        store.update { EventOps.setDetached(it, "e", "a", true) }
        val text = File(dir, AppStore.FILE_NAME).readText()
        assertTrue(text.contains("\"version\": 2"))
        assertEquals(listOf("a"), AppStore(dir).data.value.specialEvents.single().detachedFrom)
    }
}

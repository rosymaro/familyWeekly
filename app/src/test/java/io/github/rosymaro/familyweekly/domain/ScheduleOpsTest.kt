package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import io.github.rosymaro.familyweekly.domain.TestData.ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY as MON
import java.time.DayOfWeek.WEDNESDAY as WED

class ScheduleOpsTest {

    private val grouped = data.mapSchedule("harin") { s ->
        s.copy(
            blocks = s.blocks + listOf(
                TestData.block("eng-mon", MON, "18:00", "19:00", "영어", groupId = "eng"),
                TestData.block("eng-wed", WED, "18:00", "19:00", "영어", groupId = "eng"),
            ),
            cancellations = listOf(ManualCancellation("piano", day(MON), "감기")),
            alarmDefaults = AlarmRule(style = AlarmStyle.FULL_SCREEN_ALARM),
            themeId = "playful",
        )
    }

    @Test
    fun addAppendsSchedule() {
        val (d, s) = ScheduleOps.add(data, "  막내 ", DaysMode.MON_SUN, ids = ids("s"))
        assertEquals("s-1", s.id)
        assertEquals("막내", s.name)
        assertEquals(DaysMode.MON_SUN, s.days)
        assertEquals(listOf("harin", "doyun", "s-1"), d.schedules.map { it.id })
    }

    @Test
    fun rename() {
        val d = ScheduleOps.rename(data, "harin", " 첫째 하린 ")
        assertEquals("첫째 하린", d.schedule("harin")!!.name)
        assertEquals("도윤", d.schedule("doyun")!!.name)
    }

    @Test
    fun duplicateIsDeepCopyWithNewIds() {
        val (d, copy) = ScheduleOps.duplicate(grouped, "harin", ids = ids("n"))!!
        val source = grouped.schedule("harin")!!

        assertEquals("하린 복사본", copy.name)
        assertEquals(listOf("harin", copy.id, "doyun"), d.schedules.map { it.id }) // 원본 바로 뒤
        assertEquals(source.blocks.size, copy.blocks.size)
        // 블록 id는 모두 새것, 내용은 같음
        assertTrue(copy.blocks.none { b -> source.blocks.any { it.id == b.id } })
        assertEquals(source.blocks.map { it.title to it.startMin }, copy.blocks.map { it.title to it.startMin })
        // 같은 그룹은 새 그룹 id 하나로 묶임
        val groups = copy.blocks.filter { it.title == "영어" }.map { it.groupId }.distinct()
        assertEquals(1, groups.size)
        assertNotEquals("eng", groups.single())
        // 개별 취소는 새 블록을 가리킴
        val piano = copy.blocks.single { it.title == "피아노" }
        assertEquals(listOf(ManualCancellation(piano.id, day(MON), "감기")), copy.cancellations)
        // 알림·테마·요일 복사
        assertEquals(AlarmStyle.FULL_SCREEN_ALARM, copy.alarmDefaults?.style)
        assertEquals("playful", copy.themeId)
        assertEquals(source.days, copy.days)
        // 원본은 그대로
        assertEquals(source, d.schedule("harin"))
    }

    @Test
    fun duplicateCustomName() {
        val (_, copy) = ScheduleOps.duplicate(data, "harin", newName = "하린 2학기", ids = ids())!!
        assertEquals("하린 2학기", copy.name)
    }

    @Test
    fun duplicateWithoutAttachLeavesEventsAlone() {
        val ev = SpecialEvent("e", "병원", day(MON), listOf("harin"), 900, 960, overlapActions = mapOf("harin" to OverlapAction.CANCEL))
        val v = Vacation("v", "방학", day(MON), day(WED), listOf("harin"))
        val (d, copy) = ScheduleOps.duplicate(data.copy(specialEvents = listOf(ev), vacations = listOf(v)), "harin", ids = ids())!!
        assertFalse(copy.id in d.specialEvents.single().scheduleIds)
        assertFalse(copy.id in d.vacations.single().scheduleIds)
    }

    @Test
    fun duplicateWithAttachCopiesTargetsAndChoices() {
        val ev = SpecialEvent("e", "병원", day(MON), listOf("harin"), 900, 960, overlapActions = mapOf("harin" to OverlapAction.CANCEL))
        val v = Vacation("v", "학원 방학", day(MON), day(WED), listOf("harin"), blockIds = listOf("math"))
        val other = SpecialEvent("o", "축구 대회", day(MON), listOf("doyun"), 600, 660)
        val src = data.copy(specialEvents = listOf(ev, other), vacations = listOf(v))

        val (d, copy) = ScheduleOps.duplicate(src, "harin", attachEventsAndVacations = true, ids = ids())!!
        val newEv = d.specialEvents.single { it.id == "e" }
        assertEquals(listOf("harin", copy.id), newEv.scheduleIds)
        assertEquals(OverlapAction.CANCEL, newEv.overlapActionFor(copy.id))
        assertEquals(listOf("doyun"), d.specialEvents.single { it.id == "o" }.scheduleIds)

        val newVac = d.vacations.single()
        assertEquals(listOf("harin", copy.id), newVac.scheduleIds)
        val copiedMath = copy.blocks.single { it.title == "수학" }.id
        assertEquals(listOf("math", copiedMath), newVac.blockIds)

        // 복사본에서도 실제로 취소 계산이 됨
        val r = OccurrenceResolver.resolve(d, copy.id, TestData.WEEK)!!
        assertEquals(OccurrenceStatus.CANCELLED, r.occurrences.single { it.block.id == copiedMath }.status)
    }

    @Test
    fun duplicateUnknownReturnsNull() {
        assertNull(ScheduleOps.duplicate(data, "nobody"))
    }

    @Test
    fun deleteCleansReferences() {
        val shared = SpecialEvent(
            "shared", "가족 행사", day(MON), listOf("harin", "doyun"), 900, 960,
            overlapActions = mapOf("harin" to OverlapAction.CANCEL, "doyun" to OverlapAction.KEEP),
        )
        val onlyHarin = SpecialEvent("solo", "병원", day(WED), listOf("harin"), 600, 660)
        val vShared = Vacation("vs", "방학", day(MON), day(WED), listOf("harin", "doyun"), blockIds = listOf("piano", "swim"))
        val vSolo = Vacation("vh", "할머니댁", day(MON), day(MON), listOf("harin"))
        val widgets = listOf(WidgetBinding(1, "harin"), WidgetBinding(2, "doyun"))
        val src = data.copy(
            specialEvents = listOf(shared, onlyHarin),
            vacations = listOf(vShared, vSolo),
            widgets = widgets,
        )

        val orphans = ScheduleOps.orphansIfDeleted(src, "harin")
        assertEquals(listOf("solo"), orphans.eventIds)
        assertEquals(listOf("vh"), orphans.vacationIds)

        // 고아 유지
        val kept = ScheduleOps.delete(src, "harin", removeOrphans = false)
        assertNull(kept.schedule("harin"))
        val keptShared = kept.specialEvents.single { it.id == "shared" }
        assertEquals(listOf("doyun"), keptShared.scheduleIds)
        assertEquals(mapOf("doyun" to OverlapAction.KEEP), keptShared.overlapActions)
        assertEquals(emptyList<String>(), kept.specialEvents.single { it.id == "solo" }.scheduleIds)
        val keptVac = kept.vacations.single { it.id == "vs" }
        assertEquals(listOf("doyun"), keptVac.scheduleIds)
        assertEquals(listOf("swim"), keptVac.blockIds)
        assertEquals(listOf(WidgetBinding(1, null), WidgetBinding(2, "doyun")), kept.widgets)

        // 고아 삭제
        val removed = ScheduleOps.delete(src, "harin", removeOrphans = true)
        assertEquals(listOf("shared"), removed.specialEvents.map { it.id })
        assertEquals(listOf("vs"), removed.vacations.map { it.id })
    }

    @Test
    fun deleteDoesNotTouchPreexistingOrphans() {
        val alreadyEmpty = SpecialEvent("old", "예전 일정", day(MON), emptyList(), 600, 660)
        val d = ScheduleOps.delete(data.copy(specialEvents = listOf(alreadyEmpty)), "harin", removeOrphans = true)
        assertEquals(listOf("old"), d.specialEvents.map { it.id })
    }

    @Test
    fun deleteUnknownIsNoOp() {
        assertEquals(data, ScheduleOps.delete(data, "nobody", removeOrphans = true))
    }

    @Test
    fun move() {
        val (three, _) = ScheduleOps.add(data, "막내", ids = ids("s"))
        assertEquals(listOf("s-1", "harin", "doyun"), ScheduleOps.move(three, "s-1", 0).schedules.map { it.id })
        assertEquals(listOf("doyun", "s-1", "harin"), ScheduleOps.move(three, "harin", 99).schedules.map { it.id })
    }
}

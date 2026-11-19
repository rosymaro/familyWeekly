package io.github.rosymaro.familyweekly.domain

import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import io.github.rosymaro.familyweekly.domain.TestData.ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY as FRI
import java.time.DayOfWeek.MONDAY as MON
import java.time.DayOfWeek.TUESDAY as TUE
import java.time.DayOfWeek.WEDNESDAY as WED

class BlockOpsTest {

    private fun draft(vararg days: java.time.DayOfWeek, start: Int = 600, end: Int = 660, title: String = "영어") =
        BlockDraft(title = title, days = days.toSet(), startMin = start, endMin = end, colorKey = "mint", place = " 학원 ")

    private fun AppData.harin() = schedule("harin")!!

    @Test
    fun validation() {
        assertNull(BlockOps.validate(draft(MON)))
        assertNotNull(BlockOps.validate(draft(MON, title = " ")))
        assertNotNull(BlockOps.validate(draft()))
        assertNotNull(BlockOps.validate(draft(MON, start = 600, end = 600)))
        assertNotNull(BlockOps.validate(draft(MON, start = 601, end = 660)))
        assertNotNull(BlockOps.validate(draft(MON, start = 1400, end = 1445)))
    }

    @Test
    fun newSingleDayHasNoGroup() {
        val d = BlockOps.save(data, "harin", draft(TUE), editingBlockId = null, ids = ids("n"))
        val b = d.harin().blocks.last()
        assertEquals("n-1", b.id)
        assertEquals(TUE, b.day)
        assertNull(b.groupId)
        assertEquals("학원", b.place) // 앞뒤 공백 정리
        assertEquals(4, d.harin().blocks.size)
    }

    @Test
    fun newMultiDaySharesGroup() {
        val d = BlockOps.save(data, "harin", draft(FRI, MON, WED), editingBlockId = null, ids = ids("n"))
        val created = d.harin().blocks.drop(3)
        assertEquals(listOf(MON, WED, FRI), created.map { it.day })
        assertEquals(1, created.map { it.groupId }.distinct().size)
        assertNotNull(created.first().groupId)
        assertEquals(3, created.map { it.id }.distinct().size)
    }

    @Test
    fun editGroupAddsAndRemovesDays() {
        val (d0) = BlockOps.save(data, "harin", draft(MON, WED), null, ids = ids("n")) to Unit
        val mon = d0.harin().blocks.single { it.title == "영어" && it.day == MON }
        val gid = mon.groupId!!
        // 이번 주 수요일 영어 개별 취소해 둠 → 수요일을 빼면 취소도 사라져야 함
        val wed = d0.harin().blocks.single { it.title == "영어" && it.day == WED }
        val d1 = BlockOps.toggleCancellation(d0, "harin", wed.id, day(WED))

        val d2 = BlockOps.save(d1, "harin", draft(MON, FRI, start = 900, end = 960, title = "영어 회화"), mon.id, applyToGroup = true, ids = ids("m"))
        val group = d2.harin().blocks.filter { it.groupId == gid }
        assertEquals(listOf(MON, FRI), group.map { it.day }.sorted())
        assertTrue(group.all { it.title == "영어 회화" && it.startMin == 900 && it.endMin == 960 })
        assertEquals(mon.id, group.single { it.day == MON }.id) // 기존 블록 id 유지
        assertTrue(d2.harin().cancellations.none { it.blockId == wed.id })
        assertTrue(d2.harin().blocks.none { it.id == wed.id })
    }

    @Test
    fun editOnlyThisBlock() {
        val d0 = BlockOps.save(data, "harin", draft(MON, WED), null, ids = ids("n"))
        val mon = d0.harin().blocks.single { it.title == "영어" && it.day == MON }
        val d1 = BlockOps.save(d0, "harin", draft(MON, start = 700, end = 760, title = "영어(월)"), mon.id, applyToGroup = false)
        val g = BlockOps.groupOf(d1.harin(), mon.id)
        assertEquals(2, g.size)
        assertEquals("영어(월)", g.single { it.day == MON }.title)
        assertEquals("영어", g.single { it.day == WED }.title)
    }

    @Test
    fun editUngroupedIntoMultipleDaysCreatesGroup() {
        val d = BlockOps.save(data, "harin", draft(MON, TUE, title = "피아노"), "piano", applyToGroup = true, ids = ids("g"))
        val group = BlockOps.groupOf(d.harin(), "piano")
        assertEquals(listOf(MON, TUE), group.map { it.day }.sorted())
        assertNotNull(group.first().groupId)
    }

    @Test
    fun deleteSingleOrGroupAndCleanReferences() {
        val d0 = BlockOps.save(data, "harin", draft(MON, WED), null, ids = ids("n"))
        val mon = d0.harin().blocks.single { it.title == "영어" && it.day == MON }
        val vac = Vacation("v", "방학", day(MON), day(FRI), listOf("harin"), blockIds = listOf(mon.id, "math"))
        val d1 = BlockOps.toggleCancellation(d0.copy(vacations = listOf(vac)), "harin", mon.id, day(MON))

        val one = BlockOps.delete(d1, "harin", mon.id, wholeGroup = false)
        assertEquals(1, one.harin().blocks.count { it.title == "영어" })
        assertTrue(one.harin().cancellations.isEmpty())
        assertEquals(listOf("math"), one.vacations.single().blockIds)

        val all = BlockOps.delete(d1, "harin", mon.id, wholeGroup = true)
        assertEquals(0, all.harin().blocks.count { it.title == "영어" })
    }

    @Test
    fun moveKeepsDurationAndSnapsAndClamps() {
        val d = BlockOps.move(data, "harin", "piano", WED, 17 * 60 + 2)
        val b = d.harin().blocks.single { it.id == "piano" }
        assertEquals(WED, b.day)
        assertEquals(17 * 60, b.startMin)
        assertEquals(18 * 60, b.endMin)

        val late = BlockOps.move(data, "harin", "piano", MON, 23 * 60 + 30).harin().blocks.single { it.id == "piano" }
        assertEquals(23 * 60, late.startMin)
        assertEquals(24 * 60, late.endMin)
    }

    @Test
    fun toggleCancellation() {
        val on = BlockOps.toggleCancellation(data, "harin", "piano", day(MON))
        assertTrue(BlockOps.isCancelled(on.harin(), "piano", day(MON)))
        assertFalse(BlockOps.isCancelled(on.harin(), "piano", day(MON).plusWeeks(1)))
        val off = BlockOps.toggleCancellation(on, "harin", "piano", day(MON))
        assertFalse(BlockOps.isCancelled(off.harin(), "piano", day(MON)))
    }

    @Test
    fun todaySummary() {
        val week = OccurrenceResolver.resolve(data, "harin", TestData.WEEK, now = day(MON).atTime(15, 30))!!
        val s = TodaySummary.of(week, day(MON).atTime(15, 30))
        assertEquals(2, s.remaining) // 피아노(진행 중) + 수학
        assertEquals("piano", s.next?.block?.id)
        assertEquals("오늘 남은 일정 2개 · 다음 15:00 피아노", s.text())

        val evening = TodaySummary.of(week, day(MON).atTime(20, 0))
        assertEquals("오늘 남은 일정 없음", evening.text())

        val saturday = TodaySummary.of(week, day(java.time.DayOfWeek.SATURDAY).atTime(10, 0))
        assertFalse(saturday.isScheduleDay)

        val cancelled = BlockOps.toggleCancellation(data, "harin", "math", day(MON))
        val w2 = OccurrenceResolver.resolve(cancelled, "harin", TestData.WEEK)!!
        val s2 = TodaySummary.of(w2, day(MON).atTime(16, 10))
        assertEquals(0, s2.remaining)
        assertEquals(1, s2.cancelledToday)
        assertEquals("오늘 남은 일정 없음 · 취소 1", s2.text())
    }
}

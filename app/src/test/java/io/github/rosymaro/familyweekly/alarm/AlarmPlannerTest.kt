package io.github.rosymaro.familyweekly.alarm

import io.github.rosymaro.familyweekly.alarm.planner.AlarmDiff
import io.github.rosymaro.familyweekly.alarm.planner.AlarmPlanner
import io.github.rosymaro.familyweekly.alarm.planner.AlarmSource
import io.github.rosymaro.familyweekly.alarm.planner.AlarmText
import io.github.rosymaro.familyweekly.alarm.planner.ChannelImportance
import io.github.rosymaro.familyweekly.alarm.planner.ChannelRules
import io.github.rosymaro.familyweekly.domain.AlarmRule
import io.github.rosymaro.familyweekly.domain.AlarmStyle
import io.github.rosymaro.familyweekly.domain.AlarmTrigger
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.Block
import io.github.rosymaro.familyweekly.domain.BlockOps
import io.github.rosymaro.familyweekly.domain.EventOps
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import io.github.rosymaro.familyweekly.domain.OverlapAction
import io.github.rosymaro.familyweekly.domain.QuietHours
import io.github.rosymaro.familyweekly.domain.SoundKind
import io.github.rosymaro.familyweekly.domain.SoundSetting
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TestData
import io.github.rosymaro.familyweekly.domain.TestData.data
import io.github.rosymaro.familyweekly.domain.TestData.day
import io.github.rosymaro.familyweekly.domain.TriggerType
import io.github.rosymaro.familyweekly.domain.Vacation
import io.github.rosymaro.familyweekly.domain.VibrationPattern
import io.github.rosymaro.familyweekly.domain.VibrationSetting
import io.github.rosymaro.familyweekly.domain.mapSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime

/** 기본 규칙: 10분 전 팝업. 하린 월 피아노 15:00, 수학 16:30, 수 태권도 17:00 / 도윤 월 수영 15:30, 토 축구 10:00 */
class AlarmPlannerTest {

    private val mon = day(MONDAY)
    private fun plan(d: AppData = data, now: LocalDateTime = mon.atTime(8, 0), h: HolidayCalendar = HolidayCalendar.EMPTY) =
        AlarmPlanner.plan(d, h, now)

    @Test
    fun defaultRuleTenMinutesBeforeWithin48Hours() {
        val p = plan()
        assertEquals(
            listOf("${mon}T14:50", "${mon}T15:20", "${mon}T16:20"),
            p.map { it.at },
        ) // 수요일 태권도는 48시간 밖
        assertEquals("하린 · 피아노 10분 전", p.first().title) // 장소가 없으면 "( )" 부분이 빠짐
        assertEquals("15:00~16:00", p.first().text)
        assertEquals(AlarmStyle.HEADS_UP, p.first().style)
        assertTrue(p.all { it.source == AlarmSource.BLOCK })
    }

    @Test
    fun windowMovesWithNow() {
        assertEquals(listOf("${mon}T15:20", "${mon}T16:20"), plan(now = mon.atTime(15, 0)).map { it.at })
        // 월 17:00 → 수 17:00까지: 수요일 태권도(17:00) 10분 전이 들어옴
        assertEquals(listOf("${day(WEDNESDAY)}T16:50"), plan(now = mon.atTime(17, 0)).map { it.at })
        // 정확히 지금인 알림은 이미 지난 것으로 봄
        assertTrue(plan(now = mon.atTime(14, 50)).none { it.at == "${mon}T14:50" })
    }

    @Test
    fun allTriggerTypesAndMultipleBefore() {
        val rule = AlarmRule(
            triggers = listOf(
                AlarmTrigger(TriggerType.BEFORE_START, 30),
                AlarmTrigger(TriggerType.BEFORE_START, 5),
                AlarmTrigger(TriggerType.AT_START),
                AlarmTrigger(TriggerType.BEFORE_END, 10),
                AlarmTrigger(TriggerType.AT_END),
            ),
        )
        val d = data.mapSchedule("harin") { s -> s.copy(blocks = s.blocks.map { if (it.id == "piano") it.copy(alarm = rule, place = "음악학원") else it }) }
        val piano = plan(d).filter { it.sourceId == "piano" }
        assertEquals(listOf("14:30", "14:55", "15:00", "15:50", "16:00"), piano.map { it.at.substringAfter("T") })
        assertEquals(
            listOf(
                "하린 · 피아노 30분 전 (음악학원)",
                "하린 · 피아노 5분 전 (음악학원)",
                "하린 · 피아노 시작",
                "하린 · 피아노 10분 뒤 끝 (음악학원)",
                "하린 · 피아노 끝",
            ),
            piano.map { it.title },
        )
        assertEquals("15:00~16:00 · 음악학원", piano.first().text)
    }

    @Test
    fun ruleHierarchyBlockOverScheduleOverApp() {
        val app = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 15)), style = AlarmStyle.NOTIFICATION)
        val sched = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.AT_START)), style = AlarmStyle.FULL_SCREEN_ALARM)
        val block = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 60)), style = AlarmStyle.SILENT_NOTIFICATION)
        val d = data.copy(settings = data.settings.copy(defaultAlarm = app))
            .mapSchedule("harin") { s -> s.copy(alarmDefaults = sched, blocks = s.blocks.map { if (it.id == "math") it.copy(alarm = block) else it }) }
        val p = plan(d)
        val piano = p.single { it.sourceId == "piano" }
        assertEquals("${mon}T15:00", piano.at) // 스케줄 기본값
        assertEquals(AlarmStyle.FULL_SCREEN_ALARM, piano.style)
        val math = p.single { it.sourceId == "math" }
        assertEquals("${mon}T15:30", math.at) // 블록 개별값
        assertEquals(AlarmStyle.SILENT_NOTIFICATION, math.style)
        val swim = p.single { it.sourceId == "swim" }
        assertEquals("${mon}T15:15", swim.at) // 앱 기본값
    }

    @Test
    fun cancelledBlocksDoNotAlarm() {
        // 개별 취소, 휴가, 특이 일정 CANCEL
        var d = BlockOps.toggleCancellation(data, "harin", "piano", mon)
        d = d.copy(vacations = listOf(Vacation("v", "방학", mon, mon, listOf("doyun"))))
        d = EventOps.save(d, SpecialEvent("e", "상담", mon, listOf("harin"), 16 * 60 + 30, 17 * 60, overlapActions = mapOf("harin" to OverlapAction.CANCEL), alarm = AlarmRule(triggers = emptyList())))
        assertTrue(plan(d).none { it.source == AlarmSource.BLOCK })
    }

    @Test
    fun holidayCancelPolicyDoesNotAlarm() {
        val h = HolidayCalendar(listOf(io.github.rosymaro.familyweekly.domain.Holiday(mon, "공휴일")))
        val d = data.mapSchedule("harin") { it.copy(holidayPolicy = io.github.rosymaro.familyweekly.domain.HolidayPolicy.CANCEL_BLOCKS) }
        val p = plan(d, h = h)
        assertTrue(p.none { it.scheduleId == "harin" })
        assertEquals(1, p.count { it.scheduleId == "doyun" })
    }

    @Test
    fun alarmsAreOffByDefault() {
        // 새로 설치한 앱: 알림은 꺼져 있음
        assertFalse(io.github.rosymaro.familyweekly.domain.Settings().alarmsEnabled)
        assertTrue(plan(data.copy(settings = io.github.rosymaro.familyweekly.domain.Settings())).isEmpty())
    }

    @Test
    fun switchesOff() {
        assertTrue(plan(data.copy(settings = data.settings.copy(alarmsEnabled = false))).isEmpty())
        val p = plan(data.mapSchedule("harin") { it.copy(alarmsEnabled = false) })
        assertTrue(p.all { it.scheduleId == "doyun" })
        // 트리거가 없으면 알림 없음
        val none = data.copy(settings = data.settings.copy(defaultAlarm = AlarmRule(triggers = emptyList())))
        assertTrue(plan(none).isEmpty())
    }

    @Test
    fun eventAlarmsOnceAcrossChildrenAndEvenWhenDetached() {
        val ev = SpecialEvent(
            "dentist", "치과", mon, listOf("harin", "doyun"), 9 * 60, 10 * 60,
            alarm = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 30))),
        )
        var d = EventOps.save(data, ev)
        var p = plan(d).filter { it.source == AlarmSource.EVENT }
        assertEquals(1, p.size)
        assertEquals("${mon}T08:30", p.single().at)
        assertEquals("하린·도윤 · 📌 치과 30분 전", p.single().title)

        d = EventOps.setDetached(d, "dentist", "harin", true)
        assertEquals(1, plan(d).count { it.source == AlarmSource.EVENT }) // 뗀 포스트잇도 알림은 그대로

        // 두 아이 모두 알림을 끄면 없음
        d = d.mapSchedule("harin") { it.copy(alarmsEnabled = false) }.mapSchedule("doyun") { it.copy(alarmsEnabled = false) }
        assertTrue(plan(d).none { it.source == AlarmSource.EVENT })
    }

    @Test
    fun allDayEventUsesAllDayAlarmTime() {
        val ev = SpecialEvent("trip", "체험학습", mon.plusDays(1), listOf("harin"), allDay = true)
        val d = EventOps.save(data, ev.copy(alarm = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 30), AlarmTrigger(TriggerType.AT_START)))))
            .let { it.copy(settings = it.settings.copy(allDayAlarmMin = 7 * 60)) }
        val p = plan(d).filter { it.sourceId == "trip" }
        assertEquals(listOf("${mon.plusDays(1)}T06:30", "${mon.plusDays(1)}T07:00"), p.map { it.at })
        assertEquals("하루 종일", p.first().text)
    }

    @Test
    fun beforeStartCrossesMidnightAndWeekBoundary() {
        // 월요일 0:10 일정, 30분 전 → 일요일 23:40 (지난주 일요일 밤에 계획)
        val early = Block("early", "새벽 수영", MONDAY, 10, 60, alarm = AlarmRule(triggers = listOf(AlarmTrigger(TriggerType.BEFORE_START, 30))))
        val d = data.mapSchedule("doyun") { it.copy(blocks = it.blocks + early) }
        val sundayNight = day(SUNDAY).minusWeeks(1).atTime(22, 0)
        val p = plan(d, now = sundayNight).filter { it.sourceId == "early" }
        assertEquals(listOf("${mon.minusDays(1)}T23:40"), p.map { it.at })
    }

    @Test
    fun weekendBlockOfMonSunSchedule() {
        val p = plan(now = day(SATURDAY).atTime(8, 0))
        assertEquals("${day(SATURDAY)}T09:50", p.first { it.sourceId == "soccer" }.at)
    }

    @Test
    fun quietHoursSilenceExceptAllowedFullScreen() {
        val q = QuietHours(22 * 60, 7 * 60)
        assertTrue(AlarmPlanner.inQuietHours(q, mon.atTime(23, 0)))
        assertTrue(AlarmPlanner.inQuietHours(q, mon.atTime(6, 59)))
        assertFalse(AlarmPlanner.inQuietHours(q, mon.atTime(7, 0)))
        assertFalse(AlarmPlanner.inQuietHours(q, mon.atTime(21, 59)))
        assertTrue(AlarmPlanner.inQuietHours(QuietHours(13 * 60, 14 * 60), mon.atTime(13, 30)))
        assertFalse(AlarmPlanner.inQuietHours(null, mon.atTime(13, 30)))

        val night = Block("night", "야간 자습", MONDAY, 22 * 60 + 30, 23 * 60)
        val base = data.mapSchedule("harin") { it.copy(blocks = it.blocks + night) }.let { it.copy(settings = it.settings.copy(quietHours = q)) }
        val quiet = plan(base).single { it.sourceId == "night" }
        assertEquals(AlarmStyle.SILENT_NOTIFICATION, quiet.style)
        assertTrue(quiet.quieted)
        assertFalse(quiet.sound.enabled)

        val full = AlarmRule(style = AlarmStyle.FULL_SCREEN_ALARM, repeatUntilDismissed = true)
        val withFull = base.mapSchedule("harin") { s -> s.copy(blocks = s.blocks.map { if (it.id == "night") it.copy(alarm = full) else it }) }
        assertEquals(AlarmStyle.SILENT_NOTIFICATION, plan(withFull).single { it.sourceId == "night" }.style)
        val allowed = withFull.copy(settings = withFull.settings.copy(quietHours = q.copy(allowFullScreenAlarm = true)))
        val a = plan(allowed).single { it.sourceId == "night" }
        assertEquals(AlarmStyle.FULL_SCREEN_ALARM, a.style)
        assertTrue(a.repeatUntilDismissed)
    }

    @Test
    fun keysAreStableAndUnique() {
        val a = plan()
        val b = plan(now = mon.atTime(8, 1))
        assertEquals(a.map { it.key }, b.map { it.key })
        assertEquals(a.size, a.map { it.key }.distinct().size)
        // 다음 주 같은 블록은 다른 키
        val next = plan(now = mon.plusWeeks(1).atTime(8, 0))
        assertTrue(next.map { it.key }.none { it in a.map { x -> x.key } })
    }

    // ── 등록 차이 ──

    @Test
    fun diffOnlyChangedAlarms() {
        val first = plan()
        val registered = first.associate { it.key to AlarmDiff.fingerprint(it) }
        assertTrue(AlarmDiff.diff(registered, first).let { it.cancel.isEmpty() && it.set.isEmpty() })

        // 피아노 시간을 옮기고 수학을 지우면: 피아노는 다시 등록, 수학은 해제, 수영은 그대로
        val moved = BlockOps.move(data, "harin", "piano", MONDAY, 14 * 60)
        val gone = BlockOps.delete(moved, "harin", "math", wholeGroup = false)
        val r = AlarmDiff.diff(registered, plan(gone))
        assertEquals(setOf(first.single { it.sourceId == "piano" }.key, first.single { it.sourceId == "math" }.key), r.cancel.toSet())
        assertEquals(listOf("piano"), r.set.map { it.sourceId })
        assertNotEquals(AlarmDiff.requestCode("a"), AlarmDiff.requestCode("b"))
    }

    // ── 채널 ──

    @Test
    fun channelPerCombination() {
        val p = plan().first()
        val heads = ChannelRules.specFor(p)
        assertEquals(ChannelImportance.HIGH, heads.importance)
        assertEquals(ChannelRules.DEFAULT_SOUND, heads.soundUri)
        assertEquals(listOf(0L, 250L), heads.vibration!!.toList())
        // 같은 조합이면 같은 채널
        assertEquals(heads.id, ChannelRules.specFor(p.copy(key = "x", title = "다른 제목")).id)
        // 소리 종류·진동이 다르면 다른 채널
        val alarmSound = ChannelRules.specFor(p.copy(sound = SoundSetting(kind = SoundKind.ALARM)))
        assertNotEquals(heads.id, alarmSound.id)
        assertTrue(alarmSound.alarmUsage)
        val dbl = ChannelRules.specFor(p.copy(vibration = VibrationSetting(pattern = VibrationPattern.DOUBLE)))
        assertNotEquals(heads.id, dbl.id)
        // 무음 알림은 소리·진동 없음
        val silent = ChannelRules.specFor(p.copy(style = AlarmStyle.SILENT_NOTIFICATION))
        assertEquals(ChannelImportance.LOW, silent.importance)
        assertNull(silent.soundUri)
        assertNull(silent.vibration)
        // 안 쓰는 채널 정리
        assertEquals(listOf("fw_alarm_old"), ChannelRules.unused(listOf("fw_alarm_old", heads.id, "other"), listOf(p)))
    }

    @Test
    fun textTemplate() {
        assertEquals("하린 · 피아노 10분 전 (학원)", AlarmText.fill("{아이} · {일정} {N}분 전 ({장소})", "하린", "피아노", 10, "학원"))
        assertEquals("하린 · 피아노 10분 전", AlarmText.fill("{아이} · {일정} {N}분 전 ({장소})", "하린", "피아노", 10, null))
        assertEquals("피아노 5분 남음", AlarmText.fill("{일정} {N}분 남음", "하린", "피아노", 5, null))
    }

}

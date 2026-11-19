package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.CustomHoliday
import io.github.rosymaro.familyweekly.domain.Holiday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate

class HolidaySyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun d(s: String) = LocalDate.parse(s)

    // ── 응답 해석 ──

    private val sample2026 = """
        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
         "body":{"items":{"item":[
           {"dateKind":"01","dateName":"1월1일","isHoliday":"Y","locdate":20260101,"seq":1},
           {"dateKind":"01","dateName":"설날","isHoliday":"Y","locdate":20260217,"seq":1},
           {"dateKind":"01","dateName":"대체공휴일","isHoliday":"Y","locdate":20260302,"seq":1},
           {"dateKind":"01","dateName":"전국동시지방선거","isHoliday":"Y","locdate":"20260603","seq":1},
           {"dateKind":"01","dateName":"어버이날","isHoliday":"N","locdate":20260508,"seq":1}
         ]},"numOfRows":100,"pageNo":1,"totalCount":5}}}
    """.trimIndent()

    @Test
    fun parsesHolidayList() {
        val r = KasiHolidayApi.parse(sample2026, 2026) as KasiResult.Ok
        assertEquals(
            listOf(
                Holiday(d("2026-01-01"), "신정"),
                Holiday(d("2026-02-17"), "설날"),
                Holiday(d("2026-03-02"), "대체공휴일"),
                Holiday(d("2026-06-03"), "전국동시지방선거"),
            ),
            r.holidays,
        ) // 쉬는 날이 아닌 기념일(isHoliday N)은 뺌, locdate 숫자/문자 둘 다
    }

    @Test
    fun singleItemIsObjectAndEmptyIsString() {
        val one = """{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":{"dateName":"한글날","isHoliday":"Y","locdate":20261009}},"totalCount":1}}}"""
        assertEquals(listOf(Holiday(d("2026-10-09"), "한글날")), (KasiHolidayApi.parse(one) as KasiResult.Ok).holidays)
        val none = """{"response":{"header":{"resultCode":"00"},"body":{"items":"","totalCount":0}}}"""
        assertTrue((KasiHolidayApi.parse(none) as KasiResult.Ok).holidays.isEmpty())
    }

    @Test
    fun sameDayHolidaysAreMerged() {
        val body = """{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
            {"dateName":"추석","isHoliday":"Y","locdate":20281003},
            {"dateName":"개천절","isHoliday":"Y","locdate":20281003}]}}}}"""
        assertEquals(listOf(Holiday(d("2028-10-03"), "추석 · 개천절")), (KasiHolidayApi.parse(body) as KasiResult.Ok).holidays)
    }

    @Test
    fun errorsFromJsonHeaderAndXml() {
        val json = """{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}"""
        assertTrue((KasiHolidayApi.parse(json) as KasiResult.Error).message.contains("인증키가 등록되지 않았어요"))
        val xml = """<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"""
        assertTrue((KasiHolidayApi.parse(xml) as KasiResult.Error).message.contains("인증키가 등록되지 않았어요"))
        assertTrue(KasiHolidayApi.parse("") is KasiResult.Error)
        assertTrue(KasiHolidayApi.parse("Unauthorized") is KasiResult.Error)
    }

    @Test
    fun keyEncoding() {
        val raw = KasiHolidayApi.buildUrl("ab+c/d==", 2026).toString()
        assertTrue(raw.contains("serviceKey=ab%2Bc%2Fd%3D%3D&"))
        assertTrue(raw.contains("solYear=2026"))
        val already = KasiHolidayApi.buildUrl("ab%2Bc%2Fd%3D%3D", 2026).toString()
        assertTrue(already.contains("serviceKey=ab%2Bc%2Fd%3D%3D&")) // 두 번 인코딩하지 않음
    }

    @Test
    fun networkFailureBecomesError() {
        val api = KasiHolidayApi { throw java.net.UnknownHostException("x") }
        assertEquals("인터넷에 연결되지 않았어요", (api.fetchYear("k", 2026) as KasiResult.Error).message)
    }

    // ── 저장소: 받은 해가 기본 자료를 대신함 ──

    private val bundledText = """{"version":1,"holidays":[
        {"date":"2026-01-01","name":"신정"},{"date":"2026-07-17","name":"제헌절"},
        {"date":"2027-01-01","name":"신정"}]}"""

    @Test
    fun fetchedYearReplacesBundledYear() {
        val cache = File(tmp.root, HolidayRepository.CACHE_FILE)
        val repo = HolidayRepository(cache) { bundledText }
        assertEquals(HolidaySource.BUNDLED, repo.sourceOf(2026))
        assertEquals(HolidaySource.NONE, repo.sourceOf(2031))

        repo.store(mapOf(2026 to listOf(Holiday(d("2026-01-01"), "신정"), Holiday(d("2026-01-27"), "임시공휴일"))), Instant.parse("2026-11-19T00:00:00Z"))
        assertEquals(HolidaySource.FETCHED, repo.sourceOf(2026))
        val cal = repo.calendar(emptyList())
        assertEquals("임시공휴일", cal.holidayOn(d("2026-01-27"))?.name)
        assertNull(cal.holidayOn(d("2026-07-17"))) // 받은 해에 없는 날은 기본 자료에서도 안 씀
        assertEquals("신정", cal.holidayOn(d("2027-01-01"))?.name) // 안 받은 해는 기본 자료

        // 사용자 숨기기는 받은 자료 위에도 적용
        assertNull(repo.calendar(listOf(CustomHoliday(d("2026-01-27"), hidden = true))).holidayOn(d("2026-01-27")))

        // 파일에 남아서 다시 열어도 그대로
        val reopened = HolidayRepository(cache) { bundledText }
        assertEquals("임시공휴일", reopened.calendar(emptyList()).holidayOn(d("2026-01-27"))?.name)
        assertEquals(listOf(2026), reopened.updates.value.yearsSorted())
    }

    @Test
    fun storeKeepsOtherYears() {
        val repo = HolidayRepository(File(tmp.root, "c.json")) { bundledText }
        repo.store(mapOf(2026 to listOf(Holiday(d("2026-01-01"), "신정"))), Instant.EPOCH)
        repo.store(mapOf(2027 to listOf(Holiday(d("2027-01-01"), "신정"))), Instant.EPOCH)
        assertEquals(listOf(2026, 2027), repo.updates.value.yearsSorted())
    }

    @Test
    fun brokenCacheFileIsIgnored() {
        val f = File(tmp.root, "c.json").apply { writeText("{ 깨짐") }
        val repo = HolidayRepository(f) { bundledText }
        assertEquals("제헌절", repo.calendar(emptyList()).holidayOn(d("2026-07-17"))?.name)
    }

    @Test
    fun refreshPolicy() {
        val today = d("2026-11-19")
        val now = Instant.parse("2026-11-19T00:00:00Z")
        assertTrue(HolidayRepository.needsRefresh(HolidayCache(), today, now))
        val fresh = HolidayCache(now.toString(), mapOf("2026" to emptyList()))
        assertFalse(HolidayRepository.needsRefresh(fresh, today, now.plusSeconds(86400 * 29)))
        assertTrue(HolidayRepository.needsRefresh(fresh, today, now.plusSeconds(86400 * 30)))
        // 해가 바뀌어 올해 자료가 없으면 바로
        assertTrue(HolidayRepository.needsRefresh(fresh, d("2027-01-01"), now.plusSeconds(86400)))
        assertEquals(listOf(2026, 2027), HolidayRepository.yearsToFetch(today))
    }

    // ── 받기 ──

    private fun fakeApi(byYear: Map<Int, String>) = KasiHolidayApi { url ->
        val year = Regex("solYear=(\\d+)").find(url.toString())!!.groupValues[1].toInt()
        byYear[year] ?: error("no data")
    }

    @Test
    fun syncStoresYearsAndSkipsUnpublishedNextYear() {
        val repo = HolidayRepository(File(tmp.root, "c.json")) { bundledText }
        val empty = """{"response":{"header":{"resultCode":"00"},"body":{"items":""}}}"""
        val syncer = HolidaySyncer(repo, fakeApi(mapOf(2026 to sample2026, 2027 to empty)), { Instant.parse("2026-11-19T00:00:00Z") }, { d("2026-11-19") })
        val r = syncer.sync("key")
        assertNull(r.error)
        assertEquals(listOf(2026), r.fetchedYears)
        assertEquals(4, r.count)
        assertEquals(HolidaySource.FETCHED, repo.sourceOf(2026))
        assertEquals(HolidaySource.BUNDLED, repo.sourceOf(2027))
        // 방금 받았으니 다시 받지 않음
        assertNull(syncer.syncIfNeeded("key"))
        assertNull(syncer.syncIfNeeded(null))
    }

    @Test
    fun syncErrorKeepsOldData() {
        val repo = HolidayRepository(File(tmp.root, "c.json")) { bundledText }
        val bad = """{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}"""
        val r = HolidaySyncer(repo, fakeApi(mapOf(2026 to bad, 2027 to bad)), { Instant.EPOCH }, { d("2026-11-19") }).sync("key")
        assertTrue(r.error!!.contains("인증키"))
        assertTrue(r.fetchedYears.isEmpty())
        assertEquals(HolidaySource.BUNDLED, repo.sourceOf(2026))
        assertNull(repo.updates.value.fetchedAt)
    }

}

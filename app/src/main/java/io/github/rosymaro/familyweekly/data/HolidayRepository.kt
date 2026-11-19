package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.CustomHoliday
import io.github.rosymaro.familyweekly.domain.Holiday
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class HolidayFile(
    val version: Int = 1,
    val holidays: List<Holiday> = emptyList(),
)

/** 공식 API에서 받아 둔 공휴일 (연도별). `files/holidays_cache.json` */
@Serializable
data class HolidayCache(
    /** ISO Instant. null이면 한 번도 받지 않음. */
    val fetchedAt: String? = null,
    /** "2026" → 그해 공휴일 전부 */
    val years: Map<String, List<Holiday>> = emptyMap(),
) {
    val fetchedInstant: Instant? get() = fetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    fun yearsSorted(): List<Int> = years.keys.mapNotNull { it.toIntOrNull() }.sorted()
}

/** 어느 해 자료를 어디서 가져왔는지 (화면 안내용). */
enum class HolidaySource { FETCHED, BUNDLED, NONE }

/**
 * 공휴일 = 앱에 들어 있는 기본 자료(assets/data/holidays_kr.json, 2030년까지)
 *        + 공식 API에서 받아 둔 자료(받은 해는 기본 자료를 통째로 대신함)
 *        + 사용자 공휴일(숨기기/추가, AppData.customHolidays).
 * 받은 자료가 바뀌면 [updates]가 바뀌어 화면이 다시 그린다.
 */
class HolidayRepository(
    private val cacheFile: File? = null,
    private val readAsset: () -> String?,
) {
    val bundled: List<Holiday> by lazy { readAsset()?.let(::parse).orEmpty() }

    private val _cache = MutableStateFlow(loadCache())
    val updates: StateFlow<HolidayCache> = _cache.asStateFlow()

    /** 받은 해는 받은 자료, 나머지 해는 기본 자료. */
    val builtIn: List<Holiday> get() = merge(bundled, _cache.value)

    fun calendar(custom: List<CustomHoliday>): HolidayCalendar = HolidayCalendar(builtIn, custom)

    fun sourceOf(year: Int): HolidaySource = when {
        _cache.value.years.containsKey(year.toString()) -> HolidaySource.FETCHED
        bundled.any { it.date.year == year } -> HolidaySource.BUNDLED
        else -> HolidaySource.NONE
    }

    /** 받은 해들을 저장 (이미 받은 다른 해는 남김). */
    @Synchronized
    fun store(fetched: Map<Int, List<Holiday>>, at: Instant) {
        val next = HolidayCache(
            fetchedAt = at.toString(),
            years = _cache.value.years + fetched.mapKeys { it.key.toString() },
        )
        cacheFile?.let { f ->
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(HolidayCache.serializer(), next))
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        _cache.value = next
    }

    private fun loadCache(): HolidayCache =
        cacheFile?.takeIf { it.exists() }?.let { f ->
            runCatching { json.decodeFromString(HolidayCache.serializer(), f.readText()) }.getOrNull()
        } ?: HolidayCache()

    companion object {
        const val ASSET_PATH = "data/holidays_kr.json"
        const val CACHE_FILE = "holidays_cache.json"

        /** 다시 받는 간격 (임시공휴일·선거일이 새로 정해지는 걸 따라가려고) */
        const val REFRESH_DAYS = 30L

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): List<Holiday> =
            json.decodeFromString(HolidayFile.serializer(), text).holidays.sortedBy { it.date }

        fun merge(bundled: List<Holiday>, cache: HolidayCache): List<Holiday> {
            val fetchedYears = cache.years.keys.mapNotNull { it.toIntOrNull() }.toSet()
            return (bundled.filter { it.date.year !in fetchedYears } + cache.years.values.flatten()).sortedBy { it.date }
        }

        /** 받아야 할 해: 올해와 내년. */
        fun yearsToFetch(today: LocalDate): List<Int> = listOf(today.year, today.year + 1)

        /**
         * 한 번도 안 받았거나, 올해 자료가 없거나, 받은 지 30일이 넘었으면 다시 받는다.
         * (내년 자료는 보통 그해 중반에 발표돼서, 없다고 매번 받지는 않는다 — 30일마다 다시 시도)
         */
        fun needsRefresh(cache: HolidayCache, today: LocalDate, now: Instant): Boolean {
            val at = cache.fetchedInstant ?: return true
            if (today.year.toString() !in cache.years) return true
            return ChronoUnit.DAYS.between(at, now) >= REFRESH_DAYS
        }
    }
}

/** 받기 결과 (화면 안내용). */
data class HolidaySyncResult(val fetchedYears: List<Int>, val count: Int, val error: String?)

/** 인증키로 올해·내년 공휴일을 받아 저장. 일부 해만 성공하면 그 해만 저장하고 오류를 알린다. */
class HolidaySyncer(
    private val repo: HolidayRepository,
    private val api: KasiHolidayApi,
    private val clock: () -> Instant = Instant::now,
    private val today: () -> LocalDate = LocalDate::now,
) {
    fun sync(serviceKey: String): HolidaySyncResult {
        val ok = mutableMapOf<Int, List<Holiday>>()
        var error: String? = null
        for (year in HolidayRepository.yearsToFetch(today())) {
            when (val r = api.fetchYear(serviceKey, year)) {
                is KasiResult.Ok -> {
                    // 올해 자료가 0개면 뭔가 잘못된 것 (키·서버) → 저장하지 않음. 내년은 아직 발표 전일 수 있어 0개면 건너뜀
                    if (r.holidays.isNotEmpty()) ok[year] = r.holidays
                    else if (year == today().year) error = error ?: "${year}년 공휴일을 하나도 받지 못했어요"
                }
                is KasiResult.Error -> error = error ?: r.message
            }
        }
        if (ok.isNotEmpty()) repo.store(ok, clock())
        return HolidaySyncResult(ok.keys.sorted(), ok.values.sumOf { it.size }, error)
    }

    fun syncIfNeeded(serviceKey: String?): HolidaySyncResult? {
        if (serviceKey.isNullOrBlank()) return null
        if (!HolidayRepository.needsRefresh(repo.updates.value, today(), clock())) return null
        return sync(serviceKey)
    }
}

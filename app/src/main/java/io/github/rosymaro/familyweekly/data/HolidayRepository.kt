package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.CustomHoliday
import io.github.rosymaro.familyweekly.domain.Holiday
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HolidayFile(
    val version: Int = 1,
    val holidays: List<Holiday> = emptyList(),
)

/**
 * 기본 공휴일 파일(assets/data/holidays_kr.json) 읽기.
 * 파일 내용은 Phase 4에서 채운다. 파일이 없거나 비어 있으면 공휴일 없음으로 동작.
 */
class HolidayRepository(private val readAsset: () -> String?) {

    val builtIn: List<Holiday> by lazy {
        readAsset()?.let(::parse).orEmpty()
    }

    fun calendar(custom: List<CustomHoliday>): HolidayCalendar = HolidayCalendar(builtIn, custom)

    companion object {
        const val ASSET_PATH = "data/holidays_kr.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): List<Holiday> =
            json.decodeFromString(HolidayFile.serializer(), text).holidays.sortedBy { it.date }
    }
}

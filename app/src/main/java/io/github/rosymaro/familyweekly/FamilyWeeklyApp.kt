package io.github.rosymaro.familyweekly

import android.app.Application
import io.github.rosymaro.familyweekly.data.AppStore
import io.github.rosymaro.familyweekly.data.HolidayRepository

/** 저장소를 하나씩 만들어 화면·위젯·알림에 나눠 준다 (DI 없음). */
class FamilyWeeklyApp : Application() {

    val store: AppStore by lazy { AppStore(filesDir) }

    val holidays: HolidayRepository by lazy {
        HolidayRepository {
            runCatching { assets.open(HolidayRepository.ASSET_PATH).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
}

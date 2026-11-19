package io.github.rosymaro.familyweekly

import android.app.Application
import io.github.rosymaro.familyweekly.data.AppStore
import io.github.rosymaro.familyweekly.data.HolidayRepository
import io.github.rosymaro.familyweekly.theme.ThemePresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 저장소를 하나씩 만들어 화면·위젯·알림에 나눠 준다 (DI 없음). */
class FamilyWeeklyApp : Application() {

    val store: AppStore by lazy { AppStore(filesDir) }

    /** 저장처럼 화면이 닫혀도 끝까지 해야 하는 일. (화면 ViewModel 범위에서 하면 뒤로 가기와 함께 취소될 수 있음) */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val holidays: HolidayRepository by lazy {
        HolidayRepository {
            readAsset(HolidayRepository.ASSET_PATH)
        }
    }

    val presets: ThemePresets by lazy { ThemePresets(::readAsset) }

    private fun readAsset(path: String): String? =
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
}

package io.github.rosymaro.familyweekly

import android.app.Application
import io.github.rosymaro.familyweekly.data.AppStore
import io.github.rosymaro.familyweekly.data.HolidayKeyStore
import io.github.rosymaro.familyweekly.data.HolidayRepository
import io.github.rosymaro.familyweekly.data.HolidaySyncer
import io.github.rosymaro.familyweekly.data.KasiHolidayApi
import io.github.rosymaro.familyweekly.theme.ThemePresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** 저장소를 하나씩 만들어 화면·위젯·알림에 나눠 준다 (DI 없음). */
class FamilyWeeklyApp : Application() {

    val store: AppStore by lazy { AppStore(filesDir) }

    /** 저장처럼 화면이 닫혀도 끝까지 해야 하는 일. (화면 ViewModel 범위에서 하면 뒤로 가기와 함께 취소될 수 있음) */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val holidays: HolidayRepository by lazy {
        HolidayRepository(cacheFile = File(filesDir, HolidayRepository.CACHE_FILE)) {
            readAsset(HolidayRepository.ASSET_PATH)
        }
    }
    val holidayKey: HolidayKeyStore by lazy { HolidayKeyStore(this) }
    val holidaySyncer: HolidaySyncer by lazy { HolidaySyncer(holidays, KasiHolidayApi()) }

    val presets: ThemePresets by lazy { ThemePresets(::readAsset) }

    override fun onCreate() {
        super.onCreate()
        // 인증키가 있으면 한 달에 한 번 공휴일을 새로 받는다 (실패하면 기존 자료 그대로)
        appScope.launch { runCatching { holidaySyncer.syncIfNeeded(holidayKey.key.value) } }
    }

    private fun readAsset(path: String): String? =
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
}

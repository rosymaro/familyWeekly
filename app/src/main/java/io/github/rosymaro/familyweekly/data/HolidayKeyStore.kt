package io.github.rosymaro.familyweekly.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 공휴일 API 인증키. 앱 전용 저장소(SharedPreferences)에만 둔다 — 일정 파일(family_weekly.json)과 분리.
 * 공공데이터포털 무료 키라 암호화는 하지 않는다 (다른 앱은 읽을 수 없는 앱 전용 영역).
 */
class HolidayKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("holiday_sync", Context.MODE_PRIVATE)
    private val _key = MutableStateFlow(prefs.getString(KEY, null)?.takeIf { it.isNotBlank() })
    val key: StateFlow<String?> = _key.asStateFlow()

    fun set(value: String?) {
        val v = value?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply { if (v == null) remove(KEY) else putString(KEY, v) }.apply()
        _key.value = v
    }

    private companion object {
        const val KEY = "serviceKey"
    }
}

package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.Holiday
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** 공휴일 받기 결과. */
sealed interface KasiResult {
    data class Ok(val holidays: List<Holiday>) : KasiResult
    data class Error(val message: String) : KasiResult
}

/**
 * 한국천문연구원 특일 정보 API (공공데이터포털) — 공휴일(대체공휴일·임시공휴일·선거일 포함).
 * 인증키는 사용자가 data.go.kr에서 무료로 발급받아 앱에 넣는다 (저장소·APK에 넣지 않음).
 * HTTP는 HttpURLConnection (라이브러리 추가 없음). 응답 해석은 순수 함수라 JVM 테스트로 검증.
 */
class KasiHolidayApi(private val httpGet: (URL) -> String = ::defaultHttpGet) {

    fun fetchYear(serviceKey: String, year: Int): KasiResult =
        try {
            parse(httpGet(buildUrl(serviceKey, year)), year)
        } catch (e: java.net.UnknownHostException) {
            KasiResult.Error("인터넷에 연결되지 않았어요")
        } catch (e: java.net.SocketTimeoutException) {
            KasiResult.Error("응답이 너무 늦어요. 잠시 뒤 다시 해 주세요")
        } catch (e: Exception) {
            KasiResult.Error("받지 못했어요 (${e.javaClass.simpleName}: ${e.message})")
        }

    companion object {
        const val ENDPOINT = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo"

        /** 활용신청 안내 페이지 (공공데이터포털 "한국천문연구원_특일 정보") */
        const val GUIDE_URL = "https://www.data.go.kr/data/15012690/openapi.do"

        /**
         * 인증키는 포털에 "Encoding"(이미 % 인코딩) / "Decoding" 두 가지로 나온다.
         * %가 들어 있으면 이미 인코딩된 키로 보고 그대로, 아니면 인코딩해서 붙인다.
         */
        fun buildUrl(serviceKey: String, year: Int): URL {
            val key = serviceKey.trim().let { if ('%' in it) it else URLEncoder.encode(it, "UTF-8") }
            return URL("$ENDPOINT?serviceKey=$key&solYear=$year&numOfRows=100&_type=json")
        }

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 응답 본문 → 공휴일. 잘못된 키 등 오류는 XML로 오기도 해서 둘 다 본다. */
        fun parse(body: String, year: Int? = null): KasiResult {
            val text = body.trim()
            if (text.isEmpty()) return KasiResult.Error("빈 응답이에요")
            if (text.startsWith("<")) return KasiResult.Error(xmlError(text))
            val root = try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return KasiResult.Error("응답 형식을 읽지 못했어요: ${text.take(80)}")
            }
            val response = root["response"] as? JsonObject ?: return KasiResult.Error("응답 형식을 읽지 못했어요")
            val header = response["header"] as? JsonObject
            val code = header?.get("resultCode")?.jsonPrimitive?.content
            if (code != null && code != "00") {
                return KasiResult.Error(explain(header["resultMsg"]?.jsonPrimitive?.content ?: code))
            }
            val items = (response["body"] as? JsonObject)?.get("items")
            val list: List<JsonObject> = when (items) {
                is JsonObject -> when (val item = items["item"]) {
                    is JsonArray -> item.mapNotNull { it as? JsonObject }
                    is JsonObject -> listOf(item)
                    else -> emptyList()
                }
                else -> emptyList() // 그해 자료가 없으면 "items": ""
            }
            val holidays = list
                .filter { it.str("isHoliday").equals("Y", ignoreCase = true) }
                .mapNotNull { item ->
                    val date = item.str("locdate")?.let(::parseLocdate) ?: return@mapNotNull null
                    Holiday(date, normalizeName(item.str("dateName").orEmpty()))
                }
                .filter { year == null || it.date.year == year }
            return KasiResult.Ok(mergeSameDay(holidays))
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

        private fun parseLocdate(s: String): LocalDate? = s.trim().takeIf { it.length == 8 }?.let {
            runCatching { LocalDate.of(it.substring(0, 4).toInt(), it.substring(4, 6).toInt(), it.substring(6, 8).toInt()) }.getOrNull()
        }

        /** "1월1일" → "신정". 나머지는 그대로 (예: 대체공휴일, 임시공휴일, 전국동시지방선거). */
        fun normalizeName(name: String): String = when (val n = name.trim()) {
            "1월1일", "1월 1일" -> "신정"
            "" -> "공휴일"
            else -> n
        }

        /** 같은 날 두 공휴일(예: 추석과 개천절)은 이름을 합친다. */
        fun mergeSameDay(list: List<Holiday>): List<Holiday> =
            list.groupBy { it.date }.map { (date, hs) -> Holiday(date, hs.map { it.name }.distinct().joinToString(" · ")) }
                .sortedBy { it.date }

        private fun xmlError(xml: String): String {
            fun tag(name: String) = Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.trim()
            val msg = tag("returnAuthMsg") ?: tag("resultMsg") ?: tag("errMsg") ?: xml.take(80)
            return explain(msg)
        }

        private fun explain(msg: String): String = when {
            msg.contains("SERVICE_KEY_IS_NOT_REGISTERED", true) || msg.contains("SERVICE KEY IS NOT REGISTERED", true) ->
                "인증키가 등록되지 않았어요. 발급 직후라면 1~2시간 뒤에 다시 해 주세요 ($msg)"
            msg.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS", true) -> "오늘 요청 한도를 넘었어요 ($msg)"
            msg.contains("NO_OPENAPI_SERVICE", true) || msg.contains("UNREGISTERED_IP", true) ->
                "이 인증키로 특일 정보 서비스를 쓸 수 없어요. 활용신청을 확인해 주세요 ($msg)"
            else -> "공휴일 서버 오류: $msg"
        }

        fun defaultHttpGet(url: URL): String {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            return try {
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}

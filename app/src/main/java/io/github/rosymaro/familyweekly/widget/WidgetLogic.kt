package io.github.rosymaro.familyweekly.widget

import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.DarkMode
import io.github.rosymaro.familyweekly.domain.WidgetBinding
import io.github.rosymaro.familyweekly.layout.RenderSpec
import kotlin.math.max
import kotlin.math.sqrt

/** 위젯 크기·메모리·연결 계산. Android 의존성 없음 (JVM 테스트). */
object WidgetLogic {

    /** 런처가 크기를 알려 주지 않을 때 (4×3 정도) */
    const val FALLBACK_WIDTH_DP = 300f
    const val FALLBACK_HEIGHT_DP = 220f

    /**
     * AppWidgetManager 옵션의 dp 크기 → 지금 보이는 크기.
     * 세로 화면은 (최소 폭, 최대 높이), 가로 화면은 (최대 폭, 최소 높이)가 실제 크기다 (안드로이드 문서 규칙).
     */
    fun currentSizeDp(minW: Int, minH: Int, maxW: Int, maxH: Int, landscape: Boolean): Pair<Float, Float> {
        val w = if (landscape) maxW else minW
        val h = if (landscape) minH else maxH
        return (if (w > 0) w.toFloat() else FALLBACK_WIDTH_DP) to (if (h > 0) h.toFloat() else FALLBACK_HEIGHT_DP)
    }

    /**
     * RemoteViews 비트맵 한도(화면 가로×세로×4바이트×1.5) 안에 들어오게 밀도를 낮춘다.
     * 한도의 [safety] 비율까지만 쓰고, 그래도 크면 해상도를 줄인다 (모양·글자 크기 비율은 그대로).
     */
    fun renderSpec(
        widthDp: Float,
        heightDp: Float,
        density: Float,
        fontScale: Float,
        maxBitmapBytes: Long,
        systemCornerRadiusDp: Float? = null,
        safety: Float = 0.8f,
    ): RenderSpec {
        val budget = (maxBitmapBytes * safety).toLong().coerceAtLeast(64 * 1024)
        val want = widthDp * density * heightDp * density * 4
        val d = if (want <= budget) density else density * sqrt(budget / want).toFloat()
        return RenderSpec(
            widthPx = max(1, (widthDp * d).toInt()),
            heightPx = max(1, (heightDp * d).toInt()),
            density = d,
            fontScale = fontScale,
            systemCornerRadiusDp = systemCornerRadiusDp,
        )
    }

    /** 화면 크기로 RemoteViews 비트맵 한도 (안드로이드 AppWidgetService 규칙과 같음). */
    fun maxBitmapBytes(screenWidthPx: Int, screenHeightPx: Int): Long =
        (screenWidthPx.toLong() * screenHeightPx * 4 * 3) / 2

    fun bytes(spec: RenderSpec): Long = spec.widthPx.toLong() * spec.heightPx * 4

    /** 위젯에 쓸 테마: 위젯이 고른 프리셋, 없으면 스케줄 테마. */
    fun themeIdFor(data: AppData, binding: WidgetBinding?): String? =
        binding?.themeId ?: binding?.scheduleId?.let { data.schedule(it)?.themeId }

    fun isDark(mode: DarkMode, systemDark: Boolean): Boolean = when (mode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    /** 위젯 연결 저장 (같은 위젯 id면 바꿈). */
    fun bind(data: AppData, binding: WidgetBinding): AppData =
        data.copy(widgets = data.widgets.filterNot { it.appWidgetId == binding.appWidgetId } + binding)

    /** 홈 화면에서 지운 위젯의 연결 정리. */
    fun remove(data: AppData, appWidgetIds: Collection<Int>): AppData =
        data.copy(widgets = data.widgets.filterNot { it.appWidgetId in appWidgetIds })

    /** 런처에 더 이상 없는 위젯의 연결 정리 (재설치·런처 초기화 대비). */
    fun keepOnly(data: AppData, liveIds: Collection<Int>): AppData =
        data.copy(widgets = data.widgets.filter { it.appWidgetId in liveIds })

    fun binding(data: AppData, appWidgetId: Int): WidgetBinding? = data.widgets.firstOrNull { it.appWidgetId == appWidgetId }

    /** 이 스케줄을 보여 주는 위젯 수 (홈 카드 표시). */
    fun countFor(data: AppData, scheduleId: String): Int = data.widgets.count { it.scheduleId == scheduleId }

    /**
     * 다시 그릴지 판단할 때 쓰는 "지금" 값: 오늘이 보이는 요일이고 시간선이 표시 범위 안이면 분,
     * 아니면 -1 (밤처럼 시간선이 안 보일 때는 5분마다 다시 그리지 않아도 됨).
     */
    fun nowKey(week: io.github.rosymaro.familyweekly.domain.ResolvedWeek, now: java.time.LocalDateTime): Int {
        if (week.days.none { it.date == now.toLocalDate() }) return -1
        val m = io.github.rosymaro.familyweekly.domain.TimeStep.fromLocalTime(now.toLocalTime())
        return if (m in week.displayStartMin..week.displayEndMin) m else -1
    }

    /**
     * 위젯 그림 캐시 키: 같은 키면 그림이 같으므로 런처로 다시 보내지 않는다 (배터리·런처 부담).
     * 데이터, 공휴일, 크기, 다크, 날짜(오늘 강조·새 주), 지금 시각(시간선이 보일 때만)을 넣는다.
     */
    fun renderKey(vararg parts: Any?): Int = parts.toList().hashCode()

    /** 현재 시간선 갱신 간격 */
    const val TICK_MINUTES = 5L

    /** 다음 5분 정각(…:00, :05, :10 …)까지 남은 밀리초. */
    fun millisToNextTick(nowEpochMillis: Long): Long {
        val step = TICK_MINUTES * 60_000
        val next = (nowEpochMillis / step + 1) * step
        return next - nowEpochMillis
    }
}

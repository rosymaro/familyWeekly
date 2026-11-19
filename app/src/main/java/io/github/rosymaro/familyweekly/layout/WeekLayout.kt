package io.github.rosymaro.familyweekly.layout

import io.github.rosymaro.familyweekly.domain.DayInfo
import io.github.rosymaro.familyweekly.domain.EventOccurrence
import io.github.rosymaro.familyweekly.domain.Occurrence
import io.github.rosymaro.familyweekly.domain.ResolvedWeek
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.domain.Vacation
import io.github.rosymaro.familyweekly.theme.AllDayPlacement
import io.github.rosymaro.familyweekly.theme.FontSpec
import io.github.rosymaro.familyweekly.theme.LabelAlign
import io.github.rosymaro.familyweekly.theme.NowSpan
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.Visibility
import io.github.rosymaro.familyweekly.theme.WidgetTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** px 단위 사각형 (android.graphics.RectF 대신, JVM 테스트용). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
    fun inset(dx: Float, dy: Float) = Box(left + dx, top + dy, right - dx, bottom - dy)
}

/** 그릴 크기. 위젯이면 AppWidgetManager 옵션에서, 앱 미리보기면 화면에서. */
data class RenderSpec(
    val widthPx: Int,
    val heightPx: Int,
    /** px / dp */
    val density: Float,
    /** 시스템 글자 크기 배율 (sp = dp × fontScale) */
    val fontScale: Float = 1f,
    /** Android 12+ 시스템 위젯 모서리 반경 dp. 없으면 테마 값. */
    val systemCornerRadiusDp: Float? = null,
) {
    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density
    val sizeClass: SizeClass get() = SizeClass.of(widthDp, heightDp)
}

data class HeaderLayout(val box: Box, val title: String, val subtitle: String?)

data class DayHeaderCell(
    val box: Box,
    val date: LocalDate,
    /** 줄바꿈(\n) 가능 */
    val label: String,
    val isToday: Boolean,
    val holidayName: String?,
) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
}

data class HourLabel(val y: Float, val text: String)

enum class TintKind { ALTERNATE, WEEKEND, HOLIDAY, VACATION, TODAY }

data class ColumnTint(val box: Box, val kind: TintKind)

data class VacationBand(val box: Box, val vacation: Vacation)

data class BlockLayout(
    val box: Box,
    val occurrence: Occurrence,
    val lane: Lane,
    /** 표시 범위 위/아래로 잘렸는지 */
    val clippedTop: Boolean,
    val clippedBottom: Boolean,
    val showTitle: Boolean,
    val titleMaxLines: Int,
    val showTime: Boolean,
    val showPlace: Boolean,
)

data class PostItLayout(
    /** 회전 전 사각형 */
    val box: Box,
    val rotationDeg: Float,
    val event: EventOccurrence,
    val stackIndex: Int,
    val showTime: Boolean,
)

data class NowLineLayout(val y: Float, val startX: Float, val endX: Float, val minuteOfDay: Int)

data class WeekLayoutResult(
    val spec: RenderSpec,
    val sizeClass: SizeClass,
    val canvas: Box,
    val cornerRadius: Float,
    val header: HeaderLayout?,
    val dayHeaders: List<DayHeaderCell>,
    val vacationBands: List<VacationBand>,
    val allDayStrip: Box?,
    val timeAxis: Box,
    val hourLabels: List<HourLabel>,
    val grid: Box,
    val columns: List<Box>,
    val hourLines: List<Float>,
    val halfHourLines: List<Float>,
    val columnDividers: List<Float>,
    val columnTints: List<ColumnTint>,
    val blocks: List<BlockLayout>,
    val postIts: List<PostItLayout>,
    val nowLine: NowLineLayout?,
    val emptyState: Box?,
    val displayStartMin: Int,
    val displayEndMin: Int,
    val pxPerMinute: Float,
) {
    /** 분 → y. 그리드 위쪽 기준. */
    fun yOf(minute: Int): Float = grid.top + (minute - displayStartMin) * pxPerMinute
}

/**
 * ResolvedWeek + 테마 + 크기 → 그릴 위치 전부 (px). Android 의존성 없음.
 * 그리기(WeekPainter)는 이 결과를 그대로 칠하기만 한다.
 */
object WeekLayout {

    private val KOREAN = Locale.KOREAN

    /** 글자 한 줄 높이 배율 */
    const val LINE_HEIGHT = 1.25f

    fun compute(
        week: ResolvedWeek,
        theme: WidgetTheme,
        spec: RenderSpec,
        now: LocalDateTime? = null,
        sizeClass: SizeClass = spec.sizeClass,
    ): WeekLayoutResult {
        val d = spec.density
        fun dp(v: Float) = v * d
        fun lineH(f: FontSpec) = f.size * d * spec.fontScale * theme.scale * LINE_HEIGHT

        val canvas = Box(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat())
        val cornerRadius = dp(
            if (theme.canvas.matchSystem && spec.systemCornerRadiusDp != null) spec.systemCornerRadiusDp
            else theme.canvas.cornerRadius,
        )
        val pad = theme.canvas.padding
        val content = Box(canvas.left + dp(pad.start), canvas.top + dp(pad.top), canvas.right - dp(pad.end), canvas.bottom - dp(pad.bottom))

        var y = content.top

        // 머리말
        val header = if (theme.header.visible) {
            val h = dp(theme.header.height)
            val title = buildString {
                if (theme.header.showEmoji && !week.schedule.emoji.isNullOrBlank()) append(week.schedule.emoji).append(' ')
                append(week.schedule.name)
            }
            val subtitle = if (theme.header.subtitle.visible) subtitleText(theme.header.subtitle.format, week.days) else null
            HeaderLayout(Box(content.left, y, content.right, y + h), title, subtitle).also { y += h }
        } else null

        // 가로 배치: 시간 축 + 열
        val axisW = dp(theme.timeAxis.width)
        val colsLeft = content.left + axisW
        val n = week.days.size
        val gap = dp(theme.grid.columnGap)
        val colW = ((content.right - colsLeft) - gap * (n - 1)) / n
        fun colX(i: Int) = colsLeft + i * (colW + gap)

        // 요일 머리말
        val dayH = dp(theme.dayHeader.height)
        val dayHeaders = week.days.mapIndexed { i, day ->
            DayHeaderCell(
                box = Box(colX(i), y, colX(i) + colW, y + dayH),
                date = day.date,
                label = formatDate(theme.dayHeader.labelFormat, day.date),
                isToday = day.isToday,
                holidayName = day.holiday?.name,
            )
        }
        y += dayH

        // 휴가 띠 (연속한 날은 하나로)
        val band = theme.vacation.headerBand
        val vacationBands = mutableListOf<VacationBand>()
        if (band.visible && week.days.any { it.vacations.isNotEmpty() }) {
            val bandH = dp(band.height)
            val vacations = week.days.flatMap { it.vacations }.distinctBy { it.id }
            for (v in vacations) {
                runsOf(week.days) { v in it.vacations }.forEach { (from, to) ->
                    vacationBands += VacationBand(Box(colX(from), y, colX(to) + colW, y + bandH), v)
                }
            }
            y += bandH
        }

        // 하루 종일 포스트잇 줄
        val allDayEvents = week.events.filter { it.event.allDay }
        val allDayH = dp(theme.postIt.allDayHeight)
        val allDayStrip = if (allDayEvents.isNotEmpty() && theme.postIt.allDayPlacement == AllDayPlacement.HEADER_STRIP) {
            Box(colsLeft, y, content.right, y + allDayH).also { y = it.bottom + dp(2f) }
        } else null

        // 그리드
        val start = week.displayStartMin
        val end = week.displayEndMin
        val range = (end - start).coerceAtLeast(TimeStep.STEP_MIN)
        val available = (content.bottom - y).coerceAtLeast(1f)
        val pxPerMin = theme.grid.hourHeight?.let { dp(it) / 60f } ?: (available / range)
        val gridBottom = min(content.bottom, y + range * pxPerMin)
        val grid = Box(colsLeft, y, content.right, gridBottom)
        val timeAxis = Box(content.left, y, colsLeft, gridBottom)
        fun yOf(m: Int) = grid.top + (m - start) * pxPerMin
        val columns = week.days.indices.map { Box(colX(it), grid.top, colX(it) + colW, grid.bottom) }

        // 시간 선 / 라벨 (선은 1시간마다, 라벨은 N시간마다)
        val firstHour = TimeStep.ceilToHour(start)
        val hourMinutes = (firstHour..end step 60).toList()
        val hourLines = hourMinutes.map(::yOf)
        val halfHourLines = (TimeStep.floorToHour(start) + 30..end step 60).filter { it > start }.map(::yOf)
        val every = theme.timeAxis.showEveryNHours.coerceAtLeast(1)
        val labelMinutes = hourMinutes.filterIndexed { i, _ -> i % every == 0 }.filter {
            theme.timeAxis.labelAlign == LabelAlign.CENTER_ON_LINE || it < end
        }
        val hourLabels = labelMinutes.map { HourLabel(yOf(it), formatHour(theme.timeAxis.labelFormat, it)) }
        val columnDividers = (1 until n).map { colX(it) - gap / 2 }

        // 열 틴트 (그리는 순서: 줄무늬 → 주말 → 공휴일 → 휴가 → 오늘)
        val tints = mutableListOf<ColumnTint>()
        week.days.forEachIndexed { i, day ->
            val box = columns[i]
            if (theme.grid.alternateColumnTint != null && i % 2 == 1) tints += ColumnTint(box, TintKind.ALTERNATE)
            if (theme.grid.weekendColumnTint != null && day.date.dayOfWeek in WEEKEND) tints += ColumnTint(box, TintKind.WEEKEND)
            if (theme.grid.holidayColumnTint != null && day.holiday != null) tints += ColumnTint(box, TintKind.HOLIDAY)
            if (theme.vacation.columnTint != null && day.vacations.isNotEmpty()) tints += ColumnTint(box, TintKind.VACATION)
            if (day.isToday && (theme.today.columnTint != null || theme.today.columnBorder != null)) tints += ColumnTint(box, TintKind.TODAY)
        }

        // 블록
        val bt = theme.block
        val titleH = lineH(bt.title)
        val timeH = lineH(bt.time)
        val blocks = mutableListOf<BlockLayout>()
        week.days.forEachIndexed { i, day ->
            val occs = week.occurrences.filter { it.date == day.date }
            val lanes = LaneAssigner.assign(occs.map { it.startMin to it.endMin })
            occs.forEachIndexed { k, occ ->
                if (occ.endMin <= start || occ.startMin >= end) return@forEachIndexed
                val lane = lanes[k]
                val laneW = colW / lane.count
                val inset = dp(bt.inset)
                val top = yOf(max(occ.startMin, start))
                val bottom = yOf(min(occ.endMin, end))
                val box = Box(colX(i) + lane.index * laneW + inset, top + inset, colX(i) + (lane.index + 1) * laneW - inset, bottom - inset)
                val inner = box.height - dp(bt.padding.top) - dp(bt.padding.bottom)
                val showTitle = box.height >= dp(bt.minTextHeight)
                val showTime = showTitle && when (bt.showTime) {
                    Visibility.ALWAYS -> true
                    Visibility.IF_FITS -> inner >= titleH + timeH
                    Visibility.NEVER -> false
                }
                val usedByTime = if (showTime) timeH else 0f
                val showPlace = showTitle && !occ.block.place.isNullOrBlank() && when (bt.showPlace) {
                    Visibility.ALWAYS -> true
                    Visibility.IF_FITS -> inner >= titleH + usedByTime + timeH
                    Visibility.NEVER -> false
                }
                val usedByPlace = if (showPlace) timeH else 0f
                val lines = floor((inner - usedByTime - usedByPlace) / titleH).toInt().coerceIn(1, bt.titleMaxLines.coerceAtLeast(1))
                blocks += BlockLayout(
                    box = box,
                    occurrence = occ,
                    lane = lane,
                    clippedTop = occ.startMin < start,
                    clippedBottom = occ.endMin > end,
                    showTitle = showTitle,
                    titleMaxLines = lines,
                    showTime = showTime,
                    showPlace = showPlace,
                )
            }
        }

        // 포스트잇
        val pt = theme.postIt
        val postTitleH = lineH(pt.font)
        val postTimeH = lineH(pt.time)
        val postIts = mutableListOf<PostItLayout>()
        week.days.forEachIndexed { i, day ->
            val colLeft = colX(i)
            val w = colW * pt.widthRatio.coerceIn(0.2f, 1f)
            val baseX = colLeft + (colW - w) / 2 + dp(pt.offset.x)
            val stack = dp(pt.multiDayStack)

            // 하루 종일
            week.events.filter { it.event.allDay && it.event.date == day.date }.forEachIndexed { k, ev ->
                val top = (allDayStrip?.top ?: grid.top) + dp(pt.offset.y) * 0.5f + k * stack
                postIts += PostItLayout(
                    box = Box(baseX + k * stack, top, baseX + w + k * stack, top + allDayH),
                    rotationDeg = PostItPlacer.rotationFor(ev.event.id, pt.rotation),
                    event = ev,
                    stackIndex = k,
                    showTime = false,
                )
            }

            // 시각 있는 일정
            val timed = week.events.filter { !it.event.allDay && it.event.date == day.date }
            val stackIdx = PostItPlacer.stackIndices(timed.map { it.event.startMin to it.event.endMin })
            timed.forEachIndexed { k, ev ->
                val e = ev.event
                if (e.endMin <= start || e.startMin >= end) return@forEachIndexed
                val s = stackIdx[k]
                val top = yOf(max(e.startMin, start)) + dp(pt.offset.y) + s * stack
                val h = max(yOf(min(e.endMin, end)) - yOf(max(e.startMin, start)), dp(pt.minHeight))
                val inner = h - dp(pt.padding.top) - dp(pt.padding.bottom)
                postIts += PostItLayout(
                    box = Box(baseX + s * stack, top, baseX + w + s * stack, top + h),
                    rotationDeg = PostItPlacer.rotationFor(e.id, pt.rotation),
                    event = ev,
                    stackIndex = s,
                    showTime = when (pt.showTime) {
                        Visibility.ALWAYS -> true
                        Visibility.IF_FITS -> inner >= postTitleH + postTimeH
                        Visibility.NEVER -> false
                    },
                )
            }
        }

        // 현재 시간선
        val nowLine = now?.let {
            if (!theme.nowLine.visible) return@let null
            val idx = week.days.indexOfFirst { day -> day.date == it.toLocalDate() }
            val minute = TimeStep.fromLocalTime(it.toLocalTime())
            if (idx < 0 || minute < start || minute > end) return@let null
            when (theme.nowLine.span) {
                NowSpan.TODAY_COLUMN -> NowLineLayout(yOf(minute), colX(idx), colX(idx) + colW, minute)
                NowSpan.ALL_COLUMNS -> NowLineLayout(yOf(minute), grid.left, grid.right, minute)
            }
        }

        val emptyState = if (week.occurrences.isEmpty() && week.events.isEmpty()) grid else null

        return WeekLayoutResult(
            spec = spec,
            sizeClass = sizeClass,
            canvas = canvas,
            cornerRadius = cornerRadius,
            header = header,
            dayHeaders = dayHeaders,
            vacationBands = vacationBands,
            allDayStrip = allDayStrip,
            timeAxis = timeAxis,
            hourLabels = hourLabels,
            grid = grid,
            columns = columns,
            hourLines = hourLines,
            halfHourLines = halfHourLines,
            columnDividers = columnDividers,
            columnTints = tints,
            blocks = blocks,
            postIts = postIts,
            nowLine = nowLine,
            emptyState = emptyState,
            displayStartMin = start,
            displayEndMin = end,
            pxPerMinute = pxPerMin,
        )
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** 조건이 이어서 참인 구간 (시작 index, 끝 index 포함). */
    private fun runsOf(days: List<DayInfo>, pred: (DayInfo) -> Boolean): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var startIdx = -1
        days.forEachIndexed { i, d ->
            if (pred(d)) {
                if (startIdx < 0) startIdx = i
            } else if (startIdx >= 0) {
                runs += startIdx to i - 1
                startIdx = -1
            }
        }
        if (startIdx >= 0) runs += startIdx to days.lastIndex
        return runs
    }

    /** "M월 d일 ~ M월 d일": ~ 왼쪽은 첫날, 오른쪽은 마지막 날. 없으면 첫날로. 패턴이 틀리면 그대로 보여 줌. */
    fun subtitleText(format: String, days: List<DayInfo>): String {
        if (days.isEmpty()) return ""
        val first = days.first().date
        val last = days.last().date
        val parts = format.split("~", limit = 2)
        return if (parts.size == 2) {
            formatDate(parts[0].trim(), first) + " ~ " + formatDate(parts[1].trim(), last)
        } else formatDate(format, first)
    }

    fun formatDate(pattern: String, date: LocalDate): String =
        runCatching { DateTimeFormatter.ofPattern(pattern, KOREAN).format(date) }.getOrDefault(pattern)

    /** 시간 축 라벨. 1440(24:00)은 "24". */
    fun formatHour(pattern: String, minute: Int): String {
        if (minute >= TimeStep.MINUTES_PER_DAY) return if (pattern.contains("mm")) "24:00" else "24"
        return runCatching {
            DateTimeFormatter.ofPattern(pattern, KOREAN).format(TimeStep.toLocalTime(minute))
        }.getOrDefault(TimeStep.format(minute))
    }

    /** 블록/포스트잇 시각 글자. "H:mm" 또는 "H:mm–H:mm". */
    fun formatTimeRange(pattern: String, startMin: Int, endMin: Int): String {
        val sep = listOf("–", "~", "-").firstOrNull { pattern.contains(it) }
        return if (sep != null) {
            val (a, b) = pattern.split(sep, limit = 2).map { it.trim() }
            formatHourMinute(a, startMin) + sep + formatHourMinute(b, endMin)
        } else formatHourMinute(pattern, startMin)
    }

    private fun formatHourMinute(pattern: String, minute: Int): String =
        if (minute >= TimeStep.MINUTES_PER_DAY) "24:00"
        else runCatching { DateTimeFormatter.ofPattern(pattern, KOREAN).format(TimeStep.toLocalTime(minute)) }
            .getOrDefault(TimeStep.format(minute))
}

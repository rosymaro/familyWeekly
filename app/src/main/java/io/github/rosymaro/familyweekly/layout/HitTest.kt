package io.github.rosymaro.familyweekly.layout

import io.github.rosymaro.familyweekly.domain.TimeStep
import kotlin.math.roundToInt

/** 드래그·탭 좌표 → 블록 / 요일 / 시각. 주간 편집 화면이 쓴다. 순수 함수. */
object HitTest {

    /** 그 점에 있는 블록 (겹치면 나중에 그린 것). */
    fun blockAt(l: WeekLayoutResult, x: Float, y: Float): BlockLayout? =
        l.blocks.lastOrNull { it.box.contains(x, y) }

    /** 그 점에 있는 포스트잇 (회전은 무시하고 사각형으로, 약간 넉넉하게). */
    fun postItAt(l: WeekLayoutResult, x: Float, y: Float, slopPx: Float = 0f): PostItLayout? =
        l.postIts.lastOrNull { it.box.inset(-slopPx, -slopPx).contains(x, y) }

    /** 열 번호. 열 밖(시간 축, 간격)이면 null. */
    fun dayIndexAt(l: WeekLayoutResult, x: Float): Int? =
        l.columns.indexOfFirst { x >= it.left && x < it.right }.takeIf { it >= 0 }

    /** 가장 가까운 열 (드래그 중 열 사이 간격에 있어도 가까운 쪽). */
    fun nearestDayIndex(l: WeekLayoutResult, x: Float): Int =
        l.columns.indices.minByOrNull { kotlin.math.abs(l.columns[it].centerX - x) } ?: 0

    /** y → 분 (5분 스냅, 표시 범위 안으로). 그리드 밖이면 null. */
    fun minuteAt(l: WeekLayoutResult, y: Float): Int? {
        if (y < l.grid.top || y > l.grid.bottom) return null
        val raw = l.displayStartMin + (y - l.grid.top) / l.pxPerMinute
        return TimeStep.snap(raw.toDouble()).coerceIn(l.displayStartMin, l.displayEndMin)
    }

    /** 빈 칸 길게 누르기: 새 블록 위치 (요일 번호, 시작 분). 시작은 5분 스냅, 1시간이 표시 범위 안에 들어가게. */
    fun newBlockAt(l: WeekLayoutResult, x: Float, y: Float, durationMin: Int = 60): Pair<Int, Int>? {
        val day = dayIndexAt(l, x) ?: return null
        val minute = minuteAt(l, y) ?: return null
        val start = minute.coerceAtMost((l.displayEndMin - durationMin).coerceAtLeast(l.displayStartMin))
        return day to TimeStep.snap(start)
    }

    /**
     * 드래그 중 놓일 위치.
     * - 요일: 블록 가운데를 dx만큼 옮겼을 때 가장 가까운 열
     * - 시작: 원래 시작 + dy(분), 5분 스냅, 표시 범위 안 (길이가 범위보다 길면 0..하루 안)
     */
    fun dragTarget(
        l: WeekLayoutResult,
        origin: BlockLayout,
        dxPx: Float,
        dyPx: Float,
    ): DragTarget {
        val occ = origin.occurrence
        val duration = occ.endMin - occ.startMin
        val dayIndex = nearestDayIndex(l, origin.box.centerX + dxPx)
        val deltaMin = (dyPx / l.pxPerMinute).roundToInt()
        val (lo, hi) = if (duration <= l.displayEndMin - l.displayStartMin) {
            l.displayStartMin to l.displayEndMin - duration
        } else 0 to TimeStep.MINUTES_PER_DAY - duration
        val start = TimeStep.snap(occ.startMin + deltaMin).coerceIn(lo, hi)
        return DragTarget(dayIndex, start, start + duration)
    }

    /** 놓일 위치의 사각형 (미리보기 그림자용, lane 무시하고 열 전체 폭). */
    fun targetBox(l: WeekLayoutResult, t: DragTarget): Box {
        val col = l.columns[t.dayIndex]
        return Box(col.left, l.yOf(t.startMin), col.right, l.yOf(t.endMin))
    }

    private fun Box.contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}

data class DragTarget(val dayIndex: Int, val startMin: Int, val endMin: Int)

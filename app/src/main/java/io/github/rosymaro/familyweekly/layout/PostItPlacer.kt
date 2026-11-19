package io.github.rosymaro.familyweekly.layout

import io.github.rosymaro.familyweekly.theme.FloatRange

object PostItPlacer {

    /** 일정 id로 정한 기울기. 같은 id면 언제나 같은 각도 (위젯을 다시 그려도 흔들리지 않음). */
    fun rotationFor(id: String, range: FloatRange): Float {
        if (range.max <= range.min) return range.min
        val f = (fnv1a(id) ushr 8).toFloat() / (1 shl 24).toFloat() // 0..1
        return range.min + (range.max - range.min) * f
    }

    /**
     * 같은 날 시간대가 겹치는 포스트잇의 계단 순서 (0, 1, 2 ...). 입력 순서 그대로 돌려준다.
     * 앞의 것과 겹치면 하나씩 늘어나고, 겹치지 않으면 0부터 다시.
     */
    fun stackIndices(spans: List<Pair<Int, Int>>): List<Int> {
        val order = spans.indices.sortedWith(compareBy({ spans[it].first }, { spans[it].second }))
        val result = IntArray(spans.size)
        var clusterEnd = Int.MIN_VALUE
        var n = 0
        for (i in order) {
            val (s, e) = spans[i]
            n = if (s < clusterEnd) n + 1 else 0
            clusterEnd = if (n == 0) e else maxOf(clusterEnd, e)
            result[i] = n
        }
        return result.toList()
    }

    private fun fnv1a(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in s) {
            h = h xor ch.code
            h *= 0x01000193
        }
        return h
    }
}

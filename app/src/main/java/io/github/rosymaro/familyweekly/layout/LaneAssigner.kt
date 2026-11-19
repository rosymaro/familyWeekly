package io.github.rosymaro.familyweekly.layout

/** 같은 요일에 시간이 겹치는 블록을 나란히 놓기 위한 칸 번호. */
data class Lane(val index: Int, val count: Int)

object LaneAssigner {

    /**
     * [spans] (start, end)마다 lane을 정한다. 결과는 입력 순서 그대로.
     * 서로 이어서 겹치는 묶음(cluster)마다 필요한 lane 수를 따로 센다
     * → 겹치지 않는 블록은 폭을 다 쓴다. 끝과 시작이 같으면(16:00/16:00) 겹치지 않음.
     */
    fun assign(spans: List<Pair<Int, Int>>): List<Lane> {
        if (spans.isEmpty()) return emptyList()
        val order = spans.indices.sortedWith(compareBy({ spans[it].first }, { -spans[it].second }))
        val laneOf = IntArray(spans.size)
        val countOf = IntArray(spans.size)

        var cluster = mutableListOf<Int>()
        var clusterEnd = Int.MIN_VALUE
        val laneEnds = mutableListOf<Int>()

        fun closeCluster() {
            val count = laneEnds.size
            cluster.forEach { countOf[it] = count }
            cluster = mutableListOf()
            laneEnds.clear()
        }

        for (i in order) {
            val (start, end) = spans[i]
            if (cluster.isNotEmpty() && start >= clusterEnd) closeCluster()
            val free = laneEnds.indexOfFirst { it <= start }
            val lane = if (free >= 0) free else laneEnds.size.also { laneEnds.add(0) }
            laneEnds[lane] = end
            laneOf[i] = lane
            cluster += i
            clusterEnd = if (cluster.size == 1) end else maxOf(clusterEnd, end)
        }
        closeCluster()
        return spans.indices.map { Lane(laneOf[it], countOf[it]) }
    }
}

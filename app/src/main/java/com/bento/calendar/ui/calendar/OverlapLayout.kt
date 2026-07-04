package com.bento.calendar.ui.calendar

/**
 * Column assignment for overlapping timeline events (the classic calendar
 * collision layout): events that overlap in time are grouped into clusters,
 * each cluster divides the available width into as many columns as it needs,
 * and every event in the cluster is told its column and the cluster's total —
 * so concurrent events render side by side instead of stacking.
 */
data class Positioned<T>(val item: T, val col: Int, val cols: Int)

/**
 * [startOf]/[endOf] give each event's interval in minutes (already clipped to
 * the visible grid). Events touching end-to-start do NOT overlap. Zero-length
 * intervals are treated as 1 minute so they still claim a column.
 */
fun <T> layoutOverlaps(
    items: List<T>,
    startOf: (T) -> Int,
    endOf: (T) -> Int,
): List<Positioned<T>> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedWith(
        compareBy({ startOf(it) }, { -endOf(it) }),
    )

    val out = ArrayList<Positioned<T>>(sorted.size)
    // Current cluster state:
    var cluster = ArrayList<Pair<T, Int>>() // item -> column
    var columnEnds = ArrayList<Int>() // per-column last end time
    var clusterMaxEnd = Int.MIN_VALUE

    fun flushCluster() {
        val cols = columnEnds.size
        cluster.forEach { (item, col) -> out.add(Positioned(item, col, cols)) }
        cluster = ArrayList()
        columnEnds = ArrayList()
        clusterMaxEnd = Int.MIN_VALUE
    }

    for (item in sorted) {
        val s = startOf(item)
        val e = maxOf(endOf(item), s + 1)
        if (cluster.isNotEmpty() && s >= clusterMaxEnd) {
            // No overlap with anything active — the cluster is closed.
            flushCluster()
        }
        // Lowest-index column that is free at this start time.
        var col = columnEnds.indexOfFirst { it <= s }
        if (col == -1) {
            columnEnds.add(e)
            col = columnEnds.size - 1
        } else {
            columnEnds[col] = e
        }
        cluster.add(item to col)
        clusterMaxEnd = maxOf(clusterMaxEnd, e)
    }
    flushCluster()
    return out
}

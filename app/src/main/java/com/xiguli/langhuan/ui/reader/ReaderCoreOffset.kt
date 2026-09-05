package com.xiguli.langhuan.ui.reader

/**
 * Callable helper owned by ReaderCore. It deliberately is not a top-level function so it does not
 * participate in overload resolution with the retired ReaderExperience file-private helper.
 */
internal object pageForRawTextOffset {
    operator fun invoke(offsets: List<Int>, offset: Int): Int {
        if (offsets.isEmpty()) return 0
        val target = offset.coerceAtLeast(0)
        var low = 0
        var high = offsets.lastIndex
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets[mid] <= target) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer.coerceIn(0, offsets.lastIndex)
    }
}

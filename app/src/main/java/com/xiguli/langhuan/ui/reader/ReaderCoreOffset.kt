package com.xiguli.langhuan.ui

/**
 * Page lookup owned by the new reader core.
 *
 * ReaderExperience keeps a file-private implementation for the retired reader, but the new
 * runtime must not reach into that file. Offsets are sorted page-start offsets; the returned page
 * is the last page whose start is not after [offset].
 */
internal fun pageForRawTextOffset(offsets: List<Int>, offset: Int): Int {
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

package com.xiguli.langhuan.ui

/**
 * Home navigation (V104): three horizontally swiped pages that follow the finger 1:1 —
 * 书架列表 on the LEFT, 正在阅读 in the centre (home) and 我的 on the RIGHT. Dragging right from
 * 正在阅读 reveals 书架列表, dragging left reveals 我的. The pages stay attached (no parallax, dim
 * or edge shadow — measured from the reference recording); back from either side returns to the
 * centre, and the predictive back gesture drags the pager with the finger.
 *
 * Pure functions here are unit-tested; the composable only feeds them the pager position.
 */
internal object ShelfPagerV102 {
    const val LIST_PAGE = 0
    const val SHELF_PAGE = 1
    const val MINE_PAGE = 2
    const val PAGE_COUNT = 3

    /** How much of 书架列表 is revealed, 0 (on 正在阅读) … 1 (list fully open). */
    fun listReveal(currentPage: Int, currentPageOffsetFraction: Float): Float =
        (SHELF_PAGE - (currentPage + currentPageOffsetFraction)).coerceIn(0f, 1f)

    /** How much of 我的 is revealed, 0 (on 正在阅读) … 1 (我的 fully open). */
    fun mineReveal(currentPage: Int, currentPageOffsetFraction: Float): Float =
        (currentPage + currentPageOffsetFraction - SHELF_PAGE).coerceIn(0f, 1f)

    /**
     * Fraction of a page the pager is dragged towards the centre for a predictive back gesture at
     * [progress] (0…1). Attached 1:1, stopping just short of the centre so the commit animation
     * always has a little travel left.
     */
    fun backDrag(progress: Float): Float = progress.coerceIn(0f, 1f) * 0.9f

    /** Scroll direction (in pages) of a back gesture that starts on [page]: +1 from the list, −1 from 我的. */
    fun backDirection(page: Int): Int = when (page) {
        LIST_PAGE -> 1
        MINE_PAGE -> -1
        else -> 0
    }

    /** Where system back goes: from either side page back to the centre, otherwise not handled here. */
    fun backTarget(settledPage: Int): Int? = if (settledPage == LIST_PAGE || settledPage == MINE_PAGE) SHELF_PAGE else null

    /** Page a saved legacy route opens on ("shelves" was the old full-screen 书架列表, "mine" the pushed 我的). */
    fun pageForRoute(route: String): Int = when (route) {
        "shelves" -> LIST_PAGE
        "mine" -> MINE_PAGE
        else -> SHELF_PAGE
    }
}

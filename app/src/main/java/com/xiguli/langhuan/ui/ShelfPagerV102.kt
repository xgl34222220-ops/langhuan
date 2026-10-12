package com.xiguli.langhuan.ui

/**
 * V102 shelf navigation: 书架列表 is a page to the LEFT of 正在阅读. The user drags right to
 * reveal the list and left to return; the pager tracks the finger, so the shelf's left column
 * stays visible at the right edge mid-swipe, like the reference. V103: the two pages stay attached
 * (no parallax, dim or edge shadow — measured from the reference recording).
 *
 * Pure functions here are unit-tested; the composable only feeds them the pager position.
 */
internal object ShelfPagerV102 {
    const val LIST_PAGE = 0
    const val SHELF_PAGE = 1
    const val PAGE_COUNT = 2

    /** How much of 书架列表 is revealed, 0 (on 正在阅读) … 1 (list fully open). */
    fun listReveal(currentPage: Int, currentPageOffsetFraction: Float): Float =
        (SHELF_PAGE - (currentPage + currentPageOffsetFraction)).coerceIn(0f, 1f)

    /**
     * V103: fraction of a page the pager is dragged towards the shelf for a predictive back
     * gesture at [progress] (0…1). Attached 1:1, stopping just short of the shelf so the commit
     * animation always has a little travel left.
     */
    fun backDrag(progress: Float): Float = progress.coerceIn(0f, 1f) * 0.9f

    /** Where system back goes: from the list back to the shelf, otherwise not handled here. */
    fun backTarget(settledPage: Int): Int? = if (settledPage == LIST_PAGE) SHELF_PAGE else null

    /** Page a saved legacy route opens on ("shelves" was the old full-screen 书架列表). */
    fun pageForRoute(route: String): Int = if (route == "shelves") LIST_PAGE else SHELF_PAGE
}

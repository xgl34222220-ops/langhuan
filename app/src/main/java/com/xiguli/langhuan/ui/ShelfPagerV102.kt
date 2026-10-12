package com.xiguli.langhuan.ui

/**
 * V102 shelf navigation: 书架列表 is a page to the LEFT of 正在阅读. The user drags right to
 * reveal the list and left to return; the pager tracks the finger, so the shelf's left column
 * stays visible at the right edge mid-swipe, like the reference. Inside its clipped slot the list
 * lags behind (parallax), is lightly dimmed and shaded where the shelf meets it.
 *
 * Pure functions here are unit-tested; the composable only feeds them the pager position.
 */
internal object ShelfPagerV102 {
    const val LIST_PAGE = 0
    const val SHELF_PAGE = 1
    const val PAGE_COUNT = 2

    /** Fraction of the page width the list lags behind its pager position when fully covered. */
    const val LIST_PARALLAX = 0.32f

    /** Peak dim laid over the list while the shelf still covers it. */
    const val LIST_DIM = 0.10f

    /** How much of 书架列表 is revealed, 0 (on 正在阅读) … 1 (list fully open). */
    fun listReveal(currentPage: Int, currentPageOffsetFraction: Float): Float =
        (SHELF_PAGE - (currentPage + currentPageOffsetFraction)).coerceIn(0f, 1f)

    /**
     * Extra horizontal shift of the list content inside its clipped page slot (fraction of page
     * width, positive = right), so the list appears to emerge from underneath the shelf.
     */
    fun listTranslation(reveal: Float): Float = (1f - reveal.coerceIn(0f, 1f)) * LIST_PARALLAX

    /** Dim over the list: strongest when the shelf covers it, gone when the list is open. */
    fun listDim(reveal: Float): Float = (1f - reveal.coerceIn(0f, 1f)) * LIST_DIM

    /** Edge-shadow strength on the shelf page's left edge: only while it is moving. */
    fun shelfEdgeShadow(reveal: Float): Float {
        val r = reveal.coerceIn(0f, 1f)
        return if (r <= 0f || r >= 1f) 0f else minOf(1f, r * 6f, (1f - r) * 6f)
    }

    /** Where system back goes: from the list back to the shelf, otherwise not handled here. */
    fun backTarget(settledPage: Int): Int? = if (settledPage == LIST_PAGE) SHELF_PAGE else null

    /** Page a saved legacy route opens on ("shelves" was the old full-screen 书架列表). */
    fun pageForRoute(route: String): Int = if (route == "shelves") LIST_PAGE else SHELF_PAGE
}

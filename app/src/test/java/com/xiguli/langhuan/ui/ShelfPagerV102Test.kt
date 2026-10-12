package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfPagerV102Test {
    private val eps = 1e-4f

    @Test fun listIsLeftOfShelfAndShelfIsHome() {
        assertEquals(0, ShelfPagerV102.LIST_PAGE)
        assertEquals(1, ShelfPagerV102.SHELF_PAGE)
        assertEquals(2, ShelfPagerV102.PAGE_COUNT)
        assertEquals(ShelfPagerV102.SHELF_PAGE, ShelfPagerV102.pageForRoute("shelf"))
        assertEquals(ShelfPagerV102.LIST_PAGE, ShelfPagerV102.pageForRoute("shelves"))
    }

    @Test fun revealFollowsTheFinger() {
        assertEquals(0f, ShelfPagerV102.listReveal(1, 0f), eps)
        assertEquals(1f, ShelfPagerV102.listReveal(0, 0f), eps)
        // Dragging right from the shelf: current page 1, offset goes negative.
        assertEquals(0.3f, ShelfPagerV102.listReveal(1, -0.3f), eps)
        // Past the midpoint the pager reports page 0 with a positive offset.
        assertEquals(0.6f, ShelfPagerV102.listReveal(0, 0.4f), eps)
        // Overscroll never leaves 0…1.
        assertEquals(0f, ShelfPagerV102.listReveal(1, 0.2f), eps)
        assertEquals(1f, ShelfPagerV102.listReveal(0, -0.2f), eps)
    }

    @Test fun parallaxDimAndShadowFadeOutWhenSettled() {
        assertEquals(ShelfPagerV102.LIST_PARALLAX, ShelfPagerV102.listTranslation(0f), eps)
        assertEquals(0f, ShelfPagerV102.listTranslation(1f), eps)
        assertEquals(ShelfPagerV102.LIST_PARALLAX / 2, ShelfPagerV102.listTranslation(0.5f), eps)
        assertEquals(0f, ShelfPagerV102.listDim(1f), eps)
        assertEquals(ShelfPagerV102.LIST_DIM, ShelfPagerV102.listDim(0f), eps)
        assertEquals(0f, ShelfPagerV102.shelfEdgeShadow(0f), eps)
        assertEquals(0f, ShelfPagerV102.shelfEdgeShadow(1f), eps)
        assertEquals(1f, ShelfPagerV102.shelfEdgeShadow(0.5f), eps)
        assertTrue(ShelfPagerV102.shelfEdgeShadow(0.05f) in 0.01f..0.5f)
    }

    @Test fun backReturnsFromListToShelfOnly() {
        assertEquals(ShelfPagerV102.SHELF_PAGE, ShelfPagerV102.backTarget(ShelfPagerV102.LIST_PAGE))
        assertNull(ShelfPagerV102.backTarget(ShelfPagerV102.SHELF_PAGE))
    }
}

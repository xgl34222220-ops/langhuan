package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfPagerV102Test {
    private val eps = 1e-4f

    @Test fun listLeftShelfCentreMineRight() {
        assertEquals(0, ShelfPagerV102.LIST_PAGE)
        assertEquals(1, ShelfPagerV102.SHELF_PAGE)
        assertEquals(2, ShelfPagerV102.MINE_PAGE)
        assertEquals(3, ShelfPagerV102.PAGE_COUNT)
        assertEquals(ShelfPagerV102.SHELF_PAGE, ShelfPagerV102.pageForRoute("shelf"))
        assertEquals(ShelfPagerV102.LIST_PAGE, ShelfPagerV102.pageForRoute("shelves"))
        assertEquals(ShelfPagerV102.MINE_PAGE, ShelfPagerV102.pageForRoute("mine"))
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
        // Dragging left from the shelf reveals 我的.
        assertEquals(0f, ShelfPagerV102.mineReveal(1, 0f), eps)
        assertEquals(0.3f, ShelfPagerV102.mineReveal(1, 0.3f), eps)
        assertEquals(0.6f, ShelfPagerV102.mineReveal(2, -0.4f), eps)
        assertEquals(1f, ShelfPagerV102.mineReveal(2, 0f), eps)
        assertEquals(0f, ShelfPagerV102.mineReveal(1, -0.3f), eps)
        assertEquals(0f, ShelfPagerV102.mineReveal(0, 0f), eps)
    }

    @Test fun predictiveBackDragsAttachedAndNeverOvershoots() {
        assertEquals(0f, ShelfPagerV102.backDrag(0f), eps)
        assertEquals(0.45f, ShelfPagerV102.backDrag(0.5f), eps)
        assertEquals(0.9f, ShelfPagerV102.backDrag(1f), eps)
        assertEquals(0.9f, ShelfPagerV102.backDrag(3f), eps)
        assertEquals(0f, ShelfPagerV102.backDrag(-1f), eps)
        assertEquals(1, ShelfPagerV102.backDirection(ShelfPagerV102.LIST_PAGE))
        assertEquals(-1, ShelfPagerV102.backDirection(ShelfPagerV102.MINE_PAGE))
        assertEquals(0, ShelfPagerV102.backDirection(ShelfPagerV102.SHELF_PAGE))
    }

    @Test fun backReturnsFromEitherSideToTheCentre() {
        assertEquals(ShelfPagerV102.SHELF_PAGE, ShelfPagerV102.backTarget(ShelfPagerV102.LIST_PAGE))
        assertEquals(ShelfPagerV102.SHELF_PAGE, ShelfPagerV102.backTarget(ShelfPagerV102.MINE_PAGE))
        assertNull(ShelfPagerV102.backTarget(ShelfPagerV102.SHELF_PAGE))
    }
}

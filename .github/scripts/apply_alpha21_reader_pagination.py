from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: source block not found")
    return text.replace(old, new, 1)

# Reader: taps belong to the page itself, force every idle pager back to an exact
# snap position, and clip the pager viewport so adjacent pages cannot remain
# visibly overlaid after an interrupted gesture/animation.
path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = path.read_text()

old_outer_tap = '''                        onTap = { point ->
                            if (panelVisible) panelVisible = false
                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            else when {
                                point.x < size.width * .28f -> previousPage()
                                point.x > size.width * .72f -> nextPage()
                                else -> panelVisible = true
                            }
                        },'''
new_outer_tap = '''                        onTap = {
                            if (panelVisible) panelVisible = false
                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            // Paged-mode taps are handled by the pager page itself. Keeping the
                            // tap target below HorizontalPager made taps lose to the pager gesture
                            // detector on some devices.
                        },'''
s = replace_once(s, old_outer_tap, new_outer_tap, "outer reader tap routing")

old_pager = '''                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().nestedScroll(edgeSwipe),
                    beyondViewportPageCount = 1,
                    flingBehavior = pagerFling,
                    userScrollEnabled = !panelVisible && overlay == HeroReaderOverlayV13.NONE,
                ) { pagerPage ->'''
new_pager = '''                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().clipToBounds().nestedScroll(edgeSwipe),
                    beyondViewportPageCount = 0,
                    flingBehavior = pagerFling,
                    userScrollEnabled = !panelVisible && overlay == HeroReaderOverlayV13.NONE,
                ) { pagerPage ->'''
s = replace_once(s, old_pager, new_pager, "reader pager clipping")

old_page_box = '''                    Box(transition.fillMaxSize()) {
                        val safe = pagerPage.coerceIn(0, pages.lastIndex)'''
new_page_box = '''                    Box(
                        transition
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(chapter.id, pagerPage, panelVisible) {
                                detectTapGestures(
                                    onDoubleTap = { panelVisible = true },
                                    onLongPress = { panelVisible = true },
                                    onTap = { point ->
                                        if (panelVisible) panelVisible = false
                                        else when {
                                            point.x < size.width * .28f -> previousPage()
                                            point.x > size.width * .72f -> nextPage()
                                            else -> panelVisible = true
                                        }
                                    },
                                )
                            },
                    ) {
                        val safe = pagerPage.coerceIn(0, pages.lastIndex)'''
s = replace_once(s, old_page_box, new_page_box, "page-local tap zones")

settled_anchor = '''    LaunchedEffect(chapter.id, pageMode, layoutKey, pages.size) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { persist() }
        }
    }
'''
settled_replacement = settled_anchor + '''
    // A pager must never rest between two pages. Some OEM gesture stacks can cancel the
    // pager's final settle animation, leaving two chapter pages permanently visible at once.
    // Observe the idle state and hard-snap to the nearest current page if an offset remains.
    LaunchedEffect(chapter.id, pagerState, pagerPageCount) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPageOffsetFraction }
            .collectLatest { (scrolling, offset) ->
                if (!scrolling && abs(offset) > 0.001f) {
                    pagerState.scrollToPage(pagerState.currentPage.coerceIn(0, pagerPageCount - 1))
                }
            }
    }
'''
s = replace_once(s, settled_anchor, settled_replacement, "reader exact snap guard")
path.write_text(s)

# Pagination: make the usable body viewport an exact integer number of measured
# body line boxes. With paragraph spacing disabled in paged mode, every non-final
# page therefore lands its last baseline on the same vertical grid.
path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
s = path.read_text()
old_height = '''    val bodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
'''
new_height = '''    val rawBodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val lineBoxHeight = measurer.measure(
        text = "阅",
        style = bodyStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height.coerceAtLeast(1)
    // Quantize the viewport to complete line boxes. This removes the page-to-page
    // one-line drift caused by fractional dp/sp rounding on high-density screens.
    val bodyHeight = ((rawBodyHeight / lineBoxHeight).coerceAtLeast(1) * lineBoxHeight)
'''
s = replace_once(s, old_height, new_height, "aligned reader body height")
path.write_text(s)

# Version.
path = Path("app/build.gradle.kts")
s = path.read_text()
s = replace_once(s, "versionCode = 99", "versionCode = 100", "alpha21 version code")
s = replace_once(
    s,
    'versionName = "0.28.0-alpha20-reader-chat-polish"',
    'versionName = "0.28.0-alpha21-reader-pagination-fix"',
    "alpha21 version name",
)
path.write_text(s)

# Regression contracts for the exact screenshot failures.
Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderPaginationAlpha21ContractTest.kt").write_text(r'''package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginationAlpha21ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun pagerCannotRestWithTwoPagesVisible() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("Modifier.fillMaxSize().clipToBounds().nestedScroll(edgeSwipe)"))
        assertTrue(reader.contains("beyondViewportPageCount = 0"))
        assertTrue(reader.contains("pagerState.currentPageOffsetFraction"))
        assertTrue(reader.contains("abs(offset) > 0.001f"))
        assertTrue(reader.contains("pagerState.scrollToPage(pagerState.currentPage.coerceIn(0, pagerPageCount - 1))"))
    }

    @Test
    fun pageOwnsLeftCenterRightTapZones() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains(".pointerInput(chapter.id, pagerPage, panelVisible)"))
        assertTrue(reader.contains("point.x < size.width * .28f -> previousPage()"))
        assertTrue(reader.contains("point.x > size.width * .72f -> nextPage()"))
        assertTrue(reader.contains("else -> panelVisible = true"))
    }

    @Test
    fun pageBodyUsesAnIntegerLineGrid() {
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(paginator.contains("val lineBoxHeight = measurer.measure("))
        assertTrue(paginator.contains("rawBodyHeight / lineBoxHeight"))
        assertTrue(paginator.contains("* lineBoxHeight"))
    }
}
''')

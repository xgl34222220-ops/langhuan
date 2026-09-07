from pathlib import Path
import re


def one(source: str, old: str, new: str, label: str) -> str:
    if old not in source:
        raise AssertionError(f"{label}: expected source not found")
    return source.replace(old, new, 1)


# Blueprint: a clean partial checkpoint continues from 1/3 or 2/3 instead of rebuilding stage 0.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
s = p.read_text()
s = one(
    s,
    "viewModel.generateFoundation(regenerate = state.foundation != null || state.blueprintDirty)",
    "viewModel.generateFoundation(regenerate = state.blueprintDirty || state.foundationStage >= 3)",
    "blueprint continue semantics",
)
p.write_text(s)

# This runs on every settled page. commit() synchronously fsyncs; apply() removes that UI-thread hitch.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/ReaderReadingStateV11.kt")
s = p.read_text()
s = one(
    s,
    '.putLong("updated_$bookId", System.currentTimeMillis())\n            .commit()',
    '.putLong("updated_$bookId", System.currentTimeMillis())\n            .apply()',
    "async reader progress",
)
p.write_text(s)

# Qingmo V13: one pager owns one chapter. Adjacent chapters are no longer fully paginated/rendered
# as synthetic boundary pages; an edge gesture performs the chapter handoff instead.
p = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = p.read_text()

for old, new, label in [
    ("import androidx.compose.ui.Alignment\n", "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.geometry.Offset\n", "Offset import"),
    ("import androidx.compose.ui.input.key.type\n", "import androidx.compose.ui.input.key.type\nimport androidx.compose.ui.input.nestedscroll.NestedScrollConnection\nimport androidx.compose.ui.input.nestedscroll.NestedScrollSource\nimport androidx.compose.ui.input.nestedscroll.nestedScroll\n", "nested scroll imports"),
    ("import androidx.compose.ui.unit.dp\n", "import androidx.compose.ui.unit.Velocity\nimport androidx.compose.ui.unit.dp\n", "Velocity import"),
    ("import kotlin.math.absoluteValue\n", "import kotlin.math.absoluteValue\nimport kotlin.math.abs\n", "abs import"),
]:
    if new.strip() not in s:
        s = one(s, old, new, label)

s, count = re.subn(
    r"\n    val previousTitle = remember\(previous\?\.id, previous\?\.title\) \{ previous\?\.let\(::chapterTitle\)\.orEmpty\(\) \}\n"
    r"    val previousText = remember\(previous\?\.id, previous\?\.content\) \{ previous\?\.let\(::chapterText\)\.orEmpty\(\) \}\n"
    r"    val nextTitle = remember\(next\?\.id, next\?\.title\) \{ next\?\.let\(::chapterTitle\)\.orEmpty\(\) \}\n"
    r"    val nextText = remember\(next\?\.id, next\?\.content\) \{ next\?\.let\(::chapterText\)\.orEmpty\(\) \}\n",
    "\n",
    s,
    count=1,
)
if count != 1:
    raise AssertionError("adjacent chapter text cache block not found")

s, count = re.subn(
    r"\n    val previousPagination = rememberReaderPaginationV18\(.*?\n    \)\n"
    r"    val nextPagination = rememberReaderPaginationV18\(.*?\n    \)\n",
    "\n",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("adjacent pagination block not found")

s, count = re.subn(
    r"\n    val previousPages = previousPagination\.pages\.ifEmpty \{ listOf\(previousText\) \}\n"
    r"    val previousStarts = previousPagination\.pageStartsParagraph\.ifEmpty \{ listOf\(true\) \}\n"
    r"    val nextPages = nextPagination\.pages\.ifEmpty \{ listOf\(nextText\) \}\n"
    r"    val nextStarts = nextPagination\.pageStartsParagraph\.ifEmpty \{ listOf\(true\) \}\n",
    "\n",
    s,
    count=1,
)
if count != 1:
    raise AssertionError("adjacent page lists not found")

old = """    val leadingBoundary = if (pageMode != ReaderPageModeV10.SCROLL && previous != null) 1 else 0
    val trailingBoundary = if (pageMode != ReaderPageModeV10.SCROLL && next != null) 1 else 0
    val pagerPageCount = (leadingBoundary + pages.size + trailingBoundary).coerceAtLeast(1)
    val initialPagerPage = (initialPage + leadingBoundary).coerceIn(0, pagerPageCount - 1)
    val pagerState = rememberPagerState(initialPage = initialPagerPage, pageCount = { pagerPageCount })"""
new = """    val pagerPageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pagerPageCount - 1),
        pageCount = { pagerPageCount },
    )"""
s = one(s, old, new, "single chapter pager model")
s = one(
    s,
    "    fun currentPage(): Int = (pagerState.settledPage - leadingBoundary).coerceIn(0, pages.lastIndex)",
    "    fun currentPage(): Int = pagerState.settledPage.coerceIn(0, pages.lastIndex)",
    "current page without synthetic boundary",
)

old = """    fun previousPage() {
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val target = pagerState.settledPage - 1
        if (target >= 0) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            }
        } else jumpChapter(previous, true)
    }

    fun nextPage() {
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val target = pagerState.settledPage + 1
        if (target < pagerPageCount) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            }
        } else jumpChapter(next, false)
    }"""
new = """    fun previousPage() {
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val page = currentPage()
        if (page > 0) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page - 1) else pagerState.scrollToPage(page - 1)
            }
        } else jumpChapter(previous, atEnd = true)
    }

    fun nextPage() {
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val page = currentPage()
        if (page < pages.lastIndex) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page + 1) else pagerState.scrollToPage(page + 1)
            }
        } else jumpChapter(next, atEnd = false)
    }"""
s = one(s, old, new, "tap chapter boundary navigation")

s = one(
    s,
    "    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue, leadingBoundary) {",
    "    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {",
    "layout effect keys",
)
s = one(
    s,
    "            pagerState.scrollToPage((page + leadingBoundary).coerceIn(0, pagerPageCount - 1))",
    "            pagerState.scrollToPage(page.coerceIn(0, pagerPageCount - 1))",
    "layout reposition page",
)

old = """    LaunchedEffect(chapter.id, pageMode, layoutKey, leadingBoundary, trailingBoundary, pages.size) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { settled ->
                when {
                    previous != null && leadingBoundary == 1 && settled == 0 -> jumpChapter(previous, true)
                    next != null && trailingBoundary == 1 && settled == leadingBoundary + pages.size -> jumpChapter(next, false)
                    else -> persist()
                }
            }
        }
    }"""
new = """    LaunchedEffect(chapter.id, pageMode, layoutKey, pages.size) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { persist() }
        }
    }"""
s = one(s, old, new, "settled page persistence")

marker = """    Box(
        Modifier
            .fillMaxSize()
            .background(palette.page)"""
edge = """    val edgeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    val edgeSwipe = remember(chapter.id, pages.size, pageMode, previous?.id, next?.id) {
        object : NestedScrollConnection {
            var edgeDrag = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pageMode == ReaderPageModeV10.SCROLL || source != NestedScrollSource.UserInput) return Offset.Zero
                val page = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                edgeDrag = when {
                    page == 0 && available.x > 0f -> (edgeDrag + available.x).coerceAtMost(edgeThresholdPx * 2f)
                    page == pages.lastIndex && available.x < 0f -> (edgeDrag + available.x).coerceAtLeast(-edgeThresholdPx * 2f)
                    else -> 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val drag = edgeDrag
                edgeDrag = 0f
                if (abs(drag) >= edgeThresholdPx && !crossingChapter) {
                    when {
                        drag > 0f && pagerState.currentPage == 0 -> jumpChapter(previous, atEnd = true)
                        drag < 0f && pagerState.currentPage == pages.lastIndex -> jumpChapter(next, atEnd = false)
                    }
                }
                return Velocity.Zero
            }
        }
    }

"""
if marker not in s:
    raise AssertionError("root reader Box insertion point not found")
s = s.replace(marker, edge + marker, 1)

s = one(
    s,
    "                    modifier = Modifier.fillMaxSize(),\n                    beyondViewportPageCount = 1,",
    "                    modifier = Modifier.fillMaxSize().nestedScroll(edgeSwipe),\n                    beyondViewportPageCount = 1,",
    "attach edge swipe",
)

s, count = re.subn(
    r"                    Box\(transition\.fillMaxSize\(\)\) \{\n                        when \{.*?\n                        \}\n                    \}\n",
    """                    Box(transition.fillMaxSize()) {
                        val safe = pagerPage.coerceIn(0, pages.lastIndex)
                        HeroReaderCanvasV13(
                            title = displayTitle,
                            body = pages[safe],
                            pageStartsParagraph = starts.getOrElse(safe) { true },
                            page = safe + 1,
                            pageCount = pages.size,
                            fontSize = fontSize,
                            lineFactor = lineFactor,
                            paragraphSpacing = paragraphSpacing,
                            sidePadding = sidePadding,
                            firstLineIndent = firstLineIndent,
                            family = family,
                            palette = palette,
                            showTimeBattery = showTimeBattery,
                        )
                    }
""",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("synthetic boundary render block not found")

p.write_text(s)

# Version after the merge should be the newest main version.
p = Path("app/build.gradle.kts")
s = p.read_text()
s = one(s, "versionCode = 96", "versionCode = 97", "version code")
s = one(
    s,
    'versionName = "0.28.0-alpha17-wirefix-spatial2"',
    'versionName = "0.28.0-alpha18-reader-integration"',
    "version name",
)
p.write_text(s)

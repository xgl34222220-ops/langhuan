from pathlib import Path

path = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderPaginationAlpha21ContractTest.kt")
s = path.read_text()
old = '''        assertTrue(paginator.contains("val bodyHeight = ("))
        assertTrue(paginator.contains("stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard"))
        assertTrue(!paginator.contains("rawBodyHeight / lineBoxHeight"))'''
new = '''        assertTrue(paginator.contains("val fallbackBodyHeight = ("))
        assertTrue(paginator.contains("val bodyHeight = viewportHeightPx.takeIf { it > 0 } ?: fallbackBodyHeight"))
        assertTrue(paginator.contains("val bodyWidth = viewportWidthPx.takeIf { it > 0 } ?: fallbackBodyWidth"))
        assertTrue(paginator.contains("stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard"))
        assertTrue(!paginator.contains("rawBodyHeight / lineBoxHeight"))'''
if old not in s:
    raise AssertionError("alpha21 viewport contract source not found")
path.write_text(s.replace(old, new, 1))

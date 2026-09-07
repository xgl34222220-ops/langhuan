from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: source not found")
    return text.replace(old, new, 1)

reader = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
s = reader.read_text()

s = replace_once(
    s,
    "import androidx.compose.ui.unit.Velocity\n",
    "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.Velocity\n",
    "IntSize import",
)
s = replace_once(
    s,
    "import androidx.compose.ui.unit.sp\n",
    "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.layout.onSizeChanged\n",
    "onSizeChanged import",
)

needle = '''    val displayTitle = remember(chapter.id, chapter.title) { chapterTitle(chapter) }
    val readingText = remember(chapter.id, chapter.content) { chapterText(chapter) }

    val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f
    val pagination = rememberReaderPaginationV18(
        text = readingText,
        title = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = pagedParagraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )'''
replacement = '''    val displayTitle = remember(chapter.id, chapter.title) { chapterTitle(chapter) }
    val readingText = remember(chapter.id, chapter.content) { chapterText(chapter) }
    var measuredBodyViewport by remember(book.id) { mutableStateOf(IntSize.Zero) }

    val pagedParagraphSpacing = if (pageMode == ReaderPageModeV10.SCROLL) paragraphSpacing else 0f
    val pagination = rememberReaderPaginationV18(
        text = readingText,
        title = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = pagedParagraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
        viewportWidthPx = measuredBodyViewport.width,
        viewportHeightPx = measuredBodyViewport.height,
    )'''
s = replace_once(s, needle, replacement, "pagination exact viewport args")

needle = '''                            palette = palette,
                            showTimeBattery = showTimeBattery,
                            spatialBackground = spatialBackground,
                        )'''
replacement = '''                            palette = palette,
                            showTimeBattery = showTimeBattery,
                            spatialBackground = spatialBackground,
                            onBodyViewportChanged = { size ->
                                if (size.width > 0 && size.height > 0 && size != measuredBodyViewport) {
                                    measuredBodyViewport = size
                                }
                            },
                        )'''
s = replace_once(s, needle, replacement, "canvas viewport callback")

needle = '''    showTimeBattery: Boolean,
    spatialBackground: Boolean,
) {'''
replacement = '''    showTimeBattery: Boolean,
    spatialBackground: Boolean,
    onBodyViewportChanged: (IntSize) -> Unit,
) {'''
s = replace_once(s, needle, replacement, "canvas signature")

needle = '''        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            HeroReaderPageBodyV13('''
replacement = '''        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged(onBodyViewportChanged),
        ) {
            HeroReaderPageBodyV13('''
s = replace_once(s, needle, replacement, "measure real body viewport")
reader.write_text(s)

paginator = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
p = paginator.read_text()

needle = '''    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
): ReaderPaginationV18 {'''
replacement = '''    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    viewportWidthPx: Int = 0,
    viewportHeightPx: Int = 0,
): ReaderPaginationV18 {'''
p = replace_once(p, needle, replacement, "pagination signature")

old = '''    val horizontal = with(density) { sidePadding.coerceIn(12f, 48f).dp.roundToPx() }
    val bodyWidth = (stableWidth - horizontal * 2).coerceAtLeast(with(density) { 180.dp.roundToPx() })

    val pageTop = with(density) { 8.dp.roundToPx() }
    val pageBottom = with(density) { 6.dp.roundToPx() }
    val headerGap = with(density) { 7.dp.roundToPx() }
    val footerGap = with(density) { 4.dp.roundToPx() }
    val rasterGuard = with(density) { 1.dp.roundToPx() }
    val paragraphGap = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

    val headerStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFamily = family,
        fontWeight = FontWeight.Medium,
    )
    val footerStyle = TextStyle(
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Justify,
    )
    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(13f, 32f).sp,
        lineHeight = (fontSize.coerceIn(13f, 32f) * lineFactor.coerceIn(1.25f, 2.35f)).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )

    val headerHeight = measurer.measure(
        text = title,
        style = headerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height
    val footerHeight = measurer.measure(
        text = "18:09  83%                         88/100",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height

    // Use the full measured viewport. The previous integer-line quantization rounded
    // DOWN and could discard almost one complete line per page, which appeared as
    // a large empty band. Text is still split only at complete measured line ends.
    val bodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })'''
new = '''    val horizontal = with(density) { sidePadding.coerceIn(12f, 48f).dp.roundToPx() }
    val fallbackBodyWidth = (stableWidth - horizontal * 2).coerceAtLeast(with(density) { 180.dp.roundToPx() })

    val pageTop = with(density) { 8.dp.roundToPx() }
    val pageBottom = with(density) { 6.dp.roundToPx() }
    val headerGap = with(density) { 7.dp.roundToPx() }
    val footerGap = with(density) { 4.dp.roundToPx() }
    val rasterGuard = with(density) { 1.dp.roundToPx() }
    val paragraphGap = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

    val headerStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontFamily = family,
        fontWeight = FontWeight.Medium,
    )
    val footerStyle = TextStyle(
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )
    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(13f, 32f).sp,
        lineHeight = (fontSize.coerceIn(13f, 32f) * lineFactor.coerceIn(1.25f, 2.35f)).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Justify,
    )

    val headerHeight = measurer.measure(
        text = title,
        style = headerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = fallbackBodyWidth),
    ).size.height
    val footerHeight = measurer.measure(
        text = "18:09  83%                         88/100",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = fallbackBodyWidth),
    ).size.height

    // The rendered weighted body Box is the pagination source of truth. Screen/inset math
    // is only a first-frame fallback until Compose reports the exact body viewport pixels.
    val fallbackBodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val bodyWidth = viewportWidthPx.takeIf { it > 0 } ?: fallbackBodyWidth
    val bodyHeight = viewportHeightPx.takeIf { it > 0 } ?: fallbackBodyHeight'''
p = replace_once(p, old, new, "exact viewport calculation")

p = replace_once(
    p,
    '    val token = "$stableWidth:$stableHeight:$bodyWidth:$bodyHeight:$fontSize:$lineFactor:$paragraphSpacing:$firstLineIndent:${family.hashCode()}"',
    '    val token = "$stableWidth:$stableHeight:$bodyWidth:$bodyHeight:$viewportWidthPx:$viewportHeightPx:$fontSize:$lineFactor:$paragraphSpacing:$firstLineIndent:${family.hashCode()}"',
    "layout token viewport",
)
paginator.write_text(p)

build = Path("app/build.gradle.kts")
b = build.read_text()
b = replace_once(b, "versionCode = 101", "versionCode = 102", "version code")
b = replace_once(
    b,
    'versionName = "0.28.0-alpha22-reader-reference-density"',
    'versionName = "0.28.0-alpha23-reader-exact-viewport"',
    "version name",
)
build.write_text(b)

contract = Path("app/src/test/java/com/xiguli/langhuan/ui/QingmoReplicaReaderContractTest.kt")
c = contract.read_text()
c = replace_once(
    c,
    '        assertTrue(reader.contains("Box(Modifier.fillMaxWidth().weight(1f).clipToBounds())"))',
    '        assertTrue(reader.contains(".weight(1f)"))\n        assertTrue(reader.contains(".onSizeChanged(onBodyViewportChanged)"))',
    "reader body viewport contract",
)
contract.write_text(c)

test = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderExactViewportAlpha23Test.kt")
test.write_text('''package com.xiguli.langhuan.ui\n\nimport java.io.File\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass ReaderExactViewportAlpha23Test {\n    private fun source(path: String): String = File(System.getProperty("user.dir") ?: ".", path).readText()\n\n    @Test fun paginationUsesMeasuredBodyViewportInsteadOfOnlyScreenPrediction() {\n        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")\n        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")\n        assertTrue(reader.contains("var measuredBodyViewport"))\n        assertTrue(reader.contains(".onSizeChanged(onBodyViewportChanged)"))\n        assertTrue(reader.contains("viewportWidthPx = measuredBodyViewport.width"))\n        assertTrue(reader.contains("viewportHeightPx = measuredBodyViewport.height"))\n        assertTrue(paginator.contains("viewportWidthPx: Int = 0"))\n        assertTrue(paginator.contains("viewportHeightPx: Int = 0"))\n        assertTrue(paginator.contains("val bodyWidth = viewportWidthPx.takeIf { it > 0 } ?: fallbackBodyWidth"))\n        assertTrue(paginator.contains("val bodyHeight = viewportHeightPx.takeIf { it > 0 } ?: fallbackBodyHeight"))\n        assertTrue(paginator.contains("textAlign = TextAlign.Justify"))\n    }\n}\n''')

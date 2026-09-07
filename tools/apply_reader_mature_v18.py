from pathlib import Path
import re


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if old not in source:
        raise AssertionError(f"{label}: expected source not found")
    return source.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Active mobile reader: preserve the user's local V16/mobile baseline and fix it
# in place instead of merging the old 100+ commit reader experiment.
# -----------------------------------------------------------------------------
reader = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt")
s = reader.read_text()

if "import androidx.compose.runtime.key\n" not in s:
    s = replace_once(
        s,
        "import androidx.compose.runtime.getValue\n",
        "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.key\n",
        "key import",
    )

# PagerState is stateful. If the composition slot survives a chapter change, the old
# page index survives too. This is the exact cause of chapter 1 last page -> chapter 2
# last page, and the reverse boundary bug.
m = re.search(r"    MobileReaderPage\(\n.*?\n    \)\n\n    if \(showInfo\)", s, re.S)
if not m:
    raise AssertionError("active MobileReaderPage call not found")
call = m.group(0).rsplit("\n\n    if (showInfo)", 1)[0]
indented_call = "\n".join("    " + line for line in call.splitlines())
wrapped = (
    "    // Each chapter owns a fresh pager subtree; never inherit a stale page index.\n"
    "    key(chapter.id) {\n"
    + indented_call
    + "\n    }"
)
s = s[:m.start()] + wrapped + "\n\n    if (showInfo)" + s[m.end():]

# Mature baseline, while preserving user-customized values. Values that are still exactly
# the old defaults are migrated once so an upgrade visibly improves the existing install.
s = replace_once(
    s,
    "    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"font_${book.id}\", 20f)) }\n"
    "    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"line_${book.id}\", 1.65f)) }\n"
    "    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"padding_${book.id}\", 22f)) }\n"
    "    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"paragraph_${book.id}\", 8f)) }",
    "    val matureLayoutMigrated = remember(book.id) { prefs.getBoolean(\"mature18_${book.id}\", false) }\n"
    "    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"font_${book.id}\", 18.5f).let { if (!matureLayoutMigrated && kotlin.math.abs(it - 20f) < .01f) 18.5f else it }) }\n"
    "    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"line_${book.id}\", 1.78f).let { if (!matureLayoutMigrated && kotlin.math.abs(it - 1.65f) < .01f) 1.78f else it }) }\n"
    "    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"padding_${book.id}\", 24f).let { if (!matureLayoutMigrated && kotlin.math.abs(it - 22f) < .01f) 24f else it }) }\n"
    "    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat(\"paragraph_${book.id}\", 6f).let { if (!matureLayoutMigrated && kotlin.math.abs(it - 8f) < .01f) 6f else it }) }",
    "mature reader baseline",
)

# Settings are already persisted with apply(). Mark the one-time baseline migration without
# clobbering custom values.
marker = "    LaunchedEffect(fontSize, lineFactor, sidePadding, paragraphSpacing, firstLineIndent, fontKey, themeKey, pageModeKey) {\n"
if marker not in s:
    raise AssertionError("settings persistence effect not found")
s = s.replace(
    marker,
    "    LaunchedEffect(book.id) {\n"
    "        if (!prefs.getBoolean(\"mature18_${book.id}\", false)) {\n"
    "            prefs.edit().putBoolean(\"mature18_${book.id}\", true).apply()\n"
    "        }\n"
    "    }\n\n" + marker,
    1,
)

# Do not restart the pointer-input coroutine every time the page settles.
s = replace_once(
    s,
    "Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey, pagerState.settledPage)",
    "Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey)",
    "stable pointer input key",
)

# On a paged chapter, only a page that begins at a paragraph boundary should indent its first
# continuation fragment, but every later paragraph on that page should still indent.
s, count = re.subn(
    r"(?m)^(\s*)firstLineIndent = firstLineIndent && measured\.indentFirstParagraph\.getOrElse\(safePage\) \{ true \},$",
    r"\1indentEnabled = firstLineIndent,\n\1indentFirstParagraph = firstLineIndent && measured.indentFirstParagraph.getOrElse(safePage) { true },",
    s,
    count=1,
)
if count != 1:
    raise AssertionError("page indent arguments not found")

# Remove the second bottom-right progress label. The page footer below is the single source of
# truth; two overlays visually collide on short screens.
s, count = re.subn(
    r"\n            if \(!chromeVisible\) \{\n                Text\(\n                    \"\$\{chapterIndex \+ 1\}/\$\{ordered\.size\.coerceAtLeast\(1\)\} · .*?\n                \)\n            \}\n",
    "\n",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("duplicate progress overlay not found")

page_content = r'''@Composable
private fun MobileReaderPageContent(
    pageText: String,
    title: String,
    firstPage: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    palette: MobileReaderPalette,
) {
    Column(
        Modifier.fillMaxSize().background(palette.background)
            .padding(horizontal = sidePadding.dp)
            .padding(top = 22.dp, bottom = 10.dp),
    ) {
        if (firstPage) {
            Text(
                title,
                style = TextStyle(
                    fontSize = (fontSize + 2f).sp,
                    lineHeight = (fontSize + 9f).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.Medium,
                    color = palette.foreground,
                ),
            )
            Spacer(Modifier.height(18.dp))
        } else {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .50f),
            )
            Spacer(Modifier.height(14.dp))
        }
        MobileReaderParagraphs(
            text = pageText,
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            indentEnabled = indentEnabled,
            indentFirstParagraph = indentFirstParagraph,
            family = family,
            color = palette.foreground,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$page / $pageCount",
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondary.copy(alpha = .42f),
        )
    }
}'''

s, count = re.subn(
    r"@Composable\nprivate fun MobileReaderPageContent\(.*?\n\}\n\n@Composable\nprivate fun MobileScrollReadingPage",
    page_content + "\n\n@Composable\nprivate fun MobileScrollReadingPage",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("MobileReaderPageContent replacement failed")

scroll_content = r'''@Composable
private fun MobileScrollReadingPage(
    title: String,
    text: String,
    next: ChapterDraft?,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: MobileReaderPalette,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(horizontal = sidePadding.dp)
            .padding(top = 28.dp, bottom = 24.dp),
    ) {
        Text(
            title,
            style = TextStyle(
                fontSize = (fontSize + 2f).sp,
                lineHeight = (fontSize + 9f).sp,
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                color = palette.foreground,
            ),
        )
        Spacer(Modifier.height(20.dp))
        MobileReaderParagraphs(
            text = text,
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            indentEnabled = firstLineIndent,
            indentFirstParagraph = firstLineIndent,
            family = family,
            color = palette.foreground,
        )
        Spacer(Modifier.height(64.dp))
        Text(
            if (next == null) "— 全书完 —" else "下一章 · ${readerDisplayChapterTitleV13(next.title, next.chapterNumber)}",
            Modifier.fillMaxWidth().padding(bottom = 26.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondary.copy(alpha = .62f),
        )
    }
}'''

s, count = re.subn(
    r"@Composable\nprivate fun MobileScrollReadingPage\(.*?\n\}\n\n@Composable\nprivate fun MobileReaderParagraphs",
    scroll_content + "\n\n@Composable\nprivate fun MobileReaderParagraphs",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("MobileScrollReadingPage replacement failed")

paragraph_content = r'''@Composable
private fun MobileReaderParagraphs(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n").split(Regex("\\n+")).filter { it.isNotBlank() }
    }
    paragraphs.forEachIndexed { index, paragraph ->
        val shouldIndent = indentEnabled && (index > 0 || indentFirstParagraph)
        Text(
            paragraph.trim(),
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineFactor).sp,
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                color = color,
                textIndent = TextIndent(firstLine = if (shouldIndent) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}'''

s, count = re.subn(
    r"@Composable\nprivate fun MobileReaderParagraphs\(.*?\n\}\n\n@Composable\nprivate fun MobileReaderChrome",
    paragraph_content + "\n\n@Composable\nprivate fun MobileReaderChrome",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise AssertionError("MobileReaderParagraphs replacement failed")

reader.write_text(s)


# -----------------------------------------------------------------------------
# Pagination measurement and rendering must have identical geometry.
# -----------------------------------------------------------------------------
pagination = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV16.kt")
s = pagination.read_text()

old_geometry = '''    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(12f, 36f).sp,
        lineHeight = (fontSize.coerceIn(12f, 36f) * lineFactor.coerceIn(1.2f, 2.6f)).sp,
        fontFamily = family,
    )
    val titleStyle = TextStyle(
        fontSize = (fontSize + 3f).sp,
        lineHeight = (fontSize + 8f).sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = family,
    )
    val headerStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFamily = family)
    val footerStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFamily = family)

    val topFirstPx = with(density) { 10.dp.roundToPx() }
    val topNormalPx = with(density) { 8.dp.roundToPx() }
    val titleGapPx = with(density) { 10.dp.roundToPx() }
    val headerGapPx = with(density) { 10.dp.roundToPx() }
    val footerGapPx = with(density) { 4.dp.roundToPx() }
    val bottomPx = with(density) { 4.dp.roundToPx() }
    val paragraphGapPx = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

    val titleHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = titleStyle,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val headerHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = headerStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val footerHeightPx = textMeasurer.measure(
        text = "00:00    99/99 · 100%",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height

    val firstBodyHeightPx = (
        safeHeightPx - topFirstPx - titleHeightPx - titleGapPx - footerGapPx - footerHeightPx - bottomPx
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val normalBodyHeightPx = (
        safeHeightPx - topNormalPx - headerHeightPx - headerGapPx - footerGapPx - footerHeightPx - bottomPx
        ).coerceAtLeast(with(density) { 240.dp.roundToPx() })'''

new_geometry = '''    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(12f, 36f).sp,
        lineHeight = (fontSize.coerceIn(12f, 36f) * lineFactor.coerceIn(1.2f, 2.6f)).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )
    val titleStyle = TextStyle(
        fontSize = (fontSize + 2f).sp,
        lineHeight = (fontSize + 9f).sp,
        fontWeight = FontWeight.Medium,
        fontFamily = family,
    )
    val headerStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = family)
    val footerStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = family)

    val topPx = with(density) { 22.dp.roundToPx() }
    val titleGapPx = with(density) { 18.dp.roundToPx() }
    val headerGapPx = with(density) { 14.dp.roundToPx() }
    val footerGapPx = with(density) { 7.dp.roundToPx() }
    val bottomPx = with(density) { 10.dp.roundToPx() }
    // Font rasterization can round one line differently across vendors. Reserve a tiny guard
    // instead of letting the last line clip or reflow after the page is already displayed.
    val rasterGuardPx = with(density) { 4.dp.roundToPx() }
    val paragraphGapPx = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

    val titleHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = titleStyle,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val headerHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = headerStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val footerHeightPx = textMeasurer.measure(
        text = "99 / 99",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height

    val firstBodyHeightPx = (
        safeHeightPx - topPx - titleHeightPx - titleGapPx - footerGapPx - footerHeightPx - bottomPx - rasterGuardPx
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val normalBodyHeightPx = (
        safeHeightPx - topPx - headerHeightPx - headerGapPx - footerGapPx - footerHeightPx - bottomPx - rasterGuardPx
        ).coerceAtLeast(with(density) { 240.dp.roundToPx() })'''

s = replace_once(s, old_geometry, new_geometry, "pagination geometry")
pagination.write_text(s)


# -----------------------------------------------------------------------------
# Progress persistence: page settle is a hot path; don't fsync the UI thread.
# -----------------------------------------------------------------------------
progress = Path("app/src/main/java/com/xiguli/langhuan/ui/ReaderReadingStateV11.kt")
s = progress.read_text()
s = replace_once(
    s,
    '.putLong("updated_$bookId", System.currentTimeMillis())\n            .commit()',
    '.putLong("updated_$bookId", System.currentTimeMillis())\n            .apply()',
    "async reader progress save",
)
progress.write_text(s)


# -----------------------------------------------------------------------------
# Lifecycle guard: system recents/gesture transitions must never become page turns.
# -----------------------------------------------------------------------------
native = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV18.kt")
native.write_text(r'''package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/** Stable production reader entry; paging is mounted only while the activity is RESUMED. */
@Composable
fun ReaderNativeExperienceV18(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chapterKey = state.readingChapter?.id ?: "reader-loading"
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRequested by remember(chapterKey) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var readerMounted by remember(chapterKey) { mutableStateOf(resumeRequested) }

    DisposableEffect(lifecycleOwner, chapterKey) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeRequested = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    resumeRequested = false
                    readerMounted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeRequested, chapterKey) {
        if (!resumeRequested) {
            readerMounted = false
            return@LaunchedEffect
        }
        delay(120)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            readerMounted = true
        }
    }

    if (!readerMounted) {
        Box(Modifier.fillMaxSize())
        return
    }

    ReaderMobileExperience(
        viewModel = viewModel,
        studioState = studioState,
        onBackToShelf = onBackToShelf,
        onEnterWriting = onEnterWriting,
        onOpenEditor = onOpenEditor,
        onOpenAiSetup = onOpenAiSetup,
        startOnInfo = startOnInfo,
    )
}
''')

root = Path("app/src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt")
s = root.read_text()
s = replace_once(
    s,
    "                        ReaderMobileExperience(\n",
    "                        ReaderNativeExperienceV18(\n",
    "production reader entry",
)
root.write_text(s)


# -----------------------------------------------------------------------------
# Version and regression contract. The blueprint assertions are deliberate: this reader APK must
# be built from latest main, not from the older local APK branch that omitted the recent creation
# work.
# -----------------------------------------------------------------------------
gradle = Path("app/build.gradle.kts")
s = gradle.read_text()
s = replace_once(s, "versionCode = 96", "versionCode = 97", "version code")
s = replace_once(
    s,
    'versionName = "0.28.0-alpha17-wirefix-spatial2"',
    'versionName = "0.28.0-alpha18-reader-mature"',
    "version name",
)
gradle.write_text(s)

contract = Path("app/src/test/java/com/xiguli/langhuan/ui/ReaderMatureV18ContractTest.kt")
contract.write_text(r'''package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMatureV18ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun chapterBoundariesOwnFreshPagerState() {
        val root = source("app/src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt")
        val reader = source("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt")
        val native = source("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV18.kt")
        assertTrue(root.contains("ReaderNativeExperienceV18("))
        assertTrue(reader.contains("key(chapter.id)"))
        assertTrue(reader.contains("jumpChapter(previous, atEnd = true)"))
        assertTrue(reader.contains("jumpChapter(next, atEnd = false)"))
        assertTrue(native.contains("Lifecycle.Event.ON_PAUSE"))
        assertTrue(native.contains("Lifecycle.Event.ON_STOP"))
    }

    @Test
    fun pageSettlePersistenceDoesNotFsyncUiThread() {
        val progress = source("app/src/main/java/com/xiguli/langhuan/ui/ReaderReadingStateV11.kt")
        assertTrue(progress.contains(".putLong(\"updated_\$bookId\", System.currentTimeMillis())\n            .apply()"))
        assertFalse(progress.contains(".putLong(\"updated_\$bookId\", System.currentTimeMillis())\n            .commit()"))
    }

    @Test
    fun matureTypographyAndParagraphRulesAreLocked() {
        val reader = source("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt")
        val pagination = source("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV16.kt")
        assertTrue(reader.contains("prefs.getFloat(\"font_\${book.id}\", 18.5f)"))
        assertTrue(reader.contains("prefs.getFloat(\"line_\${book.id}\", 1.78f)"))
        assertTrue(reader.contains("prefs.getFloat(\"padding_\${book.id}\", 24f)"))
        assertTrue(reader.contains("prefs.getFloat(\"paragraph_\${book.id}\", 6f)"))
        assertTrue(reader.contains("index > 0 || indentFirstParagraph"))
        assertTrue(reader.contains(".padding(top = 22.dp, bottom = 10.dp)"))
        assertTrue(pagination.contains("rasterGuardPx"))
    }

    @Test
    fun latestBlueprintFlowShipsInSameApk() {
        val creation = source("app/src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
        val conversation = source("app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
        assertTrue(creation.contains("\"生成蓝图\""))
        assertTrue(creation.contains("\"同步蓝图\""))
        assertTrue(creation.contains("\"正式建书\""))
        assertTrue(conversation.contains("if (stage < 1)"))
        assertTrue(conversation.contains("禁止删卷、并卷"))
        assertTrue(conversation.contains("runningFoundation.cancel()"))
    }
}
''')

print("reader mature v18 patch applied")

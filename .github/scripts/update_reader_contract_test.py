from pathlib import Path
import re

p = Path("app/src/test/java/com/xiguli/langhuan/ui/QingmoReplicaReaderContractTest.kt")
s = p.read_text()
replacement = '''    @Test
    fun crossingChaptersIsSwipeContinuousAndDirectionallyCorrect() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderNativeExperienceV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt").readText()

        assertTrue(entry.contains("val chapterKey = state.readingChapter?.id"))
        assertTrue(entry.contains("key(chapterKey)"))
        assertTrue(reader.contains("val pagerPageCount = pages.size.coerceAtLeast(1)"))
        assertTrue(reader.contains("nestedScroll(edgeSwipe)"))
        assertFalse(reader.contains("val leadingBoundary"))
        assertFalse(reader.contains("val trailingBoundary"))
        assertFalse(reader.contains("previousPagination = rememberReaderPaginationV18"))
        assertFalse(reader.contains("nextPagination = rememberReaderPaginationV18"))
        assertTrue(reader.contains("jumpChapter(previous, atEnd = true)"))
        assertTrue(reader.contains("jumpChapter(next, atEnd = false)"))
        assertTrue(reader.contains("positionFraction = if (atEnd) 1f else 0f"))
        assertTrue(reader.contains("textOffset = if (atEnd) Int.MAX_VALUE else 0"))
    }

'''
pattern = r'    @Test\n    fun crossingChaptersIsSwipeContinuousAndDirectionallyCorrect\(\) \{.*?\n    \}\n\n(?=    @Test\n    fun pagerIsSensitiveAndSystemBackReturnsInsideTheApp)'
s, count = re.subn(pattern, replacement, s, count=1, flags=re.S)
if count != 1:
    raise AssertionError("cross-chapter reader contract test not found")
p.write_text(s)

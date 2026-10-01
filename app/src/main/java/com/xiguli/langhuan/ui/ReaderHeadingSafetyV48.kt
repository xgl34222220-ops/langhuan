package com.xiguli.langhuan.ui

/** Restore old normalized anchors when an ordinary first-sentence prefix was wrongly stripped. */
internal fun readerRestoreBodyOffsetV48(title: String, content: String, offset: Int, bodyVersion: Int): Int {
    if (bodyVersion >= 48 || offset <= 0 || content.isBlank()) return offset.coerceAtLeast(0)
    val compact = content.replace("\r\n", "\n").trimStart().replaceFirst(Regex("^[\\uFEFF\\s]*"), "")
    val heading = title.replace(Regex("\\s+"), " ").trim()
    if (heading.isBlank()) return offset
    val restored = readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(title, content))
    val legacy = when {
        compact.startsWith(heading, true) -> readerNormalizeBodyV14(compact.drop(heading.length).trimStart('\n', '\r', ' ', '\t'))
        // Old startsWith failed on spacing differences and kept this duplicate heading line.
        compact.substringBefore('\n').trim().replace(Regex("\\s+"), " ").equals(heading, true) -> readerNormalizeBodyV14(compact)
        else -> restored // The remaining separate chapter-prefix/suffix handling is unchanged.
    }
    val difference = if (restored.endsWith(legacy) || legacy.endsWith(restored)) restored.length - legacy.length else 0
    return (offset.toLong() + difference).coerceIn(0, restored.length.toLong()).toInt()
}

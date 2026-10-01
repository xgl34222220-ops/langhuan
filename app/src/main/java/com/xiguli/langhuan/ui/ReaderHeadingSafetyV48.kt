package com.xiguli.langhuan.ui

/** Restore old normalized anchors when an ordinary first-sentence prefix was wrongly stripped. */
internal fun readerRestoreBodyOffsetV48(title: String, content: String, offset: Int, bodyVersion: Int): Int {
    if (bodyVersion >= 48 || offset <= 0 || content.isBlank()) return offset.coerceAtLeast(0)
    val compact = content.replace("\r\n", "\n").trimStart().replaceFirst(Regex("^[\\uFEFF\\s]*"), "")
    val heading = title.replace(Regex("\\s+"), " ").trim()
    if (heading.isBlank() || !compact.startsWith(heading, true)) return offset
    val restored = readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(title, content))
    val legacy = readerNormalizeBodyV14(compact.drop(heading.length).trimStart('\n', '\r', ' ', '\t'))
    val difference = if (restored.endsWith(legacy)) restored.length - legacy.length else 0
    return (offset.toLong() + difference.coerceAtLeast(0)).coerceAtMost(restored.length.toLong()).toInt()
}

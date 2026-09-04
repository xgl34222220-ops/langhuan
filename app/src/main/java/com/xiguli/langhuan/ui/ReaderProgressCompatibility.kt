package com.xiguli.langhuan.ui

/**
 * Temporary source-compatibility overload for the retained V11 rollback reader.
 * New reader code uses named stable-anchor arguments; the old shell used the fourth positional
 * argument for modeKey before fraction/textOffset were introduced.
 */
@Suppress("FunctionName")
internal fun ReaderProgressV11(
    chapterNumber: Int,
    pageIndex: Int,
    scrollY: Int,
    modeKey: String,
): ReaderProgressV11 = ReaderProgressV11(
    chapterNumber = chapterNumber,
    pageIndex = pageIndex,
    scrollY = scrollY,
    modeKey = modeKey,
)

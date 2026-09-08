package com.xiguli.langhuan.ui

/** Recognize only shipped defaults; never replace a custom reading layout. */
internal fun readerUsesLegacyDefaultV26(preset: String, font: Float, line: Float, paragraph: Float, side: Float): Boolean =
    preset == "qingmo" && font == 18f && (
        (line == 1.75f && paragraph == 3f && side == 20f) ||
        (line == 1.56f && paragraph == 0f && side == 18f)
    )

package com.xiguli.langhuan.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Temporary source-level bridge for the v3 reader typography token while the reader surface is
 * being split into smaller files. It exactly mirrors LanghuanStableTheme.bodyMedium.
 */
internal object MaterialTypeography {
    val bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    )
}

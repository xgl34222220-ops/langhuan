package com.xiguli.langhuan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距尺度，8dp 主节奏 + 4dp 微调（DESIGN.md §4）。
 * 页面里写 `padding(LanghuanSpacing.pageHorizontal)` 而不是 `padding(20.dp)`，
 * 调整节奏时只改这一个文件。
 */
@Immutable
object LanghuanSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** 页面左右安全边距（规范 18–24dp）。 */
    val pageHorizontal: Dp = 20.dp

    /** 主要区块之间（规范 24–32dp）。 */
    val sectionGap: Dp = 28.dp

    /** 卡片内部（规范 16–20dp）。 */
    val cardPadding: Dp = 18.dp

    /** 列表行之间。 */
    val listGap: Dp = 12.dp
}

/** 触控尺寸下限（DESIGN.md §4 / §13）。低于这两个数就是 bug。 */
@Immutable
object LanghuanTouch {
    /** 目标触控尺寸。 */
    val target: Dp = 48.dp

    /** 紧凑场景的绝对下限，不允许再低。 */
    val minimum: Dp = 44.dp
}

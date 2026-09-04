package com.xiguli.langhuan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角尺度。DESIGN.md §4 的硬约束是「圆角必须锁定语义」，页面里不该再出现
 * 临时的 `RoundedCornerShape(n.dp)`。要新圆角就在这里加一个语义名。
 *
 * 语义区间来自 DESIGN.md §4：
 * - 图书封面 12–18dp
 * - 普通信息卡 18–20dp
 * - 一级操作卡 / 浮层 24–30dp
 * - 胶囊操作 = 高度的一半
 *
 * 原来 shadcn 那套是 5 / 6 / 8 / 10 / 12dp，整体低于上面每一个区间——那是给
 * 桌面 Web 的密度，放到手机上触控感偏硬，封面也显得像后台管理系统的缩略图。
 * kami 原包更极端（2–16dp 的印刷密度），同样按 DESIGN.md 上调：产品规范优先
 * 于设计包。
 */
@Immutable
object LanghuanRadius {
    /** 标签、徽标等最小元素。 */
    val tag: Dp = 8.dp

    /** 芯片、小型输入框。 */
    val chip: Dp = 10.dp

    /** 图书封面。 */
    val cover: Dp = 14.dp

    /** 普通信息卡、列表卡。 */
    val card: Dp = 18.dp

    /** 分区面板、较大的内容容器。 */
    val panel: Dp = 22.dp

    /** Bottom Sheet、浮动控制条、一级操作卡。 */
    val sheet: Dp = 26.dp
}

/** 语义化 Shape。页面里优先用这些，而不是 `MaterialTheme.shapes` 的抽象档位。 */
@Immutable
object LanghuanShape {
    val tag = RoundedCornerShape(LanghuanRadius.tag)
    val chip = RoundedCornerShape(LanghuanRadius.chip)
    val cover = RoundedCornerShape(LanghuanRadius.cover)
    val card = RoundedCornerShape(LanghuanRadius.card)
    val panel = RoundedCornerShape(LanghuanRadius.panel)
    val sheet = RoundedCornerShape(LanghuanRadius.sheet)

    /** 只有顶部圆角的浮层：Bottom Sheet 主卡。 */
    val sheetTop = RoundedCornerShape(
        topStart = LanghuanRadius.sheet,
        topEnd = LanghuanRadius.sheet,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )

    /** 胶囊按钮。用 50% 而不是写死大数值，随高度自适应。 */
    val pill = RoundedCornerShape(percent = 50)

    /** 用户发出的对话气泡：自己这一侧的下角收紧，形成方向感。 */
    val bubbleOutgoing = RoundedCornerShape(
        topStart = LanghuanRadius.panel,
        topEnd = LanghuanRadius.panel,
        bottomEnd = 7.dp,
        bottomStart = LanghuanRadius.panel,
    )

    /** 对方发出的对话气泡，与 [bubbleOutgoing] 镜像。 */
    val bubbleIncoming = RoundedCornerShape(
        topStart = LanghuanRadius.panel,
        topEnd = LanghuanRadius.panel,
        bottomEnd = LanghuanRadius.panel,
        bottomStart = 7.dp,
    )
}

/** 喂给 MaterialTheme，让所有 M3 组件默认落在同一套尺度上。 */
internal val LanghuanShapes = Shapes(
    extraSmall = LanghuanShape.tag,
    small = LanghuanShape.chip,
    medium = LanghuanShape.card,
    large = LanghuanShape.panel,
    extraLarge = LanghuanShape.sheet,
)

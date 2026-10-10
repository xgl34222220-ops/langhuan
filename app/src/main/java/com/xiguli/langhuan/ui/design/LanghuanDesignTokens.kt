package com.xiguli.langhuan.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 琅嬛 Langhuan Design Tokens · v3（2026-10 UI refresh）
 *
 * 2026-10：中性色迁移到 zinc 灰阶（参考 shadcn/ui 的 neutral/zinc 基色），
 * 强调色仍只有玉青一套、赤金只做稀有语义；层级靠 1px hairline 而非投影。
 * 每个字段注释里的旧色值是 v3 历史记录，以下方 Light/Dark 实例为准。
 *
 * 核心原则：
 *
 * 1. 浅色 / 深色拥有完整独立 Token。
 * 2. 一级、二级、三级文字颜色明确分层。
 * 3. 主色使用鲜明玉青。
 * 4. 新增赤金 Gold 语义体系。
 * 5. 圆角只保留 8 / 12 / 16 / 24 dp 四档。
 * 6. 间距只保留 4 / 8 / 12 / 16 / 24 / 32 dp 六档。
 * 7. UI 分层只使用 1px border，不使用投影。
 *
 * Reader V30 的正文纸张主题仍由 Reader 自身管理，
 * 不直接使用这里的 background / card 作为小说正文纸张颜色。
 */
@Immutable
data class LanghuanUiTokens(

    /* ---------------------------------------------------------------------- */
    /*                                Surfaces                                */
    /* ---------------------------------------------------------------------- */

    /**
     * 页面最底层背景。
     *
     * Light: #F7F5F0
     * Dark : #141311
     */
    val background: Color,

    /**
     * 一级容器背景。
     *
     * Light: #FFFFFF
     * Dark : #1C1B18
     */
    val card: Color,

    /**
     * 输入区域背景。
     *
     * Light: #EAE7E0
     * Dark : #262420
     */
    val input: Color,

    /**
     * 统一边界颜色。
     *
     * v3 不再使用阴影区分普通层级，
     * Card / Input / Sheet 等统一使用 1px border。
     *
     * Light: #DDDAD3
     * Dark : #2C2A26
     */
    val border: Color,


    /* ---------------------------------------------------------------------- */
    /*                              Typography                                */
    /* ---------------------------------------------------------------------- */

    /**
     * 一级文字。
     *
     * 用于：
     * - 页面主标题
     * - 书名
     * - 正文主信息
     * - 高优先级图标
     *
     * Light: #22221F
     * Dark : #ECE8E0
     */
    val foreground: Color,

    /**
     * 二级文字。
     *
     * 用于：
     * - 列表副标题
     * - 作者
     * - 较重要说明
     * - 二级图标
     *
     * Light: #4B4B46
     * Dark : #B5B0A6
     */
    val secondaryForeground: Color,

    /**
     * 三级文字。
     *
     * 用于：
     * - 日期
     * - Hint
     * - Caption
     * - 弱辅助信息
     *
     * Light: #666760
     * Dark : #8E897F
     */
    val mutedForeground: Color,


    /* ---------------------------------------------------------------------- */
    /*                                Primary                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 鲜明玉青。
     *
     * 用于：
     * - 主要操作
     * - 当前状态
     * - 选中态强调
     * - 品牌识别
     *
     * Light: #1E6A5A
     * Dark : #7CCAB3
     */
    val primary: Color,


    /* ---------------------------------------------------------------------- */
    /*                                 Accent                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 玉青低强调容器。
     *
     * Light: #DDE9E4
     * Dark : #1B3731
     */
    val accent: Color,

    /**
     * Accent 容器上的前景色。
     *
     * Light: #17513F
     * Dark : #A6E0CD
     */
    val accentForeground: Color,


    /* ---------------------------------------------------------------------- */
    /*                                  Gold                                  */
    /* ---------------------------------------------------------------------- */

    /**
     * 赤金。
     *
     * 用于真正具有：
     * - 收藏
     * - 成就
     * - 精选
     * - 阅读连续记录
     * - 重要特殊状态
     *
     * 等语义的内容。
     *
     * Light: #A9681C
     * Dark : #D8A45E
     */
    val gold: Color,

    /**
     * Gold 强调文字。
     *
     * Light: #8A5312
     * Dark : #E2B777
     */
    val goldForeground: Color,

    /**
     * Gold 低强调背景。
     *
     * Light: #F3E6D2
     * Dark : #372C1B
     */
    val goldContainer: Color,


    /* ---------------------------------------------------------------------- */
    /*                              Destructive                               */
    /* ---------------------------------------------------------------------- */

    /**
     * 删除 / 清除 / 不可逆危险操作。
     *
     * Light: #B03A2B
     * Dark : #F08C7C
     */
    val destructive: Color,

    /**
     * destructive 实色容器上的文字 / 图标颜色。
     *
     * Claude v3 原始 Token 表没有提供这一项。
     *
     * 本实现按语义补充：
     *
     * Light: #FFFFFF
     * Dark : #32110C
     */
    val destructiveForeground: Color,


    /* ---------------------------------------------------------------------- */
    /*                                Spacing                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 4dp
     *
     * 最小间距。
     */
    val space1: Dp,

    /**
     * 8dp
     *
     * 图标 / 小型元素之间。
     */
    val space2: Dp,

    /**
     * 12dp
     *
     * 紧凑 Row 与组件内部。
     */
    val space3: Dp,

    /**
     * 16dp
     *
     * 标准内容间距。
     */
    val space4: Dp,

    /**
     * 24dp
     *
     * 大区块内部间距。
     */
    val space5: Dp,

    /**
     * 32dp
     *
     * 一级 Section 间距。
     */
    val space6: Dp,


    /* ---------------------------------------------------------------------- */
    /*                                 Radius                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 8dp
     */
    val radiusSm: Dp,

    /**
     * 12dp
     */
    val radiusMd: Dp,

    /**
     * 16dp
     */
    val radiusLg: Dp,

    /**
     * 24dp
     */
    val radiusXl: Dp,

    /* ------------------------------------------------------------------ */
    /* v2 兼容层：旧页面仍在使用的语义 token，v3 设计表未收录。            */
    /* 全部映射到 v3 语义，旧页面迁移完成后可删除。                        */
    /* ------------------------------------------------------------------ */

    /** 卡片内容色 → foreground。 */
    val cardForeground: Color,

    /** 实色主按钮前景。浅色白字，深色深字。 */
    val primaryForeground: Color,

    /** 弱填充 → input。 */
    val muted: Color,

    /** 焦点环 → primary。 */
    val ring: Color,

    /** 强调文字 → foreground。 */
    val strong: Color,

    /** 轨道/分隔填充 → border。 */
    val track: Color,

    /** 暖底 → goldContainer。 */
    val warmSurface: Color,

    /** 成功 → primary。 */
    val success: Color,

    /** 成功前景。 */
    val successForeground: Color,

    /** 警告 → gold。 */
    val warning: Color,

    /** 警告前景 → goldForeground。 */
    val warningForeground: Color,
)


/* -------------------------------------------------------------------------- */
/*                               Light Tokens                                 */
/* -------------------------------------------------------------------------- */

/**
 * Claude v3 定稿浅色 Token。
 */
val LanghuanLightUiTokens = LanghuanUiTokens(

    /* Surfaces · zinc 中性灰阶（2026-10 UI refresh） */
    background = Color(0xFFFAFAFA),
    card = Color(0xFFFFFFFF),
    input = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),

    /* Typography：三级文字在 background / card / input 上均 ≥ 4.5:1 */
    foreground = Color(0xFF18181B),
    secondaryForeground = Color(0xFF3F3F46),
    mutedForeground = Color(0xFF5C5C66),

    /* Primary：唯一强调色，玉青保持品牌识别但只用于主操作 / 进度 / 焦点 */
    primary = Color(0xFF1E6A5A),

    /* Accent */
    accent = Color(0xFFE4EFEA),
    accentForeground = Color(0xFF17513F),

    /* Gold：仅用于书签 / 继续阅读 / 成就等稀有语义 */
    gold = Color(0xFFA9681C),
    goldForeground = Color(0xFF8A5312),
    goldContainer = Color(0xFFF5EBDB),

    /* Destructive */
    destructive = Color(0xFFB03A2B),
    destructiveForeground = Color(0xFFFFFFFF),

    /* Spacing：4 / 8 / 12 / 16 / 24 / 32 */
    space1 = 4.dp,
    space2 = 8.dp,
    space3 = 12.dp,
    space4 = 16.dp,
    space5 = 24.dp,
    space6 = 32.dp,

    /* Radius：8 / 12 / 16 / 24（胶囊 = 高度一半） */
    radiusSm = 8.dp,
    radiusMd = 12.dp,
    radiusLg = 16.dp,
    radiusXl = 24.dp,

    /* v2 兼容层 */
    cardForeground = Color(0xFF18181B),
    primaryForeground = Color(0xFFFFFFFF),
    muted = Color(0xFFF4F4F5),
    ring = Color(0xFF1E6A5A),
    strong = Color(0xFF18181B),
    track = Color(0xFFE4E4E7),
    warmSurface = Color(0xFFF5EBDB),
    success = Color(0xFF1E6A5A),
    successForeground = Color(0xFFFFFFFF),
    warning = Color(0xFFA9681C),
    warningForeground = Color(0xFF8A5312),
)


/* -------------------------------------------------------------------------- */
/*                                Dark Tokens                                 */
/* -------------------------------------------------------------------------- */

/**
 * Claude v3 定稿深色 Token。
 */
val LanghuanDarkUiTokens = LanghuanUiTokens(

    /* Surfaces · zinc 中性灰阶（2026-10 UI refresh） */
    background = Color(0xFF09090B),
    card = Color(0xFF131316),
    input = Color(0xFF202024),
    border = Color(0xFF2A2A2F),

    /* Typography：三级文字在 background / card / input 上均 ≥ 4.5:1 */
    foreground = Color(0xFFFAFAFA),
    secondaryForeground = Color(0xFFD4D4D8),
    mutedForeground = Color(0xFFA1A1AA),

    /* Primary：唯一强调色，玉青保持品牌识别但只用于主操作 / 进度 / 焦点 */
    primary = Color(0xFF7CCAB3),

    /* Accent */
    accent = Color(0xFF15302A),
    accentForeground = Color(0xFFA6E0CD),

    /* Gold：仅用于书签 / 继续阅读 / 成就等稀有语义 */
    gold = Color(0xFFD8A45E),
    goldForeground = Color(0xFFE2B777),
    goldContainer = Color(0xFF33291A),

    /* Destructive */
    destructive = Color(0xFFF08C7C),
    destructiveForeground = Color(0xFF32110C),

    /* Spacing：4 / 8 / 12 / 16 / 24 / 32 */
    space1 = 4.dp,
    space2 = 8.dp,
    space3 = 12.dp,
    space4 = 16.dp,
    space5 = 24.dp,
    space6 = 32.dp,

    /* Radius：8 / 12 / 16 / 24（胶囊 = 高度一半） */
    radiusSm = 8.dp,
    radiusMd = 12.dp,
    radiusLg = 16.dp,
    radiusXl = 24.dp,

    /* v2 兼容层 */
    cardForeground = Color(0xFFFAFAFA),
    primaryForeground = Color(0xFF0B201A),
    muted = Color(0xFF202024),
    ring = Color(0xFF7CCAB3),
    strong = Color(0xFFFAFAFA),
    track = Color(0xFF2A2A2F),
    warmSurface = Color(0xFF33291A),
    success = Color(0xFF7CCAB3),
    successForeground = Color(0xFF0B201A),
    warning = Color(0xFFD8A45E),
    warningForeground = Color(0xFFE2B777),
)


/**
 * 当前琅嬛 UI Token。
 *
 * LanghuanStableTheme 会根据 themeMode：
 *
 * - FOLLOW_SYSTEM
 * - LIGHT
 * - DARK
 *
 * 注入 LanghuanLightUiTokens 或 LanghuanDarkUiTokens。
 *
 * 未包裹 Theme 时默认使用浅色 v3 Token，
 * 便于 Preview 与独立组件测试。
 */
val LocalLanghuanUiTokens = staticCompositionLocalOf {
    LanghuanLightUiTokens
}

package com.xiguli.langhuan.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 琅嬛统一 UI 组件源 · v3
 *
 * v3 核心规则：
 *
 * 1. 普通 UI 不再使用 shadow / elevation。
 * 2. 内容层级依靠：
 *    - background
 *    - card
 *    - input
 *    - accent
 *    - goldContainer
 *    - 1dp border
 * 3. 圆角只允许：
 *    - 8dp
 *    - 12dp
 *    - 16dp
 *    - 24dp
 * 4. 布局间距优先使用：
 *    - space1 = 4dp
 *    - space2 = 8dp
 *    - space3 = 12dp
 *    - space4 = 16dp
 *    - space5 = 24dp
 *    - space6 = 32dp
 * 5. 文字层级：
 *    - foreground：一级
 *    - secondaryForeground：二级
 *    - mutedForeground：三级
 *
 * Reader V30 正文、分页纸张与阅读主题不由本文件控制。
 */


/* -------------------------------------------------------------------------- */
/*                                  Metrics                                   */
/* -------------------------------------------------------------------------- */

/**
 * 非 spacing 类尺寸。
 *
 * 页面间距不要在这里继续扩张，
 * spacing 统一从 LocalLanghuanUiTokens 的 space1 ~ space6 获取。
 */
@Immutable
object LanghuanUiMetrics {

    val ButtonHeight = 48.dp

    val CompactButtonHeight = 44.dp

    val IconButtonTouchTarget = 48.dp

    val IconButtonVisibleSize = 40.dp

    val IconSize = 20.dp

    val SmallIconSize = 18.dp

    val SearchHeight = 48.dp

    val SegmentHeight = 40.dp
}


/* -------------------------------------------------------------------------- */
/*                                   Screen                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanScreen(
    modifier: Modifier = Modifier,
    applyStatusBarInset: Boolean = false,
    applyNavigationBarInset: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    var containerModifier = modifier
        .fillMaxSize()
        .background(t.background)

    if (applyStatusBarInset) {
        containerModifier = containerModifier.windowInsetsPadding(
            WindowInsets.statusBars,
        )
    }

    if (applyNavigationBarInset) {
        containerModifier = containerModifier.windowInsetsPadding(
            WindowInsets.navigationBars,
        )
    }

    Box(
        modifier = containerModifier,
        content = content,
    )
}


@Composable
fun LanghuanSafeScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        content = content,
    )
}


/* -------------------------------------------------------------------------- */
/*                                Page Header                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = t.space4,
                vertical = t.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = LanghuanTextStyles.secondary,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(t.space1))
            }

            Text(
                text = title,
                style = LanghuanTextStyles.pageTitle,
                color = t.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(t.space1))

                Text(
                    text = subtitle,
                    style = LanghuanTextStyles.secondary,
                    color = t.secondaryForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(t.space1),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}


@Composable
fun LanghuanTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = t.space3,
                vertical = t.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            navigation()

            Spacer(Modifier.width(t.space2))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(t.space1))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.space1),
            content = actions,
        )
    }
}


@Composable
fun LanghuanLargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = LanghuanTextStyles.pageTitle,
            color = t.foreground,
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(t.space2))

            Text(
                text = subtitle,
                style = LanghuanTextStyles.secondary,
                color = t.secondaryForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Section                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    contentSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = LanghuanTextStyles.sectionTitle,
                    color = t.foreground,
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(t.space1))

                    Text(
                        text = subtitle,
                        style = LanghuanTextStyles.secondary,
                        color = t.secondaryForeground,
                    )
                }
            }

            if (action != null) {
                Spacer(Modifier.width(t.space3))
                action()
            }
        }

        Spacer(Modifier.height(contentSpacing))

        Column(
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}


@Composable
fun LanghuanSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = t.mutedForeground,
    )
}


/* -------------------------------------------------------------------------- */
/*                                    Card                                    */
/* -------------------------------------------------------------------------- */

/**
 * v3 Card。
 *
 * depth 仅保留用于兼容现有调用并调整圆角：
 *
 * depth = 0 -> 12dp
 * depth = 1 -> 16dp
 * depth = 2 -> 24dp
 *
 * 所有层级均不使用投影。
 */
@Composable
fun LanghuanCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    depth: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val level = depth.coerceIn(0, 2)

    val radius = when (level) {
        0 -> t.radiusMd
        2 -> t.radiusXl
        else -> t.radiusLg
    }

    val shape = RoundedCornerShape(radius)

    val clickableModifier = if (onClick != null) {
        Modifier.springClickV31(
            pressedScale = 0.985f,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .clip(shape)
            .background(t.card)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .animateContentSize(
                animationSpec = LanghuanMotionV31.settle(),
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
        ) {
            content()
        }
    }
}


/**
 * 设置页 / 我的 / AI Provider 等列表组。
 */
@Composable
fun LanghuanGroupedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.card)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}


/**
 * 带书卷气的暖色内容卡。
 *
 * v3 不再存在独立 warmSurface Token，
 * 暖色内容区域统一使用 goldContainer。
 */
@Composable
fun LanghuanWarmCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    val clickableModifier = if (onClick != null) {
        Modifier.springClickV31(
            pressedScale = 0.985f,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(clickableModifier)
            .clip(shape)
            .background(t.goldContainer)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}


/* -------------------------------------------------------------------------- */
/*                                List Row                                    */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current

    val interaction = remember {
        MutableInteractionSource()
    }

    val pressed by interaction.collectIsPressedAsState()

    val background by animateColorAsState(
        targetValue = if (pressed && enabled) {
            t.input
        } else {
            Color.Transparent
        },
        animationSpec = tween(LanghuanMotionV31.FAST),
        label = "langhuanListRowBackground",
    )

    val contentAlpha = if (enabled) 1f else 0.42f

    val interactionModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(t.radiusMd),
            )
            .background(background)
            .then(interactionModifier)
            .padding(
                horizontal = t.space3,
                vertical = t.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.pressScaleV31(
                    interactionSource = interaction,
                    pressedScale = 0.94f,
                ),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }

            Spacer(Modifier.width(t.space3))
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = t.foreground.copy(
                    alpha = contentAlpha,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(t.space1))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground.copy(
                        alpha = contentAlpha,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(t.space3))

            Box(
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}


@Composable
fun LanghuanMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current

    LanghuanListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = {
            val shape = RoundedCornerShape(t.radiusSm)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(shape)
                    .background(t.input)
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(
                        LanghuanUiMetrics.IconSize,
                    ),
                    tint = t.secondaryForeground,
                )
            }
        },
        trailing = trailing,
        onClick = onClick,
    )
}


/* -------------------------------------------------------------------------- */
/*                                Icon Button                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current

    val background by animateColorAsState(
        targetValue = when {
            !enabled -> t.input
            selected -> t.accent
            else -> t.card
        },
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanIconButtonBackground",
    )

    val foreground by animateColorAsState(
        targetValue = when {
            !enabled -> t.mutedForeground
            selected -> t.accentForeground
            else -> t.secondaryForeground
        },
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanIconButtonForeground",
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            t.primary
        } else {
            t.border
        },
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanIconButtonBorder",
    )

    val interaction = remember {
        MutableInteractionSource()
    }

    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(LanghuanUiMetrics.IconButtonTouchTarget)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(
                    HapticFeedbackType.TextHandleMove,
                )

                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .pressScaleV31(
                    interactionSource = interaction,
                    pressedScale = 0.9f,
                )
                .size(LanghuanUiMetrics.IconButtonVisibleSize)
                .clip(CircleShape)
                .background(background)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = icon,
                animationSpec = tween(
                    LanghuanMotionV31.FAST,
                ),
                label = "langhuanIconButtonGlyph",
            ) { glyph ->
                Icon(
                    imageVector = glyph,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(
                        LanghuanUiMetrics.IconSize,
                    ),
                    tint = foreground.copy(
                        alpha = if (enabled) 1f else 0.5f,
                    ),
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                   Badge                                    */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current

    val background by animateColorAsState(
        targetValue = if (accent) {
            t.accent
        } else {
            t.input
        },
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanBadgeBackground",
    )

    val foreground by animateColorAsState(
        targetValue = if (accent) {
            t.accentForeground
        } else {
            t.secondaryForeground
        },
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanBadgeForeground",
    )

    Box(
        modifier = modifier
            .animateContentSize(
                animationSpec = LanghuanMotionV31.settle(),
            )
            .clip(CircleShape)
            .background(background)
            .border(
                width = 1.dp,
                color = t.border,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = t.space2,
                vertical = t.space1,
            ),
            style = LanghuanTextStyles.badge,
            color = foreground,
        )
    }
}


enum class LanghuanBadgeTone {
    Neutral,
    Accent,
    Gold,
    Success,
    Warning,
    Destructive,
}


@Composable
fun LanghuanStatusBadge(
    text: String,
    tone: LanghuanBadgeTone,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    /*
     * v3 没有单独 success / warning Token：
     *
     * Success -> 玉青体系
     * Warning -> 赤金体系
     */
    val background = when (tone) {
        LanghuanBadgeTone.Neutral -> t.input

        LanghuanBadgeTone.Accent -> t.accent

        LanghuanBadgeTone.Gold -> t.goldContainer

        LanghuanBadgeTone.Success -> t.accent

        LanghuanBadgeTone.Warning -> t.goldContainer

        LanghuanBadgeTone.Destructive ->
            t.destructive.copy(alpha = 0.10f)
    }

    val foreground = when (tone) {
        LanghuanBadgeTone.Neutral -> t.secondaryForeground

        LanghuanBadgeTone.Accent -> t.accentForeground

        LanghuanBadgeTone.Gold -> t.goldForeground

        LanghuanBadgeTone.Success -> t.primary

        LanghuanBadgeTone.Warning -> t.goldForeground

        LanghuanBadgeTone.Destructive -> t.destructive
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(
                width = 1.dp,
                color = t.border,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = t.space2,
                vertical = t.space1,
            ),
            style = LanghuanTextStyles.badge,
            color = foreground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                   Button                                   */
/* -------------------------------------------------------------------------- */

enum class LanghuanButtonStyle {
    Primary,
    Secondary,
    Ghost,
    Destructive,
}


@Composable
fun LanghuanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LanghuanButtonStyle = LanghuanButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val t = LocalLanghuanUiTokens.current

    val background = when (style) {
        LanghuanButtonStyle.Primary -> t.primary
        LanghuanButtonStyle.Secondary -> t.card
        LanghuanButtonStyle.Ghost -> Color.Transparent
        LanghuanButtonStyle.Destructive -> t.destructive
    }

    /*
     * v3 Token 表没有 primaryForeground。
     *
     * 浅色：
     * primary #1E6A5A + card #FFFFFF
     *
     * 深色：
     * primary #7CCAB3 + card #1C1B18
     *
     * 因此 card 正好作为两套主题下的高对比 Primary 前景。
     */
    val contentColor = when (style) {
        LanghuanButtonStyle.Primary -> t.card
        LanghuanButtonStyle.Secondary -> t.foreground
        LanghuanButtonStyle.Ghost -> t.primary
        LanghuanButtonStyle.Destructive -> t.destructiveForeground
    }

    val borderColor = when (style) {
        LanghuanButtonStyle.Primary -> t.primary
        LanghuanButtonStyle.Secondary -> t.border
        LanghuanButtonStyle.Ghost -> Color.Transparent
        LanghuanButtonStyle.Destructive -> t.destructive
    }

    val interaction = remember {
        MutableInteractionSource()
    }

    val pressed by interaction.collectIsPressedAsState()

    val pressedBackground = when (style) {
        LanghuanButtonStyle.Primary ->
            t.primary.copy(alpha = 0.88f)

        LanghuanButtonStyle.Secondary ->
            t.input

        LanghuanButtonStyle.Ghost ->
            t.accent

        LanghuanButtonStyle.Destructive ->
            t.destructive.copy(alpha = 0.88f)
    }

    val animatedBackground by animateColorAsState(
        targetValue = if (pressed) {
            pressedBackground
        } else {
            background
        },
        animationSpec = tween(LanghuanMotionV31.FAST),
        label = "langhuanButtonBackground",
    )

    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = modifier
            .height(LanghuanUiMetrics.ButtonHeight)
            .clip(shape)
            .background(
                animatedBackground.copy(
                    alpha = if (enabled) 1f else 0.45f,
                ),
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(
                    alpha = if (enabled) 1f else 0.45f,
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space4,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )

            Spacer(Modifier.width(t.space2))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(
                    LanghuanUiMetrics.SmallIconSize,
                ),
                tint = contentColor.copy(
                    alpha = if (enabled) 1f else 0.65f,
                ),
            )

            Spacer(Modifier.width(t.space2))
        }

        Text(
            text = text,
            style = LanghuanTextStyles.button,
            color = contentColor.copy(
                alpha = if (enabled) 1f else 0.65f,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Search Field                                */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val t = LocalLanghuanUiTokens.current

    val interaction = remember {
        MutableInteractionSource()
    }

    val focused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            t.primary
        } else {
            t.border
        },
        animationSpec = tween(LanghuanMotionV31.FAST),
        label = "langhuanSearchBorder",
    )

    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LanghuanUiMetrics.SearchHeight)
            .clip(shape)
            .background(t.input)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(
                horizontal = t.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = t.mutedForeground,
            )

            Spacer(Modifier.width(t.space2))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = t.foreground,
                ),
                cursorBrush = SolidColor(t.primary),
                interactionSource = interaction,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
            )
        }

        if (trailing != null) {
            Spacer(Modifier.width(t.space2))
            trailing()
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                            Segmented Control                               */
/* -------------------------------------------------------------------------- */

@Immutable
data class LanghuanSegment<T>(
    val value: T,
    val label: String,
)


@Composable
fun <T> LanghuanSegmentedControl(
    items: List<LanghuanSegment<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current

    if (items.isEmpty()) return

    val outerShape = RoundedCornerShape(t.radiusMd)
    val innerShape = RoundedCornerShape(t.radiusSm)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LanghuanUiMetrics.SegmentHeight)
            .clip(outerShape)
            .background(t.input)
            .border(
                width = 1.dp,
                color = t.border,
                shape = outerShape,
            )
            .padding(t.space1),
        horizontalArrangement = Arrangement.spacedBy(t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.value == selectedValue

            val background by animateColorAsState(
                targetValue = if (selected) {
                    t.card
                } else {
                    Color.Transparent
                },
                animationSpec = tween(LanghuanMotionV31.MEDIUM),
                label = "langhuanSegmentBackground",
            )

            val foreground by animateColorAsState(
                targetValue = if (selected) {
                    t.foreground
                } else {
                    t.mutedForeground
                },
                animationSpec = tween(LanghuanMotionV31.MEDIUM),
                label = "langhuanSegmentForeground",
            )

            val segmentBorder by animateColorAsState(
                targetValue = if (selected) {
                    t.border
                } else {
                    Color.Transparent
                },
                animationSpec = tween(LanghuanMotionV31.MEDIUM),
                label = "langhuanSegmentBorder",
            )

            val interaction = remember(item.value) {
                MutableInteractionSource()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(innerShape)
                    .background(background)
                    .border(
                        width = 1.dp,
                        color = segmentBorder,
                        shape = innerShape,
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Tab,
                    ) {
                        onSelected(item.value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    color = foreground.copy(
                        alpha = if (enabled) 1f else 0.45f,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Empty State                                  */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = t.space5,
                vertical = t.space6,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            val shape = RoundedCornerShape(t.radiusLg)

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(t.input)
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = t.secondaryForeground,
                )
            }

            Spacer(Modifier.height(t.space4))
        }

        Text(
            text = title,
            style = LanghuanTextStyles.sectionTitle,
            color = t.foreground,
        )

        Spacer(Modifier.height(t.space2))

        Text(
            text = description,
            style = LanghuanTextStyles.secondary,
            color = t.secondaryForeground,
        )

        if (action != null) {
            Spacer(Modifier.height(t.space5))
            action()
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Stat Tile                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = modifier
            .clip(shape)
            .background(t.card)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(t.space4),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = t.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(t.space1))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = t.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!supportingText.isNullOrBlank()) {
            Spacer(Modifier.height(t.space2))

            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Text Helpers                                */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanPrimaryText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
) {
    val t = LocalLanghuanUiTokens.current

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = t.foreground,
        maxLines = maxLines,
        overflow = if (maxLines != Int.MAX_VALUE) {
            TextOverflow.Ellipsis
        } else {
            TextOverflow.Clip
        },
    )
}


/**
 * 二级文字。
 */
@Composable
fun LanghuanSecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    val t = LocalLanghuanUiTokens.current

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = t.secondaryForeground,
        maxLines = maxLines,
        overflow = if (maxLines != Int.MAX_VALUE) {
            TextOverflow.Ellipsis
        } else {
            TextOverflow.Clip
        },
    )
}


/**
 * 三级辅助文字。
 */
@Composable
fun LanghuanMutedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = Int.MAX_VALUE,
) {
    val t = LocalLanghuanUiTokens.current

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = t.mutedForeground,
        maxLines = maxLines,
        overflow = if (maxLines != Int.MAX_VALUE) {
            TextOverflow.Ellipsis
        } else {
            TextOverflow.Clip
        },
    )
}


/* -------------------------------------------------------------------------- */
/*                                Separators                                  */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanSeparator(
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp,
) {
    val t = LocalLanghuanUiTokens.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalInset,
            )
            .height(1.dp)
            .background(t.border),
    )
}


@Composable
fun LanghuanInsetSeparator(
    modifier: Modifier = Modifier,
    startInset: Dp = 64.dp,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.width(startInset))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(t.border),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Book Visual                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanBookCoverSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)

    Box(
        modifier = modifier
            .clip(shape)
            .background(t.goldContainer)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            ),
        content = content,
    )
}


@Composable
fun LanghuanBookMeta(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = LanghuanTextStyles.bookTitle,
            color = t.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(t.space1))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!metadata.isNullOrBlank()) {
            Spacer(Modifier.height(t.space1))

            Text(
                text = metadata,
                style = LanghuanTextStyles.caption,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Progress                                  */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    height: Dp = 4.dp,
) {
    val t = LocalLanghuanUiTokens.current

    val safeProgress = progress.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "langhuanProgress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(t.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(height)
                .clip(CircleShape)
                .background(accent ?: t.primary),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Icon Tile                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    accent: Boolean = false,
    size: Dp = 40.dp,
) {
    val t = LocalLanghuanUiTokens.current

    val background = if (accent) {
        t.accent
    } else {
        t.input
    }

    val foreground = tint ?: if (accent) {
        t.accentForeground
    } else {
        t.secondaryForeground
    }

    val shape = RoundedCornerShape(t.radiusSm)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(
                if (size >= 40.dp) {
                    20.dp
                } else {
                    18.dp
                },
            ),
            tint = foreground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Inline Action                                */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current

    val interaction = remember {
        MutableInteractionSource()
    }

    val pressed by interaction.collectIsPressedAsState()

    val color by animateColorAsState(
        targetValue = if (pressed) {
            t.primary.copy(alpha = 0.68f)
        } else {
            t.primary
        },
        animationSpec = tween(LanghuanMotionV31.FAST),
        label = "langhuanTextActionColor",
    )

    Text(
        text = text,
        modifier = modifier
            .clip(
                RoundedCornerShape(t.radiusSm),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space2,
                vertical = t.space1,
            ),
        style = MaterialTheme.typography.labelLarge,
        color = color.copy(
            alpha = if (enabled) 1f else 0.4f,
        ),
    )
}


/* -------------------------------------------------------------------------- */
/*                           Lightweight Container                            */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanSurface(
    modifier: Modifier = Modifier,
    warm: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 16.dp,
        vertical = 12.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(t.radiusMd)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (warm) {
                    t.goldContainer
                } else {
                    t.input
                },
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}


/* -------------------------------------------------------------------------- */
/*                          Gold Editorial Surface                            */
/* -------------------------------------------------------------------------- */

/**
 * 赤金语义内容块。
 *
 * 用于：
 * - 收藏
 * - 阅读成就
 * - 连续阅读
 * - 精选内容
 *
 * 不用于普通操作按钮。
 */
@Composable
fun LanghuanGoldSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 16.dp,
        vertical = 12.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(t.goldContainer)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}


/* -------------------------------------------------------------------------- */
/*                           Content Color Helpers                            */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanMutedContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    CompositionLocalProvider(
        LocalContentColor provides t.mutedForeground,
    ) {
        Column(
            modifier = modifier,
            content = content,
        )
    }
}


@Composable
fun LanghuanSecondaryContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    CompositionLocalProvider(
        LocalContentColor provides t.secondaryForeground,
    ) {
        Column(
            modifier = modifier,
            content = content,
        )
    }
}

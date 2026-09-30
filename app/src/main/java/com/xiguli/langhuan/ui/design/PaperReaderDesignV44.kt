package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paper-and-ink styling for the reading edition's app pages, never the reading body. */
object PaperReaderPaletteV44 {
    val background = Color(0xFFFAF9F6)
    val ink = Color(0xFF15263E)
    val muted = Color(0xFF757C87)
    val card = Color(0xFFFFFEFC)
    val line = Color(0xFFEAE7E1)
    val accent = Color(0xFF245B9E)
    val accentSoft = Color(0xFFE8EFF7)
}

/** Opt in at the page boundary. Reader typography and legacy pages stay independent. */
@Composable
fun PaperReaderThemeV44(content: @Composable () -> Unit) {
    val p = PaperReaderPaletteV44
    val tokens = LocalLanghuanUiTokens.current.copy(
        background = p.background, foreground = p.ink, card = p.card, cardForeground = p.ink,
        muted = Color(0xFFF1F0EC), mutedForeground = p.muted, strong = p.ink,
        track = p.line, border = p.line, input = Color(0xFFF1F0EC), primary = p.accent,
        primaryForeground = Color.White, accent = p.accentSoft, accentForeground = p.accent,
        ring = p.accent.copy(alpha = .24f), warmSurface = p.background,
        destructive = Color(0xFFB3261E), destructiveForeground = Color.White,
        success = Color(0xFF1B7550), successForeground = Color.White,
        warning = Color(0xFFA7650D), warningForeground = Color.White,
        radiusMd = 18.dp, radiusLg = 20.dp,
    )
    val colors = MaterialTheme.colorScheme.copy(
        primary = p.accent, onPrimary = Color.White, primaryContainer = p.accentSoft,
        onPrimaryContainer = p.ink, secondary = p.accent, onSecondary = Color.White,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        background = p.background, onBackground = p.ink, surface = p.card,
        onSurface = p.ink, surfaceVariant = tokens.muted, onSurfaceVariant = p.muted,
        outline = p.muted, outlineVariant = p.line, surfaceTint = Color.Transparent,
        error = tokens.destructive, onError = Color.White,
    )
    CompositionLocalProvider(LocalLanghuanUiTokens provides tokens) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
fun PaperPageTitleV44(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = PaperReaderPaletteV44
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, Modifier.weight(1f), color = p.ink, fontFamily = FontFamily.Serif,
                fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, Modifier.padding(top = 8.dp), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PaperCardV44(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = PaperReaderPaletteV44
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = p.card,
        contentColor = p.ink,
        border = BorderStroke(.7.dp, p.line),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun PaperIconButtonV44(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val p = PaperReaderPaletteV44
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Surface(
            Modifier.size(42.dp), shape = CircleShape,
            color = if (selected) p.accentSoft else p.card,
            border = BorderStroke(.7.dp, p.line), shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription, Modifier.size(21.dp), tint = if (selected) p.accent else p.ink)
            }
        }
    }
}

@Composable
fun PaperSectionLabelV44(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = PaperReaderPaletteV44.muted, style = MaterialTheme.typography.labelLarge, letterSpacing = .6.sp)
}

@Composable
fun PaperMenuRowV44(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingIcon: ImageVector = Icons.Rounded.ChevronRight,
) {
    val p = PaperReaderPaletteV44
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(25.dp), tint = p.ink)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = p.ink, fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, Modifier.padding(top = 4.dp), color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(trailingIcon, null, Modifier.size(20.dp), tint = p.muted)
    }
}

@Composable
fun PaperDividerV44(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = .7.dp, color = PaperReaderPaletteV44.line)
}

/** Latest app route appearance, read when the reader releases its window ownership. */
private val appWindowAppearanceV44 = java.util.WeakHashMap<android.view.Window, Boolean>()

internal fun restoreAppSystemBarsV44(window: android.view.Window, fallbackStatus: Boolean, fallbackNavigation: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    val appAppearance = appWindowAppearanceV44[window]
    controller.isAppearanceLightStatusBars = appAppearance ?: fallbackStatus
    controller.isAppearanceLightNavigationBars = appAppearance ?: fallbackNavigation
}

/** One owner outside route animations; the reader keeps its own night/immersive appearance. */
@Composable
fun PaperReaderSystemBarsV44(lightBackground: Boolean, readerActive: Boolean = false) {
    var context = LocalContext.current
    while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
    val activity = context as? android.app.Activity ?: return
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    DisposableEffect(window) {
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        onDispose {
            appWindowAppearanceV44.remove(window)
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    SideEffect {
        if (!readerActive) {
            appWindowAppearanceV44[window] = lightBackground
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = lightBackground
        }
    }
}

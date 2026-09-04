package com.xiguli.langhuan.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Semantic status vocabulary shared by AI services, Skills and Run Center. */
enum class LanghuanStatusTone { NEUTRAL, INFO, SUCCESS, WARNING, DESTRUCTIVE }

data class LanghuanStatusColors(val container: Color, val content: Color)

@Composable
fun langhuanStatusColors(tone: LanghuanStatusTone): LanghuanStatusColors {
    val t = LocalLanghuanUiTokens.current
    return when (tone) {
        LanghuanStatusTone.NEUTRAL -> LanghuanStatusColors(t.muted, t.mutedForeground)
        LanghuanStatusTone.INFO -> LanghuanStatusColors(t.accent, t.accentForeground)
        LanghuanStatusTone.SUCCESS -> LanghuanStatusColors(t.success.copy(alpha = .12f), t.success)
        LanghuanStatusTone.WARNING -> LanghuanStatusColors(t.warning.copy(alpha = .14f), t.warning)
        LanghuanStatusTone.DESTRUCTIVE -> LanghuanStatusColors(t.destructive.copy(alpha = .12f), t.destructive)
    }
}

@Composable
fun LanghuanSectionLabelV2(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
fun LanghuanGroupedSurfaceV2(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = t.card,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column { content() }
    }
}

@Composable
fun LanghuanActionRowV2(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: LanghuanStatusTone = LanghuanStatusTone.INFO,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val status = langhuanStatusColors(tone)
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(34.dp), shape = RoundedCornerShape(11.dp), color = status.container) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = status.content)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun LanghuanStatusDot(tone: LanghuanStatusTone, modifier: Modifier = Modifier) {
    val colors = langhuanStatusColors(tone)
    Surface(modifier = modifier.size(8.dp), shape = CircleShape, color = colors.content) {}
}

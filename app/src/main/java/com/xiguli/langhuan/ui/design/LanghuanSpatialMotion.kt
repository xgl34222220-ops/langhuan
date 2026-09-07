package com.xiguli.langhuan.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight spatial-motion vocabulary inspired by modern Three.js component galleries.
 *
 * Langhuan stays a native Compose app: these primitives deliberately avoid WebView/WebGL so the
 * writing/reading surfaces remain fast, battery-friendly and accessible. Motion is ambient only;
 * it never owns navigation or critical interaction state.
 */
@Composable
fun LanghuanAmbientBackdrop(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "langhuan-ambient")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 14_000 else 60_000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient-phase",
    ).value

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    t.background,
                    t.warmSurface.copy(alpha = .94f),
                    t.background,
                )
            )
        )

        val shortest = min(size.width, size.height)
        if (shortest <= 0f) return@Canvas
        val angle = phase * (2f * PI.toFloat())
        val c1 = Offset(
            x = size.width * (.24f + .08f * sin(angle)),
            y = size.height * (.22f + .05f * cos(angle * .83f)),
        )
        val c2 = Offset(
            x = size.width * (.78f + .06f * cos(angle * .71f)),
            y = size.height * (.72f + .08f * sin(angle * .91f)),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = .12f), primary.copy(alpha = .035f), Color.Transparent),
                center = c1,
                radius = shortest * .72f,
            ),
            center = c1,
            radius = shortest * .72f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = .12f), tertiary.copy(alpha = .03f), Color.Transparent),
                center = c2,
                radius = shortest * .66f,
            ),
            center = c2,
            radius = shortest * .66f,
        )
    }
}

@Composable
fun LanghuanConstellationField(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "langhuan-constellation")
    val pulse = transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 3_200 else 12_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "constellation-pulse",
    ).value

    val points = listOf(
        .08f to .28f, .18f to .14f, .30f to .34f, .43f to .18f,
        .56f to .38f, .68f to .20f, .82f to .31f, .92f to .16f,
        .14f to .70f, .31f to .60f, .49f to .76f, .67f to .63f,
        .83f to .78f, .94f to .58f,
    )
    val links = listOf(
        0 to 1, 0 to 2, 1 to 3, 2 to 3, 2 to 4, 3 to 5, 4 to 5,
        5 to 6, 6 to 7, 0 to 8, 2 to 9, 8 to 9, 9 to 10, 4 to 11,
        10 to 11, 11 to 12, 6 to 13, 12 to 13,
    )

    Canvas(modifier = modifier) {
        val resolved = points.map { (x, y) -> Offset(size.width * x, size.height * y) }
        links.forEachIndexed { index, (from, to) ->
            val alpha = .08f + ((index % 4) * .014f) + pulse * .035f
            drawLine(
                color = t.foreground.copy(alpha = alpha),
                start = resolved[from],
                end = resolved[to],
                strokeWidth = 1.dp.toPx(),
            )
        }
        resolved.forEachIndexed { index, point ->
            val hot = index % 5 == 0
            drawCircle(
                color = if (hot) primary.copy(alpha = .34f + pulse * .18f)
                else t.foreground.copy(alpha = .14f + pulse * .06f),
                radius = if (hot) 2.2.dp.toPx() else 1.4.dp.toPx(),
                center = point,
            )
        }
    }
}

@Composable
fun LanghuanOrb(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    size: Dp = 34.dp,
) {
    val t = LocalLanghuanUiTokens.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "langhuan-orb")
    val pulse = transition.animateFloat(
        initialValue = .92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 1_900 else 8_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    ).value
    val drift = transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 2_700 else 10_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-drift",
    ).value

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                translationY = drift
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = min(this.size.width, this.size.height) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = .28f), primary.copy(alpha = .06f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = .75f), primary.copy(alpha = .34f), t.card.copy(alpha = .1f)),
                    center = Offset(center.x * .82f, center.y * .76f),
                    radius = radius * .62f,
                ),
                radius = radius * .52f,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = .72f),
                radius = radius * .10f,
                center = Offset(center.x - radius * .17f, center.y - radius * .20f),
            )
        }
    }
}

@Composable
fun LanghuanGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = t.card.copy(alpha = .74f),
        contentColor = t.cardForeground,
        border = BorderStroke(1.dp, t.border.copy(alpha = .58f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun LanghuanSpatialHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    active: Boolean = true,
    trailing: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        contentColor = t.cardForeground,
        border = BorderStroke(1.dp, t.border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box {
            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = active)
            LanghuanConstellationField(Modifier.fillMaxSize(), active = active)
            Box(Modifier.fillMaxSize(), content = trailing)
            Column(Modifier.padding(22.dp)) {
                if (!eyebrow.isNullOrBlank()) {
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = t.mutedForeground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = t.foreground,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
                content()
            }
        }
    }
}

@Composable
fun LanghuanMotionStatus(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val t = LocalLanghuanUiTokens.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LanghuanOrb(active = active, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            fontWeight = FontWeight.Medium,
        )
    }
}

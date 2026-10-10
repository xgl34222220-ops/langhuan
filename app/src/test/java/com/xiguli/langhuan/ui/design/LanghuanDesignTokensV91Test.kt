package com.xiguli.langhuan.ui.design

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10 UI refresh: zinc neutrals, one restrained accent, a fixed radius / spacing scale and
 * motion tokens that collapse to snap when the system removes animations.
 */
class LanghuanDesignTokensV91Test {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertReadable(label: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue("$label contrast $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun everyTextTokenMeetsWcagAaOnEverySurfaceInBothThemes() {
        listOf("light" to LanghuanLightUiTokens, "dark" to LanghuanDarkUiTokens).forEach { (name, t) ->
            val surfaces = listOf("background" to t.background, "card" to t.card, "input" to t.input)
            val texts = listOf(
                "foreground" to t.foreground,
                "secondary" to t.secondaryForeground,
                "muted" to t.mutedForeground,
                "primary" to t.primary,
                "destructive" to t.destructive,
                "goldForeground" to t.goldForeground,
            )
            surfaces.forEach { (surfaceName, surface) ->
                texts.forEach { (textName, text) -> assertReadable("$name $textName on $surfaceName", text, surface) }
            }
            assertReadable("$name primaryForeground on primary", t.primaryForeground, t.primary)
            assertReadable("$name accentForeground on accent", t.accentForeground, t.accent)
            assertReadable("$name goldForeground on goldContainer", t.goldForeground, t.goldContainer)
            assertReadable("$name destructiveForeground on destructive", t.destructiveForeground, t.destructive)
            // Ink pills (selected shelf tab / organizer choice / continue-reading CTA).
            assertReadable("$name background on foreground", t.background, t.foreground)
        }
    }

    @Test
    fun neutralsAreZincGreysAndTheAccentStaysSingle() {
        val l = LanghuanLightUiTokens
        val d = LanghuanDarkUiTokens
        assertEquals(Color(0xFFFAFAFA), l.background)
        assertEquals(Color(0xFF09090B), d.background)
        // Neutrals carry no hue: R, G and B stay within a few steps of each other.
        listOf(l.background, l.card, l.input, l.border, l.foreground, d.background, d.card, d.input, d.foreground)
            .forEach { c ->
                val spread = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
                assertTrue("neutral $c drifts", spread <= 0.05f)
            }
        // Hairline borders separate layers: visible against card, but quieter than any text.
        assertTrue(contrast(l.border, l.card) in 1.15..2.0)
        assertTrue(contrast(d.border, d.card) in 1.15..2.0)
        // One accent: ring, success and primary are the same jade.
        assertEquals(l.primary, l.ring)
        assertEquals(l.primary, l.success)
        assertEquals(d.primary, d.ring)
    }

    @Test
    fun radiusAndSpacingScalesAreFixed() {
        listOf(LanghuanLightUiTokens, LanghuanDarkUiTokens).forEach { t ->
            assertEquals(listOf(8.dp, 12.dp, 16.dp, 24.dp), listOf(t.radiusSm, t.radiusMd, t.radiusLg, t.radiusXl))
            assertEquals(
                listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp),
                listOf(t.space1, t.space2, t.space3, t.space4, t.space5, t.space6),
            )
        }
    }

    @Test
    fun motionTokensAreOrderedAndCollapseWhenAnimationsAreRemoved() {
        val m = LanghuanMotion
        assertTrue(m.DURATION_INSTANT < m.DURATION_QUICK)
        assertTrue(m.DURATION_QUICK < m.DURATION_STANDARD)
        assertTrue(m.DURATION_STANDARD < m.DURATION_EMPHASIZED)
        assertTrue(m.DURATION_EMPHASIZED <= 300)
        assertTrue(m.exitDuration(m.DURATION_STANDARD) < m.DURATION_STANDARD)
        assertTrue(m.PRESS_SCALE in 0.95f..0.99f)

        val enter = m.enter<Float>()
        assertTrue(enter is TweenSpec<*>)
        assertEquals(m.DURATION_STANDARD, (enter as TweenSpec<*>).durationMillis)
        assertTrue(m.enter<Float>(reduced = true) is SnapSpec<*>)
        assertTrue(m.exit<Float>(reduced = true) is SnapSpec<*>)
        assertTrue(m.standard<Float>(reduced = true) is SnapSpec<*>)
        assertEquals(androidx.compose.animation.EnterTransition.None, m.sheetEnter(reduced = true))
        assertEquals(androidx.compose.animation.ExitTransition.None, m.sheetExit(reduced = true))
        assertEquals(androidx.compose.animation.EnterTransition.None, m.popEnter(reduced = true))
    }

    @Test
    fun reducedMotionIsProvidedByTheThemeAndHonouredByRefreshedSurfaces() {
        val root = File(System.getProperty("user.dir") ?: ".")
        fun source(path: String) = File(root, "src/main/java/com/xiguli/langhuan/ui/$path").readText()
        val theme = source("theme/LanghuanStableTheme.kt")
        assertTrue(theme.contains("LocalLanghuanReducedMotion provides reducedMotion"))
        val home = source("LanghuanHomeV4.kt")
        assertTrue(home.contains("LanghuanMotion.revealEnter(reducedMotion)"))
        assertTrue(home.contains("enterOnceV31(enter, book.id, index"))
        assertFalse(home.contains("fadeIn(tween(160))"))
        val menu = source("reader/ReaderMenuV30.kt")
        assertTrue(menu.contains("LocalLanghuanReducedMotion.current"))
        assertTrue(menu.contains("label = \"readerTabIndicator\""))
        val rootRoute = source("LanghuanRootV4.kt")
        assertTrue(rootRoute.contains("rootRouteTransitionV30(initialState, targetState, reducedMotion)"))
        // Reader body typesetting/pagination is untouched by the chrome refresh.
        val engine = source("reader/ReaderLayoutEngineV30.kt")
        assertFalse(engine.contains("LanghuanMotion"))
    }
}

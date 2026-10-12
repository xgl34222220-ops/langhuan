package com.xiguli.langhuan.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private data class ReaderWindowOptionsV27(
    val immersive: Boolean, val keepScreen: Boolean, val portrait: Boolean, val night: Boolean,
    /** V101 状态栏 / 导航栏 toggles; 沉浸式 hides both regardless. */
    val statusBar: Boolean = true, val navigationBar: Boolean = true,
    /** V92 window brightness override; BRIGHTNESS_OVERRIDE_NONE follows the system. */
    val brightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
)

internal fun Context.readerActivityV27(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.readerActivityV27()
    else -> null
}

/** The window belongs to a reading session, never to an individual chapter. */
@Composable
internal fun ReaderWindowSessionV27(resumed: Boolean) {
    val context = LocalContext.current
    val activity = context.readerActivityV27() ?: return
    val prefs = remember(context) { context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE) }
    fun readOptions() = ReaderWindowOptionsV27(
        prefs.getBoolean("immersive", false), prefs.getBoolean("keepScreen", false),
        prefs.getBoolean("lockPortrait", true),
        if (prefs.contains(ReaderSettingsV30.KEY_NIGHT_MODE)) prefs.getBoolean(ReaderSettingsV30.KEY_NIGHT_MODE, false)
        else prefs.getString("theme", "paper") == "night",
        prefs.getBoolean(ReaderSettingsV30.KEY_STATUS_BAR, true),
        prefs.getBoolean(ReaderSettingsV30.KEY_NAVIGATION_BAR, true),
        readerWindowBrightnessV92(
            runCatching { prefs.getFloat(ReaderSettingsV30.KEY_BRIGHTNESS, READER_BRIGHTNESS_SYSTEM_V92) }
                .getOrDefault(READER_BRIGHTNESS_SYSTEM_V92),
        ),
    )
    var options by remember(prefs) { mutableStateOf(readOptions()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf(
                    "immersive", "keepScreen", "lockPortrait", "theme", ReaderSettingsV30.KEY_BRIGHTNESS,
                    ReaderSettingsV30.KEY_NIGHT_MODE, ReaderSettingsV30.KEY_STATUS_BAR, ReaderSettingsV30.KEY_NAVIGATION_BAR,
                )
            ) {
                options = readOptions()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val window = activity.window
    val controller = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
    DisposableEffect(window) {
        val oldLightStatus = controller.isAppearanceLightStatusBars
        val oldLightNavigation = controller.isAppearanceLightNavigationBars
        val oldOrientation = activity.requestedOrientation
        val oldKeepScreen = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val oldBehavior = controller.systemBarsBehavior
        val oldBrightness = window.attributes.screenBrightness
        // Runs only when leaving the reader, not when key(chapterKey) is replaced.
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            com.xiguli.langhuan.ui.design.restoreAppSystemBarsV44(window, oldLightStatus, oldLightNavigation)
            controller.systemBarsBehavior = oldBehavior
            activity.requestedOrientation = oldOrientation
            if (oldKeepScreen) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
        }
    }
    // Brightness has its own effect: dragging the slider must not re-run the bar/orientation code.
    LaunchedEffect(window, options.brightness) {
        if (window.attributes.screenBrightness != options.brightness) {
            window.attributes = window.attributes.apply { screenBrightness = options.brightness }
        }
    }
    LaunchedEffect(options.copy(brightness = 0f), resumed) {
        controller.isAppearanceLightStatusBars = !options.night
        controller.isAppearanceLightNavigationBars = !options.night
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (options.immersive || !options.statusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
        if (options.immersive || !options.navigationBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
        else controller.show(WindowInsetsCompat.Type.navigationBars())
        if (options.keepScreen) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.requestedOrientation = if (options.portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

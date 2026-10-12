package com.xiguli.langhuan.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * V102 thin line icons (1.5 px on a 24 px grid, round caps) for the shelf, sheets, 图书详情 and
 * 我的, matching the hairline glyphs of the reference instead of Material's 2 px outlines.
 * Every icon is drawn in black and tinted by [androidx.compose.material3.Icon].
 */
object ThinIconsV102 {
    private const val STROKE = 1.5f

    private fun icon(name: String, vararg paths: String, width: Float = STROKE): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = addPathNodes(data),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val Add: ImageVector by lazy { icon("thin.add", "M12 4.5v15M4.5 12h15") }
    val Person: ImageVector by lazy {
        icon("thin.person", "M12 11.5a3.75 3.75 0 1 0 0-7.5a3.75 3.75 0 1 0 0 7.5z", "M4.75 20c.9-3.4 3.8-5.25 7.25-5.25S18.35 16.6 19.25 20")
    }
    val Search: ImageVector by lazy { icon("thin.search", "M10.75 17.5a6.75 6.75 0 1 0 0-13.5a6.75 6.75 0 1 0 0 13.5z", "M15.7 15.7L20 20") }
    val More: ImageVector by lazy { icon("thin.more", "M5.5 12h.01M12 12h.01M18.5 12h.01", width = 2.6f) }
    val Back: ImageVector by lazy { icon("thin.back", "M14.5 5l-7 7l7 7") }
    val Check: ImageVector by lazy { icon("thin.check", "M5 12.5l4.5 4.5L19 7.5") }
    val Detail: ImageVector by lazy {
        icon(
            "thin.detail",
            "M6.5 3.75h11a1.75 1.75 0 0 1 1.75 1.75v13a1.75 1.75 0 0 1-1.75 1.75h-11a1.75 1.75 0 0 1-1.75-1.75v-13a1.75 1.75 0 0 1 1.75-1.75z",
            "M8.75 8.5h6.5M8.75 12h6.5M8.75 15.5h3.75",
        )
    }
    val Move: ImageVector by lazy {
        icon(
            "thin.move",
            "M3.75 6.75v10.5a1.75 1.75 0 0 0 1.75 1.75h13a1.75 1.75 0 0 0 1.75-1.75v-8a1.75 1.75 0 0 0-1.75-1.75h-6.75L9.9 5H5.5a1.75 1.75 0 0 0-1.75 1.75z",
            "M9.5 13.25h5.5M13 11l2.25 2.25L13 15.5",
        )
    }
    val Delete: ImageVector by lazy {
        icon(
            "thin.delete",
            "M4.5 6.75h15M9.5 6.75v-2h5v2",
            "M6.5 6.75l.8 11.9a1.6 1.6 0 0 0 1.6 1.5h6.2a1.6 1.6 0 0 0 1.6-1.5l.8-11.9",
            "M10 10.75v5.5M14 10.75v5.5",
        )
    }
    val Book: ImageVector by lazy {
        icon(
            "thin.book",
            "M5 18.75V5.5a1.75 1.75 0 0 1 1.75-1.75H19v13H6.75A1.75 1.75 0 0 0 5 18.5a1.75 1.75 0 0 0 1.75 1.75H19",
            "M9 8h6",
        )
    }
    val Image: ImageVector by lazy {
        icon(
            "thin.image",
            "M5.5 4.75h13a1.75 1.75 0 0 1 1.75 1.75v11a1.75 1.75 0 0 1-1.75 1.75h-13a1.75 1.75 0 0 1-1.75-1.75v-11a1.75 1.75 0 0 1 1.75-1.75z",
            "M3.75 15.5l4.5-4.25l3.75 3.5l2.5-2.25l5.75 5",
            "M15.5 9.5h.01",
        )
    }
    val Chart: ImageVector by lazy { icon("thin.chart", "M5 19.25h14", "M7.75 16v-4.5M12 16V7.75M16.25 16v-6.5") }
    val Quote: ImageVector by lazy {
        icon("thin.quote", "M5 6.75h14M5 11.25h14M5 15.75h8.5", "M16.5 15.25l1.5 1.5l3-3")
    }
    val Done: ImageVector by lazy {
        icon("thin.done", "M12 20.25a8.25 8.25 0 1 0 0-16.5a8.25 8.25 0 1 0 0 16.5z", "M8.25 12.25l2.6 2.6l4.9-5.1")
    }
    val Settings: ImageVector by lazy {
        icon(
            "thin.settings",
            "M12 15a3 3 0 1 0 0-6a3 3 0 1 0 0 6z",
            "M12 3.5l1.6 1.9l2.45-.45l.85 2.35l2.35.85l-.45 2.45l1.9 1.6l-1.9 1.6l.45 2.45l-2.35.85l-.85 2.35l-2.45-.45L12 20.5l-1.6-1.9l-2.45.45l-.85-2.35l-2.35-.85l.45-2.45L3.5 12l1.9-1.6l-.45-2.45l2.35-.85l.85-2.35l2.45.45z",
        )
    }
    val Chevron: ImageVector by lazy { icon("thin.chevron", "M9.5 6l6 6l-6 6") }

    /* V103 · reader 更多 grid: the same 1.5 px hairline family replaces Material Outlined (2 px). */
    private const val PHONE = "M8 3.75h8a1.25 1.25 0 0 1 1.25 1.25v14a1.25 1.25 0 0 1-1.25 1.25H8a1.25 1.25 0 0 1-1.25-1.25V5A1.25 1.25 0 0 1 8 3.75z"
    val Shirt: ImageVector by lazy {
        icon("thin.shirt", "M8.75 4.5L4 7.25l1.75 3.25l2.25-1v10h8v-10l2.25 1L20 7.25L15.25 4.5c-.6 1.35-1.85 2.1-3.25 2.1s-2.65-.75-3.25-2.1z")
    }
    val Typeface: ImageVector by lazy { icon("thin.typeface", "M5.5 7V5h13v2", "M12 5v14", "M9.5 19h5") }
    val TextSize: ImageVector by lazy { icon("thin.text_size", "M3.5 18.5l5-13l5 13", "M5.4 13.75h6.2", "M14.75 18.5l3-7.25l3 7.25", "M15.85 16h3.8") }
    val LineSpacing: ImageVector by lazy {
        icon("thin.line_spacing", "M6 5v14", "M3.75 7.25L6 5l2.25 2.25", "M3.75 16.75L6 19l2.25-2.25", "M11.5 7h8.5M11.5 12h8.5M11.5 17h8.5")
    }
    val Pin: ImageVector by lazy {
        icon(
            "thin.pin",
            "M12 20.75s-6.25-5.55-6.25-10.5a6.25 6.25 0 1 1 12.5 0c0 4.95-6.25 10.5-6.25 10.5z",
            "M12 12.25a2.25 2.25 0 1 0 0-4.5a2.25 2.25 0 1 0 0 4.5z",
        )
    }
    val SwapVertical: ImageVector by lazy {
        icon("thin.swap_vertical", "M8 19V5", "M4.75 8.25L8 5l3.25 3.25", "M16 5v14", "M12.75 15.75L16 19l3.25-3.25")
    }
    val OpenBook: ImageVector by lazy {
        icon(
            "thin.open_book",
            "M12 6.75C10 5.25 7 5 3.75 5.5v13c3.25-.5 6.25-.25 8.25 1.25c2-1.5 5-1.75 8.25-1.25v-13C17 5 14 5.25 12 6.75z",
            "M12 6.75v13",
        )
    }
    val Volume: ImageVector by lazy {
        icon("thin.volume", "M4.5 9.5h3.25L12 6v12l-4.25-3.5H4.5z", "M15.25 9.25a3.9 3.9 0 0 1 0 5.5", "M17.75 6.75a7.4 7.4 0 0 1 0 10.5")
    }
    val ScreenOn: ImageVector by lazy { icon("thin.screen_on", PHONE, "M10.75 17.25h2.5", "M12 8.25v1M12 13v.01") }
    val Clock: ImageVector by lazy {
        icon("thin.clock", "M12 20.25a8.25 8.25 0 1 0 0-16.5a8.25 8.25 0 1 0 0 16.5z", "M12 7.5V12l3 2")
    }
    val Immersive: ImageVector by lazy {
        icon(
            "thin.immersive",
            "M4.5 9V5.75c0-.7.55-1.25 1.25-1.25H9",
            "M15 4.5h3.25c.7 0 1.25.55 1.25 1.25V9",
            "M19.5 15v3.25c0 .7-.55 1.25-1.25 1.25H15",
            "M9 19.5H5.75c-.7 0-1.25-.55-1.25-1.25V15",
        )
    }
    val Tap: ImageVector by lazy {
        icon(
            "thin.tap",
            "M12 13.25a1.25 1.25 0 1 0 0-2.5a1.25 1.25 0 1 0 0 2.5z",
            "M12 16.75a4.75 4.75 0 1 0 0-9.5a4.75 4.75 0 1 0 0 9.5z",
            "M4.5 12a7.5 7.5 0 0 1 15 0",
        )
    }
    val Bookmark: ImageVector by lazy { icon("thin.bookmark", "M7 4h10a.75.75 0 0 1 .75.75V20L12 16l-5.75 4V4.75A.75.75 0 0 1 7 4z") }
    val NextPage: ImageVector by lazy {
        icon(
            "thin.next_page",
            "M6.75 3.75h10.5a1 1 0 0 1 1 1v14.5a1 1 0 0 1-1 1H6.75a1 1 0 0 1-1-1V4.75a1 1 0 0 1 1-1z",
            "M10.5 9.25L13.25 12l-2.75 2.75",
        )
    }
    val Shade: ImageVector by lazy {
        icon("thin.shade", "M12 20.25a8.25 8.25 0 1 0 0-16.5a8.25 8.25 0 1 0 0 16.5z", "M12 3.75v16.5", "M12 8h5.25M12 12h7M12 16h5.25")
    }
    val Landscape: ImageVector by lazy {
        icon("thin.landscape", "M3.5 18.5l5.25-8l3.5 5l2.25-3l6 6z", "M16.5 8.25a1.75 1.75 0 1 0 0-3.5a1.75 1.75 0 1 0 0 3.5z")
    }
    val StatusBar: ImageVector by lazy { icon("thin.status_bar", PHONE, "M6.75 7.25h10.5") }
    val NavigationBar: ImageVector by lazy { icon("thin.navigation_bar", PHONE, "M6.75 16.75h10.5") }
    val Lock: ImageVector by lazy {
        icon(
            "thin.lock",
            "M7 10.75h10a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-7.5a1 1 0 0 1 1-1z",
            "M8.75 10.75V8a3.25 3.25 0 0 1 6.5 0v2.75",
            "M12 14.5v2",
        )
    }
    val Headphones: ImageVector by lazy {
        icon(
            "thin.headphones",
            "M4.75 16v-3.5a7.25 7.25 0 0 1 14.5 0V16",
            "M4.75 14.5h2a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-.75a1.25 1.25 0 0 1-1.25-1.25z",
            "M19.25 14.5h-2a1 1 0 0 0-1 1v3a1 1 0 0 0 1 1h.75a1.25 1.25 0 0 0 1.25-1.25z",
        )
    }
    val Carousel: ImageVector by lazy {
        icon(
            "thin.carousel",
            "M8.5 5.5h7a.75.75 0 0 1 .75.75v11.5a.75.75 0 0 1-.75.75h-7a.75.75 0 0 1-.75-.75V6.25a.75.75 0 0 1 .75-.75z",
            "M4.25 7.75v8.5M19.75 7.75v8.5",
        )
    }
    val Pencil: ImageVector by lazy { icon("thin.pencil", "M14.75 5.25l4 4L9.25 18.75h-4v-4z", "M12.75 7.25l4 4") }
}

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
}

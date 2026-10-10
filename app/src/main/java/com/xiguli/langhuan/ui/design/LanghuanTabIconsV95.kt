package com.xiguli.langhuan.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * V96 custom tab glyphs for 书架 / 书城 / 创作 / 我的, drawn on a 24-unit grid with a 1.5 stroke
 * and round caps/joins (SF Symbols "regular" weight at 24 dp). 书架 = books on a shelf, 书城 = a
 * storefront under a scalloped awning, 创作 = a calligraphy brush (毛笔, echoing the ink-bamboo
 * brand), 我的 = a person. Each destination has an outline glyph (unselected) and a filled glyph
 * (selected) with exactly the same silhouette: filled shapes are filled *and* stroked with the same
 * 1.5 line, so the swap reads as ink flooding the outline, never as a size jump. Paint is black;
 * callers tint (Icon does). Source of truth for the shapes: tools/brand/tab_icons_v96.py.
 */
object LanghuanTabIconsV95 {
    val ShelfOutline: ImageVector by lazy {
        tabIcon("ShelfOutline") {
            strokePath("M5.25,4.25H7.25A1,1 0 0 1 8.25,5.25V18.75A1,1 0 0 1 7.25,19.75H5.25A1,1 0 0 1 4.25,18.75V5.25A1,1 0 0 1 5.25,4.25Z")
            strokePath("M10.75,6.75H12.75A1,1 0 0 1 13.75,7.75V18.75A1,1 0 0 1 12.75,19.75H10.75A1,1 0 0 1 9.75,18.75V7.75A1,1 0 0 1 10.75,6.75Z")
            strokePath("M15.2,18.7L18.64,19.75L21.86,9.23A1,1 0 0 0 21.19,7.98L19.66,7.51A1,1 0 0 0 18.42,8.18Z")
            strokePath("M2.75,19.75H21.25")
            strokePath("M4.25,8.25H8.25")
            strokePath("M9.75,10.25H13.75")
        }
    }

    val ShelfFilled: ImageVector by lazy {
        tabIcon("ShelfFilled") {
            fillStrokePath("M5.25,4.25H7.25A1,1 0 0 1 8.25,5.25V18.75A1,1 0 0 1 7.25,19.75H5.25A1,1 0 0 1 4.25,18.75V5.25A1,1 0 0 1 5.25,4.25ZM5.3,7.6H7.2A0.3,0.3 0 0 1 7.5,7.9V8.6A0.3,0.3 0 0 1 7.2,8.9H5.3A0.3,0.3 0 0 1 5,8.6V7.9A0.3,0.3 0 0 1 5.3,7.6Z")
            fillStrokePath("M10.75,6.75H12.75A1,1 0 0 1 13.75,7.75V18.75A1,1 0 0 1 12.75,19.75H10.75A1,1 0 0 1 9.75,18.75V7.75A1,1 0 0 1 10.75,6.75ZM10.8,9.6H12.7A0.3,0.3 0 0 1 13,9.9V10.6A0.3,0.3 0 0 1 12.7,10.9H10.8A0.3,0.3 0 0 1 10.5,10.6V9.9A0.3,0.3 0 0 1 10.8,9.6Z")
            fillStrokePath("M15.2,18.7L18.64,19.75L21.86,9.23A1,1 0 0 0 21.19,7.98L19.66,7.51A1,1 0 0 0 18.42,8.18Z")
            strokePath("M2.75,19.75H21.25")
        }
    }

    val StoreOutline: ImageVector by lazy {
        tabIcon("StoreOutline") {
            strokePath("M3.75,9.25L5.25,4.75H18.75L20.25,9.25a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0Z")
            strokePath("M5.25,12V19.25A1,1 0 0 0 6.25,20.25H17.75A1,1 0 0 0 18.75,19.25V12")
            strokePath("M10,20.25V16.25A2,2 0 0 1 14,16.25V20.25")
        }
    }

    val StoreFilled: ImageVector by lazy {
        tabIcon("StoreFilled") {
            fillStrokePath("M3.75,9.25L5.25,4.75H18.75L20.25,9.25a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0a2.06,2.06 0 0 1 -4.12,0Z")
            fillStrokePath("M5.25,12.25H18.75V19.25A1,1 0 0 1 17.75,20.25H6.25A1,1 0 0 1 5.25,19.25ZM9.4,21V16.25A2.6,2.6 0 0 1 14.6,16.25V21Z")
        }
    }

    val CreateOutline: ImageVector by lazy {
        tabIcon("CreateOutline") {
            strokePath("M19.6,4.4L13.8,10.2")
            strokePath("M12.71,9.1 L14.9,11.29 L13.41,12.78 L11.22,10.59Z")
            strokePath("M11.22,10.59C8.57,11.19 5.6,15.15 4.43,20.06C8.85,18.82 12.74,15.64 13.41,12.78Z")
        }
    }

    val CreateFilled: ImageVector by lazy {
        tabIcon("CreateFilled") {
            strokePath("M19.6,4.4L13.8,10.2")
            fillStrokePath("M12.71,9.1 L14.9,11.29 L13.41,12.78 L11.22,10.59Z")
            fillStrokePath("M11.22,10.59C8.57,11.19 5.6,15.15 4.43,20.06C8.85,18.82 12.74,15.64 13.41,12.78Z")
        }
    }

    val MineOutline: ImageVector by lazy {
        tabIcon("MineOutline") {
            strokePath("M12,4.25A3.75,3.75 0 1 1 11.99,4.25Z")
            strokePath("M4.75,20.25C5.45,16.55 8.35,14.25 12,14.25S18.55,16.55 19.25,20.25")
        }
    }

    val MineFilled: ImageVector by lazy {
        tabIcon("MineFilled") {
            fillStrokePath("M12,4.25A3.75,3.75 0 1 1 11.99,4.25Z")
            fillStrokePath("M4.75,20.25C5.45,16.55 8.35,14.25 12,14.25S18.55,16.55 19.25,20.25Z")
        }
    }
}

internal const val TAB_ICON_STROKE_V96 = 1.5f

private class TabIconScopeV95(val builder: ImageVector.Builder) {
    fun strokePath(d: String) {
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = TAB_ICON_STROKE_V96,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fillPath(d: String) {
        builder.addPath(pathData = addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
    }

    /** Fill (even-odd, so inner sub-paths are knock-outs) plus the outer outline at stroke width. */
    fun fillStrokePath(d: String) {
        fillPath(d)
        strokePath(d.substringBefore('Z') + "Z")
    }
}

private fun tabIcon(name: String, block: TabIconScopeV95.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(
        name = "LanghuanTab.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    TabIconScopeV95(builder).block()
    return builder.build()
}

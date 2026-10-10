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
 * V95 custom tab glyphs for 书架 / 书城 / 创作 / 我的, drawn on a 24-unit grid with a 1.6 stroke
 * (round caps and joins) to sit between SF Symbols and Material Symbols. Each destination has an
 * outline glyph (unselected) and a filled glyph (selected) with the same silhouette, so the swap
 * reads as a fill rather than a different icon. Paint is black; callers tint (Icon does).
 * Source of truth for the shapes: tools/brand/tab_icons.py.
 */
object LanghuanTabIconsV95 {
    val ShelfOutline: ImageVector by lazy {
        tabIcon("ShelfOutline") {
        strokePath("M5,4.5h3a1,1 0 0 1 1,1v13.5h-5v-13.5a1,1 0 0 1 1,-1z")
        strokePath("M10.5,7.5h3a1,1 0 0 1 1,1v10.5h-5v-10.5a1,1 0 0 1 1,-1z")
        strokePath("M16.1,7.9l2.4,-0.65a0.9,0.9 0 0 1 1.1,0.64l2.5,9.4l-3.6,0.96l-2.5,-9.25a0.9,0.9 0 0 1 0.1,-1.1z")
        strokePath("M3,21h18")
        strokePath("M6.5,8v1.6")
        }
    }

    val ShelfFilled: ImageVector by lazy {
        tabIcon("ShelfFilled") {
        fillPath("M5.1,3.7h2.2a1.6,1.6 0 0 1 1.6,1.6v14.5h-5.4v-14.5a1.6,1.6 0 0 1 1.6,-1.6z M5.4,7.4h1.6v2.6h-1.6z")
        fillPath("M11.6,6.7h2.2a1.6,1.6 0 0 1 1.6,1.6v11.5h-5.4v-11.5a1.6,1.6 0 0 1 1.6,-1.6z")
        fillPath("M17.1,7.6l1.6,-0.43a1.5,1.5 0 0 1 1.85,1.06l2.6,9.7l-4.6,1.23l-2.5,-9.4a1.5,1.5 0 0 1 1.05,-2.16z")
        strokePath("M3,21h18")
        }
    }

    val StoreOutline: ImageVector by lazy {
        tabIcon("StoreOutline") {
        strokePath("M2.8,9.2c3.2,-0.5 6.4,-2.2 9.2,-5.2c2.8,3 6,4.7 9.2,5.2")
        strokePath("M5,9.6v10.9h14v-10.9")
        strokePath("M9.6,20.5v-4.3a2.4,2.4 0 0 1 4.8,0v4.3")
        strokePath("M9.5,12h5")
        }
    }

    val StoreFilled: ImageVector by lazy {
        tabIcon("StoreFilled") {
        fillPath("M12,3.1c-2.8,3 -5.9,4.7 -9,5.3a0.8,0.8 0 0 0 0.25,1.58c0.4,-0.06 0.8,-0.14 1.15,-0.23v10.75a0.9,0.9 0 0 0 0.9,0.9h4.5v-5.2a2.2,2.2 0 0 1 4.4,0v5.2h4.5a0.9,0.9 0 0 0 0.9,-0.9v-10.75c0.37,0.09 0.75,0.17 1.15,0.23a0.8,0.8 0 0 0 0.25,-1.58c-3.1,-0.6 -6.2,-2.3 -9,-5.3z M9.6,11.3h4.8v1.5h-4.8z")
        }
    }

    val CreateOutline: ImageVector by lazy {
        tabIcon("CreateOutline") {
        strokePath("M14.8,4.6l4.6,4.6l-9.6,9.6l-5.3,0.7l0.7,-5.3z")
        strokePath("M12.6,6.8l4.6,4.6")
        strokePath("M13,20.5h7.5")
        }
    }

    val CreateFilled: ImageVector by lazy {
        tabIcon("CreateFilled") {
        fillPath("M15.4,3.6a1,1 0 0 0 -1.3,0l-9.8,9.8a1,1 0 0 0 -0.28,0.57l-0.75,5.6a0.9,0.9 0 0 0 1,1l5.6,-0.75a1,1 0 0 0 0.57,-0.28l9.8,-9.8a1,1 0 0 0 0,-1.3z M13.17,6.23L17.77,10.83L16.63,11.97L12.03,7.37z")
        strokePath("M13,20.5h7.5")
        }
    }

    val MineOutline: ImageVector by lazy {
        tabIcon("MineOutline") {
        strokePath("M12,4.2a3.9,3.9 0 1 0 0.01,0z")
        strokePath("M4.6,20.2c0.8,-3.6 3.7,-5.8 7.4,-5.8s6.6,2.2 7.4,5.8")
        }
    }

    val MineFilled: ImageVector by lazy {
        tabIcon("MineFilled") {
        fillPath("M12,3.4a4.7,4.7 0 1 0 0.01,0z")
        fillPath("M3.9,20.1c0.8,-4 4,-6.5 8.1,-6.5s7.3,2.5 8.1,6.5a0.9,0.9 0 0 1 -0.9,1.1h-14.4a0.9,0.9 0 0 1 -0.9,-1.1z")
        }
    }
}

private class TabIconScopeV95(val builder: ImageVector.Builder) {
    fun strokePath(d: String) {
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fillPath(d: String) {
        builder.addPath(pathData = addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
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

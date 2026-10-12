package com.xiguli.langhuan.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V97 brand (frosted-glass books on an emerald gradient): the glass art must stay inside the
 * adaptive-icon safe zone (66 dp circle) so no launcher mask clips it, the background must be an
 * opaque emerald → mint gradient, the monochrome layer must exist for themed icons, and the splash
 * must show the emerald icon tile on the pale-mint (light) / deep-emerald (dark) surface with a
 * matching starting-window background (no white flash). Writes preview sheets for the PR.
 */
class LauncherIconV97DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test @SdkSuppress(minSdkVersion = 33)
    fun glassBooksFitSafeZoneOnEveryMaskAndHaveThemedLayer() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        assertNotNull(round.monochrome)
        val size = 432
        icon.setBounds(0, 0, size, size)
        val fg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.foreground.draw(Canvas(fg))
        var glass = 0
        var outsideSafeZone = 0
        // AdaptiveIconDrawable bounds are the visible 72 dp viewport (layers are drawn 1.5x around
        // it), so the 66 dp safe-zone circle has radius 33/72 of the bounds.
        val safeRadius = size * 33f / 72f
        for (y in 0 until size) for (x in 0 until size) {
            val c = fg.getPixel(x, y)
            val a = Color.alpha(c)
            if (a < 40) continue
            if (a > 100 && Color.red(c) > 170 && Color.green(c) > 170 && Color.blue(c) > 170) glass++
            val dx = x - size / 2f
            val dy = y - size / 2f
            if (dx * dx + dy * dy > safeRadius * safeRadius) outsideSafeZone++
        }
        assertTrue("Frosted-glass books must be clearly visible ($glass px)", glass > 20000)
        assertEquals("Glass art must stay inside the 66 dp safe zone", 0, outsideSafeZone)
        assertEquals(0, Color.alpha(fg.getPixel(0, 0)))
        fg.recycle()

        // Background layer: opaque emerald gradient, deep at bottom-left, mint at top-right.
        val bg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.background.draw(Canvas(bg))
        val mid = bg.getPixel(size / 4, size / 4)
        val deep = bg.getPixel(size / 8, size * 7 / 8)
        val mint = bg.getPixel(size * 7 / 8, size / 8)
        assertEquals(255, Color.alpha(mid))
        for (p in listOf(mid, deep, mint)) {
            assertTrue("background is emerald green: $p", Color.green(p) > Color.red(p) + 40 && Color.green(p) > Color.blue(p))
        }
        assertTrue("gradient runs deep → mint: ${lum(deep)} vs ${lum(mint)}", lum(mint) - lum(deep) > 40)
        bg.recycle()

        val cell = 432
        val sheet = Bitmap.createBitmap(cell * 4, cell + 56, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(236, 240, 237))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 34, 31); textSize = 24f; textAlign = Paint.Align.CENTER }
        val names = listOf("Circle", "Squircle", "Rounded", "Themed")
        repeat(4) { i ->
            canvas.save()
            canvas.translate((i * cell).toFloat(), 0f)
            val box = RectF(0f, 0f, cell.toFloat(), cell.toFloat())
            val mask = Path().apply {
                when (i) {
                    0, 3 -> addOval(box, Path.Direction.CW)
                    1 -> addRoundRect(box, box.width() * .32f, box.width() * .32f, Path.Direction.CW)
                    else -> addRoundRect(box, box.width() * .16f, box.width() * .16f, Path.Direction.CW)
                }
            }
            canvas.save()
            canvas.clipPath(mask)
            if (i < 3) {
                icon.background.draw(canvas); icon.foreground.draw(canvas)
            } else {
                canvas.drawColor(Color.rgb(207, 222, 244))
                icon.monochrome!!.apply { bounds = icon.foreground.bounds; setTint(Color.rgb(28, 68, 121)); draw(canvas) }
            }
            canvas.restore()
            canvas.drawText(names[i], cell / 2f, cell + 38f, label)
            canvas.restore()
        }
        save(sheet, "v97-launcher-masks.png")
    }

    @Test
    fun splashShowsEmeraldTileOnMintAndEmeraldWithMatchingWindow() {
        val w = 540
        val h = 960
        val sheet = Bitmap.createBitmap(w * 2, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val expected = listOf(Color.rgb(0xEE, 0xF5, 0xF1), Color.rgb(0x0F, 0x2A, 0x22))
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEachIndexed { i, night ->
            val cfg = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val themed = context.createConfigurationContext(cfg)
            val bg = themed.getColor(R.color.splash_bg_v97)
            assertEquals("splash surface colour (mode $night)", expected[i], bg)
            // The starting window paints the same colour as the splash: no white flash.
            val starting = ContextThemeWrapper(themed, R.style.Theme_Langhuan_Starting)
            val attrs = starting.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            val window = attrs.getDrawable(0)
            attrs.recycle()
            assertTrue("starting window background is the splash colour", window is ColorDrawable && window.color == bg)

            canvas.drawRect((i * w).toFloat(), 0f, ((i + 1) * w).toFloat(), h.toFloat(), Paint().apply { color = bg })
            val mark = themed.getDrawable(R.drawable.splash_glass_v97)!!
            val s = 378 // 288 dp icon canvas at the sheet's scale
            val left = i * w + (w - s) / 2
            val top = (h - s) / 2
            mark.setBounds(left, top, left + s, top + s)
            mark.draw(canvas)
            var emerald = 0
            for (y in top until top + s step 2) for (x in left until left + s step 2) {
                val c = sheet.getPixel(x, y)
                if (Color.green(c) > Color.red(c) + 40 && Color.green(c) > Color.blue(c) + 5 && Color.green(c) > 80) emerald++
            }
            assertTrue("emerald icon tile visible (mode $night): $emerald", emerald > 3000)
            val centre = sheet.getPixel(left + s / 2, top + s / 2)
            assertTrue("glass books at the centre of the tile (mode $night): $centre", lum(centre) > 150)
        }
        save(sheet, "v97-splash-light-dark.png")
    }

    private fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    private fun save(bitmap: Bitmap, name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/$name").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}

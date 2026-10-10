package com.xiguli.langhuan.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V95 brand: the moon-gate mark must stay inside the adaptive-icon safe zone (66 dp circle) so no
 * launcher mask clips it, the monochrome layer must exist for themed icons, and the splash mark
 * must render in both light and dark. Writes a preview sheet for the PR.
 */
class LauncherIconV95DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test @SdkSuppress(minSdkVersion = 33)
    fun markFitsSafeZoneOnEveryMaskAndHasThemedLayer() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        assertNotNull(round.monochrome)
        val size = 432
        icon.setBounds(0, 0, size, size)
        val fg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.foreground.draw(Canvas(fg))
        var ivory = 0
        var outsideSafeZone = 0
        val safeRadius = size * 33f / 108f
        for (y in 0 until size) for (x in 0 until size) {
            val c = fg.getPixel(x, y)
            if (Color.alpha(c) < 40) continue
            if (Color.red(c) > 220 && Color.green(c) > 215 && Color.blue(c) > 190) ivory++
            val dx = x - size / 2f
            val dy = y - size / 2f
            if (dx * dx + dy * dy > safeRadius * safeRadius) outsideSafeZone++
        }
        assertTrue("Ivory book and moon gate must be clearly visible ($ivory px)", ivory > 6000)
        assertEquals("Mark must stay inside the 66 dp safe zone", 0, outsideSafeZone)
        assertEquals(0, Color.alpha(fg.getPixel(0, 0)))
        fg.recycle()

        val cell = 432
        val sheet = Bitmap.createBitmap(cell * 4, cell + 56, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(236, 233, 226))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 34, 31); textSize = 24f; textAlign = Paint.Align.CENTER }
        val names = listOf("Circle", "Squircle", "Rounded", "Themed")
        repeat(4) { i ->
            canvas.save()
            canvas.translate((i * cell).toFloat(), 0f)
            val inset = cell * 18f / 108f
            val box = RectF(inset, inset, cell - inset, cell - inset)
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
        save(sheet, "v95-launcher-masks.png")
    }

    @Test
    fun splashMarkRendersInLightAndDark() {
        val w = 540
        val h = 960
        val sheet = Bitmap.createBitmap(w * 2, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEachIndexed { i, night ->
            val cfg = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val themed = context.createConfigurationContext(cfg)
            val bg = themed.getColor(R.color.langhuan_window_v95)
            val paint = Paint().apply { color = bg }
            canvas.drawRect((i * w).toFloat(), 0f, ((i + 1) * w).toFloat(), h.toFloat(), paint)
            val mark = themed.getDrawable(R.drawable.splash_mark_v95)!!
            val s = 320
            mark.setBounds(i * w + (w - s) / 2, (h - s) / 2, i * w + (w + s) / 2, (h + s) / 2)
            mark.draw(canvas)
            // The mark is ink on paper: it must differ clearly from the background in both modes.
            val probe = Bitmap.createBitmap(sheet, i * w + w / 2 - 70, h / 2 + 20, 1, 1)
            assertTrue("splash mark visible (mode $night)", probe.getPixel(0, 0) != bg)
        }
        save(sheet, "v95-splash-light-dark.png")
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/$name").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}

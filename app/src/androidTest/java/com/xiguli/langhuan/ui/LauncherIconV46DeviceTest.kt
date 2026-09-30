package com.xiguli.langhuan.ui

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
import org.junit.Assert.*
import org.junit.Test

class LauncherIconV46DeviceTest {
    @Test @SdkSuppress(minSdkVersion = 33)
    fun approvedBookArtworkFitsCircleSquircleAndThemedMasks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = 432
        icon.setBounds(0, 0, size, size)
        assertNotNull(icon.monochrome)

        val foreground = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.foreground.draw(Canvas(foreground))
        var bookPixels = 0
        var clippedBookPixels = 0
        for (y in 0 until size) for (x in 0 until size) {
            val c = foreground.getPixel(x, y)
            if (Color.alpha(c) > 230 && Color.red(c) > 190 && Color.green(c) > 185) {
                bookPixels++
                val dx = x - size / 2f
                val dy = y - size / 2f
                if (dx * dx + dy * dy > size * size / 4f) clippedBookPixels++
            }
        }
        assertTrue("Approved open book must be visible at launcher size", bookPixels > 15000)
        assertEquals("Circle mask must not cut the ivory pages", 0, clippedBookPixels)
        assertEquals(0, Color.alpha(foreground.getPixel(0, 0)))
        foreground.recycle()

        val sheet = Bitmap.createBitmap(size * 3, size + 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(225, 230, 237))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 38, 62); textSize = 22f; textAlign = Paint.Align.CENTER }
        repeat(3) { index ->
            canvas.save()
            canvas.translate((index * size).toFloat(), 0f)
            val mask = Path().apply {
                if (index == 0) addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
                else addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 100f, 100f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(mask)
            if (index < 2) { icon.background.draw(canvas); icon.foreground.draw(canvas) }
            else {
                canvas.drawColor(Color.rgb(207, 222, 244))
                icon.monochrome!!.apply { bounds = icon.foreground.bounds; setTint(Color.rgb(28, 68, 121)); draw(canvas) }
            }
            canvas.restore()
            canvas.drawText(listOf("Circle", "Rounded square", "Themed")[index], size / 2f, size + 32f, labelPaint)
            canvas.restore()
        }
        val file = File(context.getExternalFilesDir(null), "v46-launcher-masks.png")
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/v46-launcher-masks.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}

package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Diagnostic only: preserve the actual composed screen before any foreground assertion fails. */
internal fun deviceWindowEvidenceV46(label: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
    val image = File(dir, "$safeLabel.png")
    val bitmap = requireNotNull(automation.takeScreenshot()) { "Window screenshot unavailable: $safeLabel" }
    try {
        image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Window PNG could not be written: $safeLabel" } }
    } finally {
        bitmap.recycle()
    }
    val dump = File(dir, "$safeLabel-window.txt")
    dump.writeText("activePackage=${automation.rootInActiveWindow?.packageName}\n" +
        automation.executeShellCommand("dumpsys window windows").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8).take(180000) })
    automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    listOf(image, dump).filter(File::exists).forEach { file ->
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}

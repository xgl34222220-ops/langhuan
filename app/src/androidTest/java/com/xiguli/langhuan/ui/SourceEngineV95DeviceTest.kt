package com.xiguli.langhuan.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V95 on ART: the Rhino sandbox (interpreted mode, no Java access), jsoup XPath, and the shelf cover
 * written for an online book. JVM unit tests cannot prove these run on Android.
 */
class SourceEngineV95DeviceTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "v95-test-${UUID.randomUUID()}-"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(prefix + name, mode)
    }

    @Test fun scriptsXpathAndJsonRulesRunOnArt() {
        val doc = Jsoup.parse("<div class='item'><h3><a href='/b/1'>长夜</a></h3><img data-original='/c/1.jpg'></div>", "https://www.example.com/s")
        assertEquals("长夜", ruleStringV36(doc, "//h3/a/text()"))
        assertEquals("https://www.example.com/c/1_big.jpg", resolveUrlV36(doc.location(), ruleStringV36(doc, "//img/@data-original@js:result.replace('.jpg','_big.jpg')")))
        assertEquals("夜", ruleStringV36(doc, "<js>java.base64Decode('5aSc')</js>"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ruleStringV36(doc, "@js:java.md5Encode('abc')"))
        assertEquals("undefined", ruleStringV36(doc, "@js:typeof Packages"))
        assertEquals("", ruleStringV36(doc, "@js:java.lang.System.getProperty('user.home')"))
        val json = parseSourceDocumentV44("""{"data":[{"n":"甲"},{"n":"乙"}]}""".toByteArray(), "https://api.example.com/x")
        assertEquals(listOf("甲", "乙"), ruleElementsV36(json, "$.data[*]").map { ruleStringV36(it, "$.n") })
        val budget = SourceJsEngineV95.TIMEOUT_MS
        SourceJsEngineV95.TIMEOUT_MS = 1_500
        try {
            assertTrue(runCatching { ruleStringV36(doc, "@js:while(true){}") }.exceptionOrNull()?.message.orEmpty().contains("超时"))
        } finally {
            SourceJsEngineV95.TIMEOUT_MS = budget
        }
    }

    @Test fun downloadedOnlineCoverBecomesTheShelfCoverButNeverReplacesAUserCover() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val db = Room.inMemoryDatabaseBuilder(context, LanghuanDatabase::class.java).build()
        val dir = File(context.cacheDir, "v95-covers-${UUID.randomUUID()}")
        try {
            val projects = StoryProjectManager(context, db)
            val story = projects.createImportedStory(ImportedManuscript("在线书", listOf(ImportedChapter("第一章", "", "https://www.example.com/c/1")),
                sourceId = "https://www.example.com", sourceBookUrl = "https://www.example.com/b/1"))
            val id = story.snapshot.novel.id
            val bitmap = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 90, 160)) }
            val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val webpBytes = ByteArrayOutputStream().also { @Suppress("DEPRECATION") bitmap.compress(Bitmap.CompressFormat.WEBP, 100, it) }.toByteArray()
            val bytes = downloadOnlineCoverV95("https://img.example.net/1.webp", null) { _, _, _ -> webpBytes }
            assertTrue(looksLikeImageV95(png) && looksLikeImageV95(bytes))
            val path = persistOnlineCoverFileV95(dir, id, bytes)
            assertTrue(projects.setOnlineCoverIfMissingV95(id, path))
            val loaded = projects.loadStory(id)!!
            assertEquals(path, loaded.snapshot.novel.coverPath)
            assertEquals(30, BitmapFactory.decodeFile(path).width)
            assertTrue(projects.onlineShelfBooksV95().any { it.id == id && it.coverPath == path })
            // A second download never replaces an existing cover file (e.g. one the user picked).
            val other = persistOnlineCoverFileV95(File(dir, "other"), id, png)
            assertFalse(projects.setOnlineCoverIfMissingV95(id, other))
            assertEquals(path, projects.loadStory(id)!!.snapshot.novel.coverPath)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}

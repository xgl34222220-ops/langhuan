package com.xiguli.langhuan.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.StoryProjectManager
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Run on Android: the import checks below use the real ViewModel and Room shelf. */
class ExternalBookImportV1DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val uri = Uri.parse("content://external.book.fixture/document/42")

    @Test fun manifestOffersBooksAndGenericFilesWithoutClaimingImagesPdfOrApk() {
        fun resolves(action: String, mime: String?): Boolean {
            val intent = Intent(action).setPackage(app.packageName)
            if (action == Intent.ACTION_VIEW) intent.setDataAndType(uri, mime) else intent.type = mime
            return app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.name == MainActivity::class.java.name }
        }
        listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND).forEach { action ->
            listOf("text/plain", "text/markdown", "application/epub+zip", "application/octet-stream", "application/zip").forEach {
                assertTrue("Must advertise $action $it", resolves(action, it))
            }
            listOf("image/jpeg", "application/pdf", "application/vnd.android.package-archive").forEach {
                assertFalse("Must not advertise $action $it", resolves(action, it))
            }
        }
        assertTrue(resolves(Intent.ACTION_VIEW, null))
    }

    @Test fun viewAndShareReadOnlyDocumentAttachmentsAndNeverTextUrls() {
        assertEquals(uri, externalBookUriV1(Intent(Intent.ACTION_VIEW, uri)))
        assertEquals(uri, externalBookUriV1(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)))
        assertEquals(uri, externalBookUriV1(Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("book", uri) }))
        assertNull(externalBookUriV1(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://example.com/novel.txt")))
        assertThrows(IllegalArgumentException::class.java) {
            externalBookUriV1(Intent(Intent.ACTION_SEND).apply {
                clipData = ClipData.newRawUri("book", uri).also { it.addItem(ClipData.Item(Uri.parse("content://other/2"))) }
            })
        }
    }

    @Test fun queuedLaunchNeedsNeitherAReadableProviderNorADatabaseBeforeConfirmation() {
        val saved = SavedStateHandle()
        val coordinator = ExternalBookImportCoordinatorV1(saved)
        coordinator.receive(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertEquals(uri.toString(), coordinator.pending.value.single().uri)
        assertNull(coordinator.error.value)
        val restored = ExternalBookImportCoordinatorV1(saved)
        restored.receive(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri))
        assertEquals(1, restored.pending.value.size)
        restored.dismiss(uri.toString())
        assertTrue(restored.pending.value.isEmpty())
        restored.receive(Intent(Intent.ACTION_VIEW, uri))
        assertEquals(1, restored.pending.value.size)
    }

    @Test fun savedStateDefaultArgumentsCannotInjectPrivatePathsOrNetworkReads() {
        listOf("file:///private/not-read.txt", "http://example.com/book.txt", "https://example.com/book.txt",
            "content:///missing-authority", "content://bad authority/book", "content://provider/" + "a".repeat(8192)).forEach { value ->
            val state = SavedStateHandle(mapOf("external_book_uris" to arrayListOf(value)))
            assertTrue(ExternalBookImportCoordinatorV1(state).pending.value.isEmpty())
        }
        val wrongTypes = SavedStateHandle(mapOf("external_book_uris" to arrayListOf(7, null), "external_book_mimes" to "text/plain"))
        assertTrue(ExternalBookImportCoordinatorV1(wrongTypes).pending.value.isEmpty())
    }

    @Test fun coldLaunchRotationAndWarmShareKeepOnePendingRequest() {
        val launch = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/octet-stream")
            .putStringArrayListExtra("external_book_uris", arrayListOf("file:///private/not-read.txt"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(1, ViewModelProvider(activity)[ExternalBookImportCoordinatorV1::class.java].pending.value.size)
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val coordinator = ViewModelProvider(activity)[ExternalBookImportCoordinatorV1::class.java]
                instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                assertEquals(1, coordinator.pending.value.size)
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
                coordinator.dismiss(uri.toString())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(ViewModelProvider(activity)[ExternalBookImportCoordinatorV1::class.java].pending.value.isEmpty())
            }
        }
    }

    @Test fun revokedAccessAndMalformedFilesLeaveExistingShelfAndMetadataUntouched() = runBlocking {
        val manager = StoryProjectManager(app)
        val before = manager.observeStories().first()
        val active = manager.activeStoryId()
        val metadata = app.getSharedPreferences("local_book_meta_v1", 0)
        val metadataBefore = metadata.all.toMap()
        val vm = LocalBookImportViewModelV1(app)
        try {
            instrumentation.runOnMainSync {
                vm.importDocument({ "revoked.txt" }, { throw SecurityException("grant revoked") }, externalRequestUri = uri.toString())
            }
            val revoked = withTimeout(20_000) { vm.state.first { !it.busy } }
            assertTrue(revoked.error.orEmpty().contains("授权已失效"))
            assertNull(revoked.importedBookId)
            instrumentation.runOnMainSync {
                vm.dismissExternalRequest(uri.toString())
                assertNull(vm.state.value.externalRequestUri)
                vm.importDocument({ "wrong.txt" }, { byteArrayOf(0, 1, 2, 3).inputStream() })
            }
            val malformed = withTimeout(20_000) { vm.state.first { !it.busy } }
            assertNotNull(malformed.error)
            assertNull(malformed.importedBookId)
            assertEquals(before, manager.observeStories().first())
            assertEquals(active, manager.activeStoryId())
            assertEquals(metadataBefore, metadata.all)
        } finally {
            instrumentation.runOnMainSync { vm.viewModelScope.cancel() }
        }
    }

    @Test fun cancelClosesAnActiveProviderAndDoubleConfirmationCannotStartAnotherImport() = runBlocking {
        val manager = StoryProjectManager(app)
        val before = manager.observeStories().first()
        val active = manager.activeStoryId()
        val metadata = app.getSharedPreferences("local_book_meta_v1", 0)
        val metadataBefore = metadata.all.toMap()
        val enteredRead = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val vm = LocalBookImportViewModelV1(app)
        val source = object : InputStream() {
            override fun read(): Int = error("Expected bounded bulk read")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                enteredRead.countDown()
                check(released.await(10, TimeUnit.SECONDS)) { "Cancel must release the provider stream" }
                bytes.fill(65, offset, offset + length)
                return length
            }
            override fun close() { closed.countDown(); released.countDown() }
        }
        try {
            instrumentation.runOnMainSync {
                assertTrue(vm.importDocument({ "large.txt" }, { source }, externalRequestUri = uri.toString()))
                assertFalse(vm.importDocument({ error("Duplicate request must not query metadata") }, { error("Must not read twice") }))
            }
            assertTrue(enteredRead.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { vm.cancelImport() }
            val cancelled = withTimeout(20_000) { vm.state.first { !it.busy } }
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            assertNull(cancelled.error)
            assertNull(cancelled.importedBookId)
            assertEquals("已取消导入", cancelled.message)
            assertEquals(before, manager.observeStories().first())
            assertEquals(active, manager.activeStoryId())
            assertEquals(metadataBefore, metadata.all)
        } finally {
            released.countDown()
            instrumentation.runOnMainSync { vm.viewModelScope.cancel() }
        }
    }
    @Test fun clearingTheViewModelReleasesAProviderReadWithoutImportingABook() = runBlocking {
        val manager = StoryProjectManager(app)
        val before = manager.observeStories().first()
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = androidx.lifecycle.ViewModelStore()
        val vm = LocalBookImportViewModelV1(app)
        val stream = object : InputStream() {
            override fun read(): Int = error("Expected bulk read")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return -1
            }
            override fun close() { closed.countDown(); release.countDown() }
        }
        try {
            instrumentation.runOnMainSync {
                ViewModelProvider(store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = vm as T
                })[LocalBookImportViewModelV1::class.java]
                vm.importDocument({ "cancel-on-clear.txt" }, { stream })
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { store.clear() }
            assertTrue("Clearing must close the stream even though viewModelScope is cancelled", closed.await(5, TimeUnit.SECONDS))
            withTimeout(10_000) { vm.state.first { !it.busy } }
            assertNull(vm.state.value.importedBookId)
            assertEquals(before, manager.observeStories().first())
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { store.clear() }
        }
    }

}

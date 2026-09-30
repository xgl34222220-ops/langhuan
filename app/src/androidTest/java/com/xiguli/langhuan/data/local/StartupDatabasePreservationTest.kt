package com.xiguli.langhuan.data.local

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Every test uses a unique name; none opens or deletes the application's langhuan.db. */
class StartupDatabasePreservationTest {
    private lateinit var context: Context
    private lateinit var testRoot: File
    private lateinit var databaseName: String
    private val bookId = "preserved-book"
    private val bookJson = "{\"title\":\"保留小说\",\"shelfGroup\":\"待读\"}"
    private val draftJson = "{\"chapter\":7,\"content\":\"不能丢失的正文\"}"

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "startup-preservation-${UUID.randomUUID()}.db"
        testRoot = File(base.cacheDir, databaseName + "-fixture").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(testRoot, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(testRoot, "cache").apply { mkdirs() }
        }
    }

    @After
    fun tearDown() {
        // The name is generated above and never aliases the user's database.
        context.deleteDatabase(databaseName)
        testRoot.deleteRecursively()
    }

    @Test
    fun corruptDatabaseAndEverySidecarRemainByteIdentical() = runBlocking {
        createCorruptFiles()
        val before = fileBytes()

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertBlocked(status)
        assertOriginalFiles(before)
        assertTrue("A complete backup must be reported", status.backupPath.isNotBlank())
        before.forEach { (name, bytes) ->
            assertArrayEquals(bytes, File(status.backupPath, name).readBytes())
        }
        assertTrue("Disposable probe files must be removed", preflightFiles().isEmpty())
    }

    @Test
    fun unsupportedVersionIsNotReplacedByAnEmptyLibrary() = runBlocking {
        createValidBook()
        rawDatabase().use { it.version = 99 }
        val before = fileBytes()

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertBlocked(status)
        assertOriginalFiles(before)
        rawDatabase().use {
            assertEquals(99, it.version)
            assertBook(it)
        }
    }

    @Test
    fun invalidVersionOneSchemaCannotEraseBookData() = runBlocking {
        createValidBook()
        rawDatabase().use {
            it.execSQL("ALTER TABLE story_state ADD COLUMN unexpected TEXT")
            it.execSQL("DROP TABLE chapter_state")
            it.version = 1
        }
        val before = fileBytes()

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertBlocked(status)
        assertOriginalFiles(before)
        rawDatabase().use { assertBook(it) }
    }

    @Test
    fun backupWriteFailureKeepsOriginalAndReportsFailure() = runBlocking {
        createCorruptFiles()
        val before = fileBytes()
        var backupAttempted = false

        val status = StartupDatabaseGate.prepare(context, databaseName) { source, target ->
            if (target.absolutePath.contains("/database_recovery/")) {
                backupAttempted = true
                throw IOException("Injected backup write failure")
            }
            source.copyTo(target)
        }

        assertTrue(backupAttempted)
        assertBlocked(status)
        assertEquals("", status.backupPath)
        assertTrue(status.error.contains("备份未完成"))
        assertOriginalFiles(before)
    }

    @Test
    fun simulatedFullDiskDuringSnapshotNeverOpensOriginal() = runBlocking {
        createValidBook()
        val before = fileBytes()

        val status = StartupDatabaseGate.prepare(context, databaseName) { _, target ->
            // A partial write followed by ENOSPC is deterministic and does not fill the device.
            target.writeBytes(byteArrayOf(1, 2, 3))
            throw IOException("ENOSPC: injected no space left on device")
        }

        assertBlocked(status)
        assertEquals("", status.backupPath)
        assertTrue(status.error.contains("ENOSPC"))
        assertOriginalFiles(before)
        assertTrue(preflightFiles().isEmpty())
    }

    @Test
    fun cancellationDuringCopyPropagatesWithoutChangingOriginal() = runBlocking {
        createCorruptFiles()
        val before = fileBytes()
        var cancelled = false

        try {
            StartupDatabaseGate.prepare(context, databaseName) { _, target ->
                target.writeBytes(byteArrayOf(1, 2, 3))
                throw CancellationException("Injected startup cancellation")
            }
            fail("Cancellation must propagate, not become corruption or trigger recovery")
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertOriginalFiles(before)
        assertTrue(preflightFiles().isEmpty())
    }

    @Test
    fun orphanedWalCannotBeReplacedByANewDatabase() = runBlocking {
        val wal = File(context.getDatabasePath(databaseName).absolutePath + "-wal")
        wal.parentFile!!.mkdirs()
        wal.writeText("potentially recoverable WAL data")
        val before = fileBytes()

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertBlocked(status)
        assertOriginalFiles(before)
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun validDatabaseOpensAndRepeatedStartupDoesNotCopyLiveFiles() = runBlocking {
        createValidBook()

        val first = StartupDatabaseGate.prepare(context, databaseName)
        val repeated = StartupDatabaseGate.prepare(context, databaseName) { _, _ ->
            fail("An already validated database must not be copied on Activity recreation")
        }

        assertTrue(first.ready)
        assertTrue(repeated.ready)
        assertFalse(first.recovered)
        openRoom().use { room ->
            val book = room.storyStateDao().get(bookId)!!
            assertEquals(bookJson, book.snapshotJson)
            assertEquals(draftJson, book.draftJson)
        }
        assertTrue(preflightFiles().isEmpty())
    }

    @Test
    fun supportedVersionOneMigrationKeepsBookData() = runBlocking {
        createValidBook()
        // Version 1 has the same four original tables; migration 1→2 only adds chapter_state.
        rawDatabase().use {
            it.execSQL("DROP TABLE chapter_state")
            it.execSQL("DROP TABLE room_master_table")
            it.version = 1
        }

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertTrue(status.error, status.ready)
        assertFalse(status.recovered)
        rawDatabase().use {
            assertEquals(2, it.version)
            assertBook(it)
            it.rawQuery("SELECT COUNT(*) FROM chapter_state", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun aNewInstallCreatesAValidDatabase() = runBlocking {
        assertFalse(context.getDatabasePath(databaseName).exists())

        val status = StartupDatabaseGate.prepare(context, databaseName)

        assertTrue(status.error, status.ready)
        assertTrue(context.getDatabasePath(databaseName).isFile)
        openRoom().use { room -> assertEquals(0, room.aiProviderDao().count()) }
    }

    @Test
    fun realRoomCorruptionCallbackCannotDeleteTheOriginal() {
        val db = context.getDatabasePath(databaseName)
        db.parentFile!!.mkdirs()
        db.writeBytes(ByteArray(4096) { 0x5a.toByte() })
        val before = fileBytes()
        var rejected = false

        try {
            openRoom().use { it.openHelper.writableDatabase.query("SELECT 1").close() }
        } catch (_: Exception) {
            rejected = true
        }

        assertTrue("A corrupt file must not silently become an empty database", rejected)
        assertTrue(db.exists())
        assertArrayEquals(before.getValue(db.name), db.readBytes())
    }

    private fun openRoom(): LanghuanDatabase = Room.databaseBuilder(
        context, LanghuanDatabase::class.java, databaseName,
    ).openHelperFactory(PreservingSQLiteOpenHelperFactory).build()

    private suspend fun createValidBook() {
        openRoom().use { room ->
            room.storyStateDao().upsert(StoryStateEntity(bookId, bookJson, draftJson, 12345L))
        }
    }

    private fun rawDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )

    private fun assertBook(db: SQLiteDatabase) {
        db.rawQuery("SELECT snapshotJson, draftJson, updatedAt FROM story_state WHERE novelId = ?",
            arrayOf(bookId)).use { cursor ->
            assertTrue("Existing novel disappeared", cursor.moveToFirst())
            assertEquals(bookJson, cursor.getString(0))
            assertEquals(draftJson, cursor.getString(1))
            assertEquals(12345L, cursor.getLong(2))
        }
    }

    private fun createCorruptFiles() {
        val db = context.getDatabasePath(databaseName)
        db.parentFile!!.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEachIndexed { index, suffix ->
            File(db.absolutePath + suffix).writeBytes(ByteArray(4096) { (0x41 + index).toByte() })
        }
    }

    private fun fileBytes(): Map<String, ByteArray> {
        val db = context.getDatabasePath(databaseName)
        return listOf("", "-wal", "-shm", "-journal")
            .map { File(db.absolutePath + it) }
            .filter(File::exists)
            .associate { it.name to it.readBytes() }
    }

    private fun assertOriginalFiles(before: Map<String, ByteArray>) {
        val after = fileBytes()
        assertEquals("Original database file set changed", before.keys, after.keys)
        before.forEach { (name, bytes) ->
            assertArrayEquals("Original bytes changed: $name", bytes, after.getValue(name))
        }
    }

    private fun assertBlocked(status: StartupDatabaseStatus) {
        assertFalse("Failed database must stay on the diagnostic screen", status.ready)
        assertFalse("Failure must not claim the library was recovered", status.recovered)
        assertTrue(status.error.isNotBlank())
    }

    private fun preflightFiles(): List<File> =
        File(context.cacheDir, "database_preflight").listFiles()?.toList().orEmpty()
}

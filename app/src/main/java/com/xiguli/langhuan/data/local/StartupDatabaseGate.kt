package com.xiguli.langhuan.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class StartupDatabaseStatus(
    val ready: Boolean,
    val recovered: Boolean = false,
    val backupPath: String = "",
    val error: String = "",
)

/** SQLite's default corruption callback deletes files before Room reports an open failure. */
internal object PreservingSQLiteOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val delegate = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                delegate.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                delegate.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                throw SQLiteDatabaseCorruptException("数据库损坏，已保留原始文件")
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build()
        )
    }
}

/**
 * Validate existing files on a disposable copy before opening or migrating the original.
 * A failed check blocks startup; it never resets a user's library, even if backup fails.
 */
object StartupDatabaseGate {
    private const val DB_NAME = "langhuan.db"
    private val startupMutex = Mutex()
    private val preparedPaths = mutableSetOf<String>()

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chapter_state` (
                    `id` TEXT NOT NULL,
                    `novelId` TEXT NOT NULL,
                    `chapterNumber` INTEGER NOT NULL,
                    `draftJson` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_state_novelId_chapterNumber` ON `chapter_state` (`novelId`, `chapterNumber`)"
            )
        }
    }

    suspend fun prepare(context: Context): StartupDatabaseStatus = prepare(context, DB_NAME)

    // A separate name and copy operation let device tests use only isolated fixture databases.
    internal suspend fun prepare(
        context: Context,
        databaseName: String,
        copyFile: (File, File) -> Unit = { source, target -> source.copyTo(target) },
    ): StartupDatabaseStatus = withContext(Dispatchers.IO) {
        startupMutex.withLock {
            val app = context.applicationContext
            val db = app.getDatabasePath(databaseName)
            currentCoroutineContext().ensureActive()
            // Activity recreation may leave the real Room connection writing in this process.
            // Only the first successful startup checks files; do not snapshot a live connection.
            if (db.absolutePath in preparedPaths) return@withLock StartupDatabaseStatus(ready = true)

            var failure = validateSnapshot(app, db, copyFile)
            currentCoroutineContext().ensureActive()
            if (failure == null) failure = probe(app, databaseName)
            currentCoroutineContext().ensureActive()
            if (failure == null) {
                preparedPaths.add(db.absolutePath)
                return@withLock StartupDatabaseStatus(ready = true)
            }

            var backupFailure: Exception? = null
            val backup = try {
                backupDatabaseFiles(app, db, copyFile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                backupFailure = error
                null
            }
            StartupDatabaseStatus(
                ready = false,
                backupPath = backup?.absolutePath.orEmpty(),
                error = "数据库无法安全打开，已保留原始文件，未重置书库：${failure.safeMessage()}" +
                    (backupFailure?.let { "；备份未完成：${it.safeMessage()}" } ?: ""),
            )
        }
    }

    private suspend fun validateSnapshot(
        context: Context,
        db: File,
        copyFile: (File, File) -> Unit,
    ): Exception? {
        val sources = databaseFiles(db)
        if (sources.isEmpty()) return null
        // Orphan WAL/journal files might contain the only recoverable user data.
        if (!db.isFile) return IOException("主数据库文件缺失，保留日志等待恢复")
        var snapshot: File? = null
        return try {
            snapshot = createDirectory(context.cacheDir, "database_preflight")
            copyFiles(sources, snapshot, copyFile)
            probe(context, File(snapshot, db.name).absolutePath)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        } finally {
            // Only this invocation's disposable copies may be removed.
            snapshot?.deleteRecursively()
        }
    }

    private fun probe(context: Context, databaseName: String): Exception? {
        var database: LanghuanDatabase? = null
        return try {
            database = Room.databaseBuilder(
                context.applicationContext,
                LanghuanDatabase::class.java,
                databaseName,
            )
                .openHelperFactory(PreservingSQLiteOpenHelperFactory)
                .addMigrations(migration1To2)
                .build()
            database.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1)
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        } finally {
            database?.close()
        }
    }

    private fun databaseFiles(db: File): List<File> = listOf(
        db,
        File(db.absolutePath + "-wal"),
        File(db.absolutePath + "-shm"),
        File(db.absolutePath + "-journal"),
    ).filter(File::exists)

    private fun createDirectory(parent: File, name: String): File =
        File(parent, "$name/${UUID.randomUUID()}").also {
            if (!it.mkdirs()) throw IOException("无法创建数据库检查或备份目录")
        }

    private suspend fun copyFiles(
        sources: List<File>,
        target: File,
        copyFile: (File, File) -> Unit,
    ) {
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            copyFile(source, File(target, source.name))
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun backupDatabaseFiles(
        context: Context,
        db: File,
        copyFile: (File, File) -> Unit,
    ): File? {
        val sources = databaseFiles(db)
        if (sources.isEmpty()) return null
        val backup = createDirectory(context.filesDir, "database_recovery")
        try {
            copyFiles(sources, backup, copyFile)
            return backup
        } catch (error: Exception) {
            backup.deleteRecursively()
            throw error
        }
    }

    private fun Throwable.safeMessage(): String =
        message?.take(500)?.ifBlank { null } ?: javaClass.simpleName
}

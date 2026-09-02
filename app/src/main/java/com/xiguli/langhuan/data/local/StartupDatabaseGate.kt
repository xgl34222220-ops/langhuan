package com.xiguli.langhuan.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StartupDatabaseStatus(
    val ready: Boolean,
    val recovered: Boolean = false,
    val backupPath: String = "",
    val error: String = "",
)

/**
 * Opens Room before any ViewModel is created. If an old/corrupt/incompatible database cannot
 * be opened, preserve its files first and rebuild a clean database so launcher startup survives.
 */
object StartupDatabaseGate {
    private const val DB_NAME = "langhuan.db"

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

    suspend fun prepare(context: Context): StartupDatabaseStatus = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val firstFailure = probe(app)
        if (firstFailure == null) {
            return@withContext StartupDatabaseStatus(ready = true)
        }

        val backupDir = runCatching { backupDatabaseFiles(app) }.getOrNull()
        val deleted = runCatching { app.deleteDatabase(DB_NAME) }.getOrDefault(false)
        if (!deleted && app.getDatabasePath(DB_NAME).exists()) {
            return@withContext StartupDatabaseStatus(
                ready = false,
                backupPath = backupDir?.absolutePath.orEmpty(),
                error = "数据库无法隔离：${firstFailure.safeMessage()}",
            )
        }

        val secondFailure = probe(app)
        if (secondFailure == null) {
            StartupDatabaseStatus(
                ready = true,
                recovered = true,
                backupPath = backupDir?.absolutePath.orEmpty(),
                error = firstFailure.safeMessage(),
            )
        } else {
            StartupDatabaseStatus(
                ready = false,
                recovered = true,
                backupPath = backupDir?.absolutePath.orEmpty(),
                error = "重建数据库后仍无法打开：${secondFailure.safeMessage()}",
            )
        }
    }

    private fun probe(context: Context): Throwable? {
        var database: LanghuanDatabase? = null
        return try {
            database = Room.databaseBuilder(
                context.applicationContext,
                LanghuanDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(migration1To2)
                .build()
            database.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0)
            }
            null
        } catch (error: Throwable) {
            error
        } finally {
            runCatching { database?.close() }
        }
    }

    private fun backupDatabaseFiles(context: Context): File? {
        val db = context.getDatabasePath(DB_NAME)
        val candidates = listOf(
            db,
            File(db.absolutePath + "-wal"),
            File(db.absolutePath + "-shm"),
            File(db.absolutePath + "-journal"),
        ).filter(File::exists)
        if (candidates.isEmpty()) return null

        val root = File(context.filesDir, "database_recovery/${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        candidates.forEach { source ->
            source.copyTo(File(root, source.name), overwrite = true)
        }
        return root
    }

    private fun Throwable.safeMessage(): String =
        message?.take(500)?.ifBlank { null } ?: javaClass.simpleName
}

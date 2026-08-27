package com.xiguli.langhuan.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "story_state")
data class StoryStateEntity(
    @PrimaryKey val novelId: String,
    val snapshotJson: String,
    val draftJson: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "chapter_versions",
    indices = [Index(value = ["novelId", "chapterNumber", "version"], unique = true)],
)
data class ChapterVersionEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val chapterNumber: Int,
    val version: Int,
    val title: String,
    val content: String,
    val summary: String,
    val createdAt: Long,
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val protocol: String,
    val model: String,
    val temperature: Double,
    val supportsJsonMode: Boolean,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_chunks",
    indices = [Index(value = ["novelId", "chapterNumber"])],
)
data class MemoryChunkEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val sourceType: String,
    val sourceId: String,
    val chapterNumber: Int?,
    val text: String,
    val updatedAt: Long,
)

@Dao
interface StoryStateDao {
    @Query("SELECT * FROM story_state WHERE novelId = :novelId LIMIT 1")
    suspend fun get(novelId: String): StoryStateEntity?

    @Upsert
    suspend fun upsert(entity: StoryStateEntity)
}

@Dao
interface ChapterVersionDao {
    @Upsert
    suspend fun upsert(entity: ChapterVersionEntity)

    @Query("SELECT * FROM chapter_versions WHERE novelId = :novelId ORDER BY chapterNumber DESC, version DESC")
    suspend fun allForNovel(novelId: String): List<ChapterVersionEntity>
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY isDefault DESC, updatedAt DESC")
    fun observeAll(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AiProviderEntity?

    @Query("SELECT COUNT(*) FROM ai_providers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: AiProviderEntity)

    @Query("UPDATE ai_providers SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END, updatedAt = CASE WHEN id = :id THEN :now ELSE updatedAt END")
    suspend fun markDefault(id: String, now: Long)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemoryChunkDao {
    @Query("SELECT * FROM memory_chunks WHERE novelId = :novelId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(novelId: String, limit: Int): List<MemoryChunkEntity>

    @Upsert
    suspend fun upsertAll(items: List<MemoryChunkEntity>)

    @Upsert
    suspend fun upsert(item: MemoryChunkEntity)

    @Query("DELETE FROM memory_chunks WHERE novelId = :novelId")
    suspend fun deleteForNovel(novelId: String)
}

@Database(
    entities = [StoryStateEntity::class, ChapterVersionEntity::class, AiProviderEntity::class, MemoryChunkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LanghuanDatabase : RoomDatabase() {
    abstract fun storyStateDao(): StoryStateDao
    abstract fun chapterVersionDao(): ChapterVersionDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun memoryChunkDao(): MemoryChunkDao

    companion object {
        @Volatile private var instance: LanghuanDatabase? = null

        fun get(context: Context): LanghuanDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LanghuanDatabase::class.java,
                "langhuan.db",
            ).build().also { instance = it }
        }
    }
}

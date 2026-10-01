package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class ReaderBookmarkStoreV49Test {
    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        var failCommit = false
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, fallback: String?): String? = values[key] as String? ?: fallback
        @Suppress("UNCHECKED_CAST") override fun getStringSet(key: String?, fallback: MutableSet<String>?): MutableSet<String>? = (values[key] as Set<String>?)?.toMutableSet() ?: fallback
        override fun getInt(key: String?, fallback: Int) = values[key] as Int? ?: fallback
        override fun getLong(key: String?, fallback: Long) = values[key] as Long? ?: fallback
        override fun getFloat(key: String?, fallback: Float) = values[key] as Float? ?: fallback
        override fun getBoolean(key: String?, fallback: Boolean) = values[key] as Boolean? ?: fallback
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            var clearing = false
            override fun putString(key: String?, value: String?) = apply { pending[requireNotNull(key)] = value }
            override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { pending[requireNotNull(key)] = value?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { pending[requireNotNull(key)] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[requireNotNull(key)] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { pending[requireNotNull(key)] = null }
            override fun clear() = apply { clearing = true }
            override fun commit(): Boolean { apply(); return !failCommit }
            override fun apply() { if (clearing) values.clear(); pending.forEach { (k,v) -> if (v == null) values.remove(k) else values[k] = v } }
        }
    }

    @Test fun addingBookmarkToOneBookDoesNotMarkTheSameNumberInAnotherBook() {
        val prefs = MemoryPrefs()
        ReaderBookmarkStoreV49.replace(prefs, "book-A", setOf("1")).getOrThrow()
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, "book-A").getOrThrow())
        assertEquals(emptySet<String>(), ReaderBookmarkStoreV49.load(prefs, "book-B").getOrThrow())
    }
    @Test fun removingFromOneBookCannotRemoveAnotherBooksMarks() {
        val prefs = MemoryPrefs()
        ReaderBookmarkStoreV49.add(prefs, "A", 1).getOrThrow()
        ReaderBookmarkStoreV49.add(prefs, "B", 1).getOrThrow()
        ReaderBookmarkStoreV49.setMarked(prefs, "A", 1, false).getOrThrow()
        assertTrue(ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow().isEmpty())
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, "B").getOrThrow())
    }
    @Test fun legacyNeverAutoAttachesAndExplicitRestorePreservesBothExistingAndOriginal() {
        val prefs = MemoryPrefs().apply { values["bookmarks"] = setOf("1", "2", "99") }
        assertTrue(ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow().isEmpty())
        assertTrue(ReaderBookmarkStoreV49.load(prefs, "B").getOrThrow().isEmpty())
        ReaderBookmarkStoreV49.add(prefs, "A", 3).getOrThrow()
        repeat(2) { ReaderBookmarkStoreV49.restoreLegacy(prefs, "A", 1, setOf(1,2,3)).getOrThrow() }
        assertEquals(setOf("1", "3"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
        assertTrue(ReaderBookmarkStoreV49.load(prefs, "B").getOrThrow().isEmpty())
        assertEquals(setOf("1", "2", "99"), prefs.values["bookmarks"])
        assertTrue(ReaderBookmarkStoreV49.restoreLegacy(prefs, "A", 99, setOf(1,2,3)).isFailure)
        assertEquals(setOf("1", "3"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
    }
    @Test fun corruptCurrentBookSetCannotBeOverwrittenButDoesNotBlockOtherBooks() {
        val prefs = MemoryPrefs().apply { values["bookmarks_v49_A"] = setOf("bad", "1") }
        assertTrue(ReaderBookmarkStoreV49.setMarked(prefs, "A", 1, true).isFailure)
        assertEquals(setOf("bad", "1"), prefs.values["bookmarks_v49_A"])
        ReaderBookmarkStoreV49.add(prefs, "B", 2).getOrThrow()
        assertEquals(setOf("2"), ReaderBookmarkStoreV49.load(prefs, "B").getOrThrow())
    }
    @Test fun failedCommitRestoresTheExactOldSetOrMissingKey() {
        val prefs = MemoryPrefs()
        ReaderBookmarkStoreV49.add(prefs, "A", 1).getOrThrow()
        prefs.failCommit = true
        assertTrue(ReaderBookmarkStoreV49.add(prefs, "A", 2).isFailure)
        assertEquals(setOf("1"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
        assertTrue(ReaderBookmarkStoreV49.add(prefs, "B", 2).isFailure)
        assertFalse(prefs.contains("bookmarks_v49_B"))
    }
    @Test fun cancellationBeforeWriteKeepsCurrentAndLegacyBytes() {
        val prefs = MemoryPrefs().apply { values["bookmarks"] = setOf("1"); values["bookmarks_v49_A"] = setOf("2") }
        val before = prefs.values.toMap()
        try {
            Thread.currentThread().interrupt()
            assertTrue(ReaderBookmarkStoreV49.restoreLegacy(prefs, "A", 1, setOf(1,2)).isFailure)
        } finally { Thread.interrupted() }
        assertEquals(before, prefs.values)
    }
    @Test fun callerCannotMutateStoredSetsThroughTheReturnedSnapshot() {
        val prefs = MemoryPrefs()
        ReaderBookmarkStoreV49.replace(prefs, "A", setOf("1", "2")).getOrThrow()
        val snapshot = ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow().toMutableSet()
        snapshot.clear()
        assertEquals(setOf("1", "2"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
    }

    @Test fun corruptLegacySetDoesNotBlockFreshBookAndOriginalStaysUntouched() {
        val prefs = MemoryPrefs().apply { values["bookmarks"] = "malformed legacy type" }
        assertTrue(ReaderBookmarkStoreV49.legacy(prefs).isFailure)
        ReaderBookmarkStoreV49.add(prefs, "fresh", 2).getOrThrow()
        assertEquals(setOf("2"), ReaderBookmarkStoreV49.load(prefs, "fresh").getOrThrow())
        assertEquals("malformed legacy type", prefs.values["bookmarks"])
    }

    @Test fun staleAddOrRemoveIntentCannotInvertAConcurrentChange() {
        val prefs = MemoryPrefs()
        ReaderBookmarkStoreV49.replace(prefs, "A", setOf("1", "2")).getOrThrow()
        ReaderBookmarkStoreV49.setMarked(prefs, "A", 1, true).getOrThrow() // stale UI still showed Add
        assertEquals(setOf("1", "2"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
        ReaderBookmarkStoreV49.replace(prefs, "A", setOf("2", "3")).getOrThrow()
        ReaderBookmarkStoreV49.setMarked(prefs, "A", 1, false).getOrThrow() // stale UI still showed Remove
        assertEquals(setOf("2", "3"), ReaderBookmarkStoreV49.load(prefs, "A").getOrThrow())
    }

}

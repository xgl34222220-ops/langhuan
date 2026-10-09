package com.xiguli.langhuan.ui.epub

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEditionPreferenceV90Test {
    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, fallback: String?): String? = values[key] as String? ?: fallback
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, fallback: MutableSet<String>?): MutableSet<String>? =
            (values[key] as Set<String>?)?.toMutableSet() ?: fallback
        override fun getInt(key: String?, fallback: Int) = values[key] as Int? ?: fallback
        override fun getLong(key: String?, fallback: Long) = values[key] as Long? ?: fallback
        override fun getFloat(key: String?, fallback: Float) = values[key] as Float? ?: fallback
        override fun getBoolean(key: String?, fallback: Boolean) = values[key] as Boolean? ?: fallback
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String?, value: String?) = apply { pending[requireNotNull(key)] = value }
            override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { pending[requireNotNull(key)] = value?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { pending[requireNotNull(key)] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[requireNotNull(key)] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { pending[requireNotNull(key)] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v } }
        }
    }

    @Test fun booksNeverSwitchedStillOpenOnTheOriginal() {
        val prefs = MemoryPrefs()
        assertEquals(ReaderEditionV90.ORIGINAL, ReaderEditionPreferenceV90.load(prefs, "book-A"))
        assertFalse(ReaderEditionPreferenceV90.opensAsText(prefs, "book-A"))
    }

    @Test fun choosingTextSurvivesReopeningTheBook() {
        val prefs = MemoryPrefs()
        assertTrue(ReaderEditionPreferenceV90.save(prefs, "book-A", ReaderEditionV90.TEXT))
        // A later "session" reads the same store: the choice is restored, not reset to 原版.
        assertEquals(ReaderEditionV90.TEXT, ReaderEditionPreferenceV90.load(prefs, "book-A"))
        assertTrue(ReaderEditionPreferenceV90.opensAsText(prefs, "book-A"))
    }

    @Test fun choiceIsPerBook() {
        val prefs = MemoryPrefs()
        ReaderEditionPreferenceV90.save(prefs, "book-A", ReaderEditionV90.TEXT)
        assertFalse(ReaderEditionPreferenceV90.opensAsText(prefs, "book-B"))
        ReaderEditionPreferenceV90.save(prefs, "book-B", ReaderEditionV90.ORIGINAL)
        assertTrue(ReaderEditionPreferenceV90.opensAsText(prefs, "book-A"))
    }

    @Test fun switchingBackToOriginalAndForgettingWork() {
        val prefs = MemoryPrefs()
        ReaderEditionPreferenceV90.save(prefs, "book-A", ReaderEditionV90.TEXT)
        ReaderEditionPreferenceV90.save(prefs, "book-A", ReaderEditionV90.ORIGINAL)
        assertFalse(ReaderEditionPreferenceV90.opensAsText(prefs, "book-A"))
        ReaderEditionPreferenceV90.save(prefs, "book-A", ReaderEditionV90.TEXT)
        ReaderEditionPreferenceV90.forget(prefs, "book-A")
        assertFalse(prefs.contains(ReaderEditionPreferenceV90.keyFor("book-A")))
        assertEquals(ReaderEditionV90.ORIGINAL, ReaderEditionPreferenceV90.load(prefs, "book-A"))
    }

    @Test fun unknownOrBlankValuesAreSafe() {
        val prefs = MemoryPrefs()
        prefs.values[ReaderEditionPreferenceV90.keyFor("book-A")] = "garbage"
        assertEquals(ReaderEditionV90.ORIGINAL, ReaderEditionPreferenceV90.load(prefs, "book-A"))
        assertFalse(ReaderEditionPreferenceV90.save(prefs, "", ReaderEditionV90.TEXT))
        assertTrue(prefs.values.keys.none { it == ReaderEditionPreferenceV90.keyFor("") })
    }
}

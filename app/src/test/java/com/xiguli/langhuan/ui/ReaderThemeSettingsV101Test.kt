package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeSettingsV101Test {
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

    /* ------------------------------- Catalog ------------------------------- */

    @Test fun builtInDayListMatchesTheReferenceOrderAndTomatoIsDefault() {
        assertEquals(listOf("番茄", "绿", "书卷", "起点护眼色", "墨色", "花开"), ReaderThemeCatalogV101.BUILT_IN_DAY.map { it.name })
        assertEquals("tea", ReaderThemeCatalogV101.DEFAULT_DAY)
        assertEquals(ReaderThemeDecorV101.BAMBOO, ReaderThemeCatalogV101.builtIn("tea")!!.decoration)
        assertTrue(ReaderThemeCatalogV101.BUILT_IN_NIGHT.all { it.dark })
        assertTrue(ReaderThemeCatalogV101.BUILT_IN_DAY.none { it.dark })
        // Keys are unique across every bundled theme.
        val keys = ReaderThemeCatalogV101.ALL_BUILT_IN.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun customThemesRoundTripAndInvalidEntriesAreDropped() {
        val custom = ReaderThemeCatalogV101.newCustom("我的夜色", Color(0xFF101010), Color(0xFFCCCCCC), true, null, "abc")
        assertEquals("custom_abc", custom.key)
        assertTrue(custom.custom && custom.dark)
        assertEquals(0xFF101010, custom.page)
        val decoded = ReaderThemeCatalogV101.decodeCustom(ReaderThemeCatalogV101.encodeCustom(listOf(custom)))
        assertEquals(listOf(custom), decoded)
        assertEquals(emptyList<ReaderThemeSpecV101>(), ReaderThemeCatalogV101.decodeCustom("{not json"))
        val forged = custom.copy(key = "tea")
        assertEquals(emptyList<ReaderThemeSpecV101>(), ReaderThemeCatalogV101.decodeCustom(ReaderThemeCatalogV101.encodeCustom(listOf(forged))))
    }

    @Test fun installedListsDropUnknownAndWrongModeKeys() {
        assertEquals(listOf("green", "paper"), ReaderThemeCatalogV101.sanitizeInstalled(listOf("green", "night", "nope", "green", "paper"), false, emptyList()))
        assertEquals(ReaderThemeCatalogV101.defaultInstalled(true), ReaderThemeCatalogV101.sanitizeInstalled(listOf("tea"), true, emptyList()))
    }

    /* ------------------------------- Settings ------------------------------- */

    @Test fun freshSettingsUseTomatoByDayAndNightByNight() {
        val settings = ReaderSettingsV30(MemoryPrefs())
        assertEquals("tea", settings.dayTheme)
        assertEquals("night", settings.nightTheme)
        assertFalse(settings.nightMode)
        assertEquals("tea", settings.theme)
        assertEquals(ReaderThemeCatalogV101.defaultInstalled(false), settings.dayInstalled)
        assertTrue(settings.showStatusBar && settings.showNavigationBar && settings.pullBookmark)
        assertFalse(settings.followSystemNight)
    }

    @Test fun themeSelectionNightModeAndListsPersist() {
        val prefs = MemoryPrefs()
        val first = ReaderSettingsV30(prefs)
        first.selectTheme("green")
        first.selectTheme("nightInk")
        assertTrue(first.nightMode)
        assertEquals("nightInk", first.theme)
        first.installTheme("paper")
        first.installTheme("nightPure")
        assertTrue(first.removeTheme("blossom"))
        first.showStatusBar = false
        first.showNavigationBar = false
        first.pullBookmark = false
        first.save()

        val second = ReaderSettingsV30(prefs)
        assertEquals("green", second.dayTheme)
        assertEquals("nightInk", second.nightTheme)
        assertTrue(second.nightMode)
        assertTrue("paper" in second.dayInstalled)
        assertTrue("nightPure" in second.nightInstalled)
        assertFalse("blossom" in second.dayInstalled)
        assertFalse(second.showStatusBar)
        assertFalse(second.showNavigationBar)
        assertFalse(second.pullBookmark)
        second.toggleNight()
        assertEquals("green", second.theme)
    }

    @Test fun removingTheSelectedThemeFallsBackAndTheLastThemeStays() {
        val settings = ReaderSettingsV30(MemoryPrefs())
        settings.selectTheme("sheep")
        assertTrue(settings.removeTheme("sheep"))
        assertEquals(settings.dayInstalled.first(), settings.dayTheme)
        settings.nightInstalled.drop(1).forEach { settings.removeTheme(it) }
        assertEquals(1, settings.nightInstalled.size)
        assertFalse(settings.removeTheme(settings.nightInstalled.single()))
    }

    @Test fun customThemeIsSelectedPersistedAndResolvedByThePainter() {
        val prefs = MemoryPrefs()
        val settings = ReaderSettingsV30(prefs)
        val spec = ReaderThemeCatalogV101.newCustom("青瓷", Color(0xFFD9E8E1), Color(0xFF203028), false, null, "q1")
        assertTrue(settings.addCustomTheme(spec))
        assertEquals("custom_q1", settings.dayTheme)
        settings.save()
        val reloaded = ReaderSettingsV30(prefs)
        assertEquals("custom_q1", reloaded.theme)
        assertEquals("青瓷", readerThemeV30("custom_q1").name)
        assertEquals(Color(0xFFD9E8E1), readerThemeV30("custom_q1").page)
        assertTrue(reloaded.removeTheme("custom_q1"))
        assertTrue(reloaded.customThemes.isEmpty())
    }

    @Test fun backgroundFollowAppliesTheSystemDarkModeOnlyWhenOn() {
        val settings = ReaderSettingsV30(MemoryPrefs())
        settings.applySystemNight(true)
        assertFalse(settings.nightMode)
        settings.followSystemNight = true
        settings.applySystemNight(true)
        assertTrue(settings.nightMode)
        settings.applySystemNight(false)
        assertFalse(settings.nightMode)
        // The night icon is a manual choice and turns 背景跟随 off.
        settings.toggleNight()
        assertFalse(settings.followSystemNight)
        assertTrue(settings.nightMode)
    }

    @Test fun olderStoredThemesMigrate() {
        val night = MemoryPrefs().apply { values["theme"] = "night" }
        assertTrue(ReaderSettingsV30(night).nightMode)
        val paper = MemoryPrefs().apply { values["theme"] = "paper"; values["dayTheme"] = "paper" }
        val migrated = ReaderSettingsV30(paper)
        assertEquals("paper", migrated.dayTheme)
        assertTrue("paper" in migrated.dayInstalled)
        assertFalse(migrated.nightMode)
    }

    @Test fun importedFontKeysOnlyLoadWhenTheFileExists() {
        assertFalse(ReaderSettingsV30.isUsableFontKey("custom:/definitely/missing.ttf"))
        val file = java.io.File.createTempFile("font", ".ttf")
        try {
            assertTrue(ReaderSettingsV30.isUsableFontKey("custom:${file.absolutePath}"))
            val prefs = MemoryPrefs().apply { values["fontKey"] = "custom:/definitely/missing.ttf" }
            assertEquals(ReaderSettingsV30.DEFAULT_FONT_KEY, ReaderSettingsV30(prefs).fontKey)
        } finally {
            file.delete()
        }
    }

    /* ------------------------------ Quick / 定位 ------------------------------ */

    @Test fun pillSliderSnapsToStepsInsideTheRange() {
        assertEquals(1.8f, readerSnapV101(1.83f, 1.2f..2.4f, 0.05f), 0.0001f)
        assertEquals(12f, readerSnapV101(3f, 12f..34f, 1f), 0f)
        assertEquals(34f, readerSnapV101(99f, 12f..34f, 1f), 0f)
    }

    @Test fun locateMapsWholeBookPercentToChapterAndOffset() {
        assertEquals(0 to 0f, readerLocateTargetV101(0f, 10))
        val (index, fraction) = readerLocateTargetV101(0.25f, 10)
        assertEquals(2, index)
        assertEquals(0.5f, fraction, 0.0001f)
        assertEquals(9, readerLocateTargetV101(1f, 10).first)
        assertEquals(0 to 0f, readerLocateTargetV101(0.5f, 0))
    }

    @Test fun unknownDecorationKeyIsPlain() {
        assertEquals(ReaderThemeDecorV101.NONE, ReaderThemeDecorV101.of("sparkles"))
        assertNull(ReaderThemeCatalogV101.builtIn("sparkles"))
    }
}

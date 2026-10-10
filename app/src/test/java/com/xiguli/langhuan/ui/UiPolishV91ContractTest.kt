package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V91 UI polish contracts for the screens routed by LanghuanRootV4: shelf entry points, loading /
 * empty states, touch targets and the reader settings sheet.
 */
class UiPolishV91ContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    private fun source(path: String) =
        File(root, "src/main/java/com/xiguli/langhuan/$path").readText()

    @Test
    fun bottomTabsWireEveryFormerMoreMenuCallback() {
        val home = source("ui/LanghuanHomeV4.kt")
        assertFalse(home.contains("@Suppress(\"UNUSED_PARAMETER\")"))
        // V94: 「更多功能」 and the 我的 overlay are gone from the shelf; 我的 is a bottom tab.
        assertFalse(home.contains("contentDescription = \"更多功能\""))
        assertFalse(home.contains("HomeMinePageV93("))
        val router = source("ui/LanghuanRootV4.kt")
        assertTrue(router.contains("onCancelImport = localImportVm::cancelImport"))
        assertTrue(router.contains("onRunCenter = {\n                            returnAfterRunCenter = RootRouteV4.CREATE_HUB"))
        assertTrue(router.contains("onSkills = { openSkills(RootRouteV4.CREATE_HUB) }"))
        assertTrue(router.contains("onAiSetup = { openAiSetup(RootRouteV4.MINE) }"))
        assertTrue(router.contains("onNewBlankBook = libraryVm::createBlankStory"))
        assertTrue(router.contains("LanghuanBottomBarV94("))
    }

    @Test
    fun shelfHasLoadingAndSingleActionEmptyStates() {
        val home = source("ui/LanghuanHomeV4.kt")
        assertTrue(home.contains("val loadingShelf = !state.libraryLoaded && state.stories.isEmpty()"))
        assertTrue(home.contains("HomeBookListSkeletonV4()"))
        assertTrue(home.contains("HomeBookGridSkeletonV4()"))
        assertTrue(home.contains("text = \"导入本地书籍\","))
        assertTrue(home.contains("onCancel = onCancelImport.takeIf { importState.canCancel }"))
    }

    @Test
    fun shelfAvoidsPerComparisonPreferenceReadsAndKeepsTouchTargets() {
        val home = source("ui/LanghuanHomeV4.kt")
        assertTrue(home.contains("lastRead = { book -> lastReadAt[book.id] ?: 0L }"))
        assertFalse(home.contains("lastRead = { book -> progressPrefs.getLong("))
        assertEquals(44, HOME_TOOLBAR_BUTTON_DP_V91)
        assertFalse(home.contains(".size(42.dp)"))
        assertFalse(home.contains("indication = null"))
        assertTrue(home.contains("clickable(role = Role.Tab, onClick = onClick)"))
    }

    @Test
    fun readerSettingsPanelsScrollAndPreviewTypesetting() {
        val menu = source("ui/reader/ReaderMenuV30.kt")
        assertEquals(5, Regex("clip the lower rows of settings panels").findAll(menu).count())
        assertEquals(3, Regex("ReaderTypesetPreviewV91\\(settings = settings\\)").findAll(menu).count())
        assertTrue(menu.contains("FontFamily(readerTypefaceV30(settings.fontKey, settings.weight))"))
        // The search panel hosts a LazyColumn and must not be wrapped in a vertical scroll.
        val search = menu.substring(
            menu.indexOf("private fun ReaderSearchPanelV30("),
            menu.indexOf("private fun ReaderStatsPanelV30("),
        )
        assertFalse(search.contains("verticalScroll"))
        assertTrue(menu.contains(".selectable(selected = selected, role = Role.RadioButton, onClick = onClick)"))
    }

    @Test
    fun onlineSearchDismissesTheKeyboard() {
        val online = source("ui/online/OnlineBooksScreenV50.kt")
        assertTrue(online.contains("keyboard?.hide()"))
        assertTrue(online.contains("if (query.isNotBlank() && !searching) submit()"))
    }
}

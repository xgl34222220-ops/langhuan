package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCrashGuardV32ContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun localImportDoesNotPoisonStudioActiveProject() {
        val project = File(root, "src/main/java/com/xiguli/langhuan/data/StoryProjectManager.kt").readText()
        val rootUi = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val studio = File(root, "src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt").readText()

        // Reader imports must not publish a temporary Studio selection, even before rollback.
        assertTrue(project.contains("selectActive = false"))
        assertTrue(project.contains("if (selectActive) setActiveStoryId(id)"))
        val importBody = project.substringAfter("private suspend fun createImportedStoryInTransaction")
            .substringBefore("/** Appends downloaded chapters")
        assertFalse(importBody.contains("setActiveStoryId("))
        assertFalse(importBody.contains("clearActiveStoryId("))
        assertFalse(rootUi.contains("LaunchedEffect(libraryState.openedBook?.id)"))
        assertTrue(rootUi.contains("if (target == RootRouteV4.TAVERN)"))
        assertTrue(studio.contains("local_book_meta_v1"))
        assertTrue(studio.contains("projects.clearActiveStoryId()"))
        assertTrue(studio.contains("Persist only after the whole Studio restore path succeeds"))
        assertTrue(studio.contains("上次项目恢复失败，已安全回到书架"))
    }
}

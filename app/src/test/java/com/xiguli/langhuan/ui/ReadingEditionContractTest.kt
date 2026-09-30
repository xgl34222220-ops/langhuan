package com.xiguli.langhuan.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReadingEditionContractTest {
    private fun source(path: String) = File("src/main/java/com/xiguli/langhuan/ui/$path").readText()
    private fun fingerprint(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun paperShelfKeepsThreeColumnsRealDataAndExistingInteractions() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        val library = shelf.substring(shelf.indexOf("private fun LuoShelfLibraryV1("), shelf.indexOf("private fun LuoShelfCreateV1("))
        assertTrue(library.contains("GridCells.Fixed(3)"))
        assertTrue(library.contains("gridItemsIndexed(books, key = { _, book -> book.id })"))
        assertTrue(library.contains("luoSortBooksV33(state.stories, sort)"))
        assertTrue(library.contains("onSort(sort.next())"))
        assertTrue(library.contains("assignments[it.id] == activeShelf"))
        assertTrue(library.contains("it.title.contains(query, true) || it.genre.contains(query, true)"))
        assertTrue(library.contains("LuoShelfTabsV33(shelves, shelfCounts, activeShelf, onShelf"))
        assertTrue(library.contains("onLongClick = { onLongPress(book) }"))
        assertTrue(library.contains("onClick = { onOpenBook(book.id) }"))
        assertTrue(library.contains("!state.libraryLoaded -> LuoShelfSkeletonV31()"))
        assertTrue(library.contains("visible = importState.busy"))
        assertTrue(library.contains("LuoBookCoverV1(book,"))
        assertFalse(library.contains("listOf(ReaderBookUi("))
    }

    @Test fun realCoverLoadingStaysByteIdenticalToApprovedBaseline() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        val cover = shelf.substring(shelf.indexOf("private fun LuoBookCoverV1("), shelf.indexOf("private fun luoGreetingV31("))
        assertEquals("3318c73b422fbed2986e405c34a155223b991c452c9f527ebb578c19fcd560c4", fingerprint(cover))
    }

    @Test fun compactDockKeepsSafeInsetsAndAccessibleThreeWaySelection() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        val dock = shelf.substring(shelf.indexOf("private fun LuoFloatingDockV1("), shelf.indexOf("private fun LuoShelfHomeV1("))
        assertTrue(dock.contains("navigationBarsPadding()"))
        assertTrue(dock.contains("padding(horizontal = 16.dp)"))
        assertTrue(dock.contains("bottom = 12.dp"))
        assertTrue(dock.contains("height(64.dp)"))
        assertTrue(dock.contains("minOf(64.dp, slot)"))
        assertTrue(dock.contains("selected = active, role = Role.Tab"))
        assertTrue(dock.contains("onSelect(target)"))
        assertTrue(shelf.contains("Box(Modifier.weight(1f))"))
    }

    @Test fun threeDestinationsAndBookstoreUseTheRealSourceViewModel() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        assertTrue(shelf.contains("val labels = listOf(\"书架\", \"书城\", \"我的\")"))
        assertTrue(shelf.contains("mutableStateOf(LuoShelfScreenV1.SHELF)"))
        assertTrue(shelf.contains("LuoShelfScreenV1.BOOKSTORE -> onlineContent(manageSources)"))
        assertTrue(source("LanghuanRootV4.kt").contains("viewModel = onlineVm"))
        assertTrue(source("shell/ReaderProfileV41.kt").contains("AnimatedVisibility(advanced)"))
        assertFalse(source("shell/ReaderProfileV41.kt").contains("CloudSync"))
    }
}

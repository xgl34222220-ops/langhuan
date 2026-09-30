package com.xiguli.langhuan.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReadingEditionContractTest {
    private fun source(path: String) = File("src/main/java/com/xiguli/langhuan/ui/$path").readText()
    private fun fingerprint(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun shelfContentAndCoverStayByteIdenticalToApprovedBaseline() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        val library = shelf.substring(shelf.indexOf("private fun LuoShelfLibraryV1("), shelf.indexOf("private fun LuoShelfCreateV1("))
        val cover = shelf.substring(shelf.indexOf("private fun LuoBookCoverV1("), shelf.indexOf("private fun luoGreetingV31("))
        assertEquals("7fd715d9ceec2022db832c375620aa3ee24b691b562ab1422c268168eadfd0b8", fingerprint(library))
        assertEquals("3318c73b422fbed2986e405c34a155223b991c452c9f527ebb578c19fcd560c4", fingerprint(cover))
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

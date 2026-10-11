package com.xiguli.langhuan.ui

import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** 搜索聚合 / 换源 / 书源校验 logic, and the Legado rule fixes that made real sources readable. */
class SourceSwitchV97Test {
    private fun book(source: String, name: String, author: String = "", url: String = "https://$source.example/b/1") =
        OnlineBookV36(source, "源$source", name, author, "", "", "", url)

    private fun src(id: String) = BookSourceV36(id, "源$id", "https://$id.example", searchUrl = "/s?q={{key}}", searchList = ".b")

    private fun catalogue(source: String, titles: List<String>) = OnlineBookCatalogueV50(
        book(source, "斗罗大陆", "唐家三少"),
        titles.mapIndexed { i, t -> OnlineChapterV36(t, "https://$source.example/c/$i") },
        SourceCatalogueProofV50(tocUrl = "https://$source.example/b/1", pagesRead = 1, declaredTotal = null, latestOrdinal = null,
            warnings = emptyList(), hasCompletenessEvidence = false, evidence = SourceCatalogueEvidenceV51.PARSED_ONLY),
    )

    // ---- 搜索聚合 ----

    @Test fun mergesSameBookFromSeveralSourcesIntoOneRow() {
        val rows = aggregateSearchResultsV97(listOf(
            book("a", "斗罗大陆", "唐家三少"),
            book("b", "《斗罗大陆》", "作者：唐家三少"),
            book("c", "斗罗大陆 ", ""),
            book("d", "斗罗大陆", "别人"),
            book("a", "斗罗大陆2绝世唐门", "唐家三少"),
        ), "斗罗大陆")
        val first = rows.first()
        assertEquals("斗罗大陆", first.primary.name)
        assertEquals(listOf("a", "b", "c"), first.origins.map { it.sourceId })
        assertEquals(3, first.sourceCount)
        // A different author is a different book with the same title.
        assertTrue(rows.any { it.primary.sourceId == "d" && it.sourceCount == 1 })
        assertEquals(3, rows.size)
    }

    @Test fun oneSourceIsCountedOncePerBookAndOrderFollowsRelevance() {
        val rows = aggregateSearchResultsV97(listOf(
            book("a", "我的斗罗大陆", "x"),
            book("a", "斗罗大陆", "唐家三少", "https://a.example/b/1"),
            book("a", "斗罗大陆", "唐家三少", "https://a.example/b/2"),
            book("b", "斗罗大陆", "唐家三少"),
        ), "斗罗大陆")
        assertEquals("斗罗大陆", rows[0].primary.name)
        assertEquals(2, rows[0].sourceCount)
        assertEquals("我的斗罗大陆", rows[1].primary.name)
    }

    @Test fun normalizesTitleNoiseAndAuthorPrefixes() {
        assertEquals(normalizeBookNameV97("斗罗大陆"), normalizeBookNameV97("《斗罗大陆》最新章节"))
        assertEquals(normalizeAuthorV97("唐家三少"), normalizeAuthorV97("作者：唐家三少 著"))
        assertTrue(sameBookV97("斗罗大陆", "唐家三少", "斗罗大陆（全本）", ""))
        assertFalse(sameBookV97("斗罗大陆", "唐家三少", "斗罗大陆", "天蚕土豆"))
    }

    // ---- 阅读位置 ----

    @Test fun keepsPositionByChapterTitleAcrossCataloguesWithDifferentPrefixes() {
        val old = listOf("第一章 斗罗大陆", "第二章 唐三", "第三章 武魂", "第四章 觉醒")
        val new = listOf("作品相关", "公告", "第一章 斗罗大陆", "第二章 唐三", "第三章 武魂", "第四章 觉醒")
        assertEquals(4, mapChapterIndexV97(old, 2, new))
        // Same chapter written differently: punctuation, spaces, a volume prefix.
        assertEquals(1, mapChapterIndexV97(listOf("第1章 开始", "第2章：唐三！"), 1, listOf("正文 第1章 开始", "第2章 唐三")))
    }

    @Test fun fallsBackToChapterNumberThenIndex() {
        val old = listOf("第1章 甲", "第2章 乙", "第3章 丙")
        assertEquals(2, mapChapterIndexV97(old, 1, listOf("序", "第1章 A", "第2章 B", "第3章 C")))
        assertEquals(1, mapChapterIndexV97(listOf("x", "y", "z"), 1, listOf("a", "b", "c")))
        assertEquals(1, mapChapterIndexV97(listOf("x", "y", "z"), 2, listOf("a", "b")))
        assertEquals(0, mapChapterIndexV97(old, 0, emptyList()))
    }

    @Test fun repeatedTitlesPickTheOneNearestTheOldPosition() {
        val old = (1..10).map { "第${it}章 正文" } + listOf("求月票")
        val new = listOf("求月票") + (1..10).map { "第${it}章 正文" } + listOf("求月票")
        assertEquals(11, mapChapterIndexV97(old, 10, new))
    }

    // ---- 换源候选 ----

    @Test fun candidatesStreamPerSourceWithCatalogueAndTimeouts() = runBlocking {
        val found = Collections.synchronizedList(ArrayList<SourceCandidateV97>())
        val updated = Collections.synchronizedList(ArrayList<SourceCandidateV97>())
        val failures = Collections.synchronizedList(ArrayList<String>())
        searchSourceCandidatesV97(
            listOf(src("fast"), src("slow"), src("other"), src("broken")),
            "斗罗大陆", "唐家三少",
            search = { s, _ ->
                when (s.id) {
                    "slow" -> { Thread.sleep(5_000); emptyList() }
                    "other" -> listOf(book("other", "斗破苍穹", "天蚕土豆"))
                    "broken" -> error("搜索规则错误")
                    else -> listOf(book(s.id, "斗罗大陆", "唐家三少"))
                }
            },
            catalogue = { s, _ -> catalogue(s.id, listOf("第一章", "第二章", "第三章")) },
            timeoutMs = 400,
            onFound = { found += it }, onUpdated = { updated += it },
            onSourceDone = { s, e -> if (e != null) failures += s.id + ":" + (e is java.net.SocketTimeoutException) },
        )
        assertEquals(listOf("fast"), found.map { it.sourceId })
        assertEquals(3, updated.single().chapterCount)
        assertEquals("第三章", updated.single().latestChapter)
        assertTrue(failures.contains("slow:true"))
        assertTrue(failures.any { it.startsWith("broken:") })
    }

    @Test fun authorFilterAndSortingPutUsableFastSourcesFirst() {
        val state = SourceSwitchStateV97("斗罗大陆", "唐家三少", "cur", running = false, candidates = listOf(
            SourceCandidateV97("slow", "慢", book("slow", "斗罗大陆", "唐家三少"), chapterCount = 10, responseMs = 900),
            SourceCandidateV97("fast", "快", book("fast", "斗罗大陆", "唐家三少"), chapterCount = 10, responseMs = 100),
            SourceCandidateV97("bad", "坏", book("bad", "斗罗大陆", "唐家三少"), chapterCount = 0, responseMs = 50, tocError = "目录失败"),
            SourceCandidateV97("wrong", "错", book("wrong", "斗罗大陆", "别人"), chapterCount = 10, responseMs = 10),
            SourceCandidateV97("cur", "当前", book("cur", "斗罗大陆", "唐家三少"), current = true, responseMs = 5000),
        ))
        assertEquals(listOf("cur", "fast", "slow", "bad"), visibleCandidatesV97(state).map { it.sourceId })
        assertEquals(5, visibleCandidatesV97(state.copy(checkAuthor = false)).size)
    }

    @Test fun picksSameNameResultPreferringAuthor() {
        val picked = pickSourceResultV97(listOf(book("a", "斗罗大陆外传", "x"), book("a", "斗罗大陆", "别人", "u1"),
            book("a", "斗罗大陆", "唐家三少", "u2")), "斗罗大陆", "唐家三少")
        assertEquals("u2", picked?.bookUrl)
        assertNull(pickSourceResultV97(listOf(book("a", "斗破苍穹")), "斗罗大陆", ""))
    }

    // ---- 书源校验 ----

    @Test fun checkReportsTheStageThatFailed() = runBlocking {
        val ok = checkSourceV97(src("ok"), "我的", search = { s, _ -> listOf(book(s.id, "我的书")) },
            catalogue = { s, _ -> catalogue(s.id, listOf("第一章")) }, content = { _, _, _, _ -> "正文" })
        assertEquals(SourceCheckStatusV97.OK, ok.status)
        val empty = checkSourceV97(src("e"), "我的", search = { _, _ -> emptyList() })
        assertEquals(SourceCheckStatusV97.SEARCH_FAILED, empty.status)
        val toc = checkSourceV97(src("t"), "我的", search = { s, _ -> listOf(book(s.id, "我的书")) },
            catalogue = { _, _ -> error("目录规则失效") })
        assertEquals(SourceCheckStatusV97.TOC_FAILED, toc.status)
        val noChapters = checkSourceV97(src("n"), "我的", search = { s, _ -> listOf(book(s.id, "我的书")) },
            catalogue = { s, _ -> catalogue(s.id, emptyList()) })
        assertEquals(SourceCheckStatusV97.TOC_FAILED, noChapters.status)
        val content = checkSourceV97(src("c"), "我的", search = { s, _ -> listOf(book(s.id, "我的书")) },
            catalogue = { s, _ -> catalogue(s.id, listOf("第一章")) }, content = { _, _, _, _ -> error("正文规则未提取到可读文字") })
        assertEquals(SourceCheckStatusV97.CONTENT_FAILED, content.status)
        val slow = checkSourceV97(src("s"), "我的", search = { _, _ -> Thread.sleep(3_000); emptyList() }, timeoutMs = 200)
        assertEquals(SourceCheckStatusV97.TIMEOUT, slow.status)
        val unconfigured = checkSourceV97(BookSourceV36("u", "无搜索", "https://u.example"), "我的")
        assertEquals(SourceCheckStatusV97.SEARCH_FAILED, unconfigured.status)
    }

    @Test fun checkedSourcesSortUsableFirstAndListFailures() {
        val sources = listOf(src("a"), src("b"), src("c"), src("d"))
        val state = SourceCheckStateV97("我的", running = false, total = 4, results = mapOf(
            "a" to SourceCheckResultV97("a", SourceCheckStatusV97.TIMEOUT),
            "b" to SourceCheckResultV97("b", SourceCheckStatusV97.OK),
            "c" to SourceCheckResultV97("c", SourceCheckStatusV97.CHECKING),
        ))
        assertEquals(listOf("b", "c", "d", "a"), sortSourcesByCheckV97(sources, state).map { it.id })
        assertEquals(listOf("a"), state.failedIds)
        assertEquals(2, state.done)
        assertEquals(sources, sortSourcesByCheckV97(sources, null))
    }

    // ---- Legado rule semantics that real sources rely on ----

    @Test fun lastSegmentOfAValueRuleIsAnAttributeLikeLegado() {
        val doc = Jsoup.parse("<div class='v-list-item' onclick=\"newWebView('/b/229009.html', '', '')\">" +
            "<a class='t' lay-href='/book/9.html' href='#'>标题</a></div>", "https://m.example/s")
        val item = doc.selectFirst(".v-list-item")!!
        assertEquals("newWebView('/b/229009.html', '', '')", ruleStringV36(item, "@onclick"))
        assertEquals("/book/9.html", ruleStringV36(doc, "class.t@lay-href"))
        assertEquals("https://m.example/book/9.html", ruleStringV36(doc, "class.t@abs:lay-href"))
        // A last segment that is a real selector, not an attribute present on the element, keeps working.
        assertEquals("标题", ruleStringV36(doc, "class.v-list-item@a"))
        assertEquals("标题", ruleStringV36(doc, "class.v-list-item@a@text"))
    }

    @Test fun nextPageThatPointsBackEndsTheChapterInsteadOfFailing() {
        val base = "https://books.example"
        val source = BookSourceV36("loop", "fixture", base, contentText = "#content@html", contentNext = "text.下一页@href")
        val pages = mapOf(
            "$base/c/1.html" to "<h1>第一章</h1><div id='content'>第一页正文</div><a href='/c/1_2.html'>下一页</a>",
            "$base/c/1_2.html" to "<h1>第一章</h1><div id='content'>第二页正文</div><a href='/c/1.html'>下一页</a>",
        )
        val text = loadChapterTextV36(source, OnlineChapterV36("第一章", "$base/c/1.html"), setOf("$base/c/1.html")) { _, r ->
            Jsoup.parse(pages.getValue(r.url), r.url)
        }
        assertEquals("第一页正文\n第二页正文", text)
    }

    @Test fun tlsAndStaleConnectionFailuresAreRecognized() {
        val tls = javax.net.ssl.SSLHandshakeException("PKIX path building failed").apply {
            initCause(java.security.cert.CertificateException("unable to find valid certification path"))
        }
        assertTrue(sourceCertificateFailureV97(tls))
        assertTrue(sourceCertificateFailureV97(java.io.IOException("x", javax.net.ssl.SSLPeerUnverifiedException("Hostname not verified"))))
        assertFalse(sourceCertificateFailureV97(javax.net.ssl.SSLHandshakeException("Remote host terminated the handshake")))
        assertTrue(sourceStaleConnectionV97(java.io.IOException("unexpected end of stream on https://x.example/...")))
        assertFalse(sourceStaleConnectionV97(java.net.SocketTimeoutException("timeout")))
    }
}

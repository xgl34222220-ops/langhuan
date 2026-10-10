package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V94: real-world 阅读（Legado）书源 must import. The fixture `legado/xiu2-shuyuan.json` is the public
 * XIU2/Yuedu source list (22 sources) — before V94 only 4 of them imported because the request
 * options use single-quoted object literals and optional rules carry small `@js:` snippets.
 */
class BookSourceImportV94Test {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun realWorldLegadoListImportsStaticSourcesAndNamesEverySkip() {
        val result = parseBookSourcesV36(fixture("legado/xiu2-shuyuan.json"))
        val names = result.sources.map { it.name }
        // The single-quoted request-option sources now import.
        listOf("速读谷", "手机小说", "铅笔小说", "得奇小说网", "快书网", "天天看小说", "独步小说网", "就爱文学", "武林中文网").forEach {
            assertTrue("$it should import, got $names / ${result.skipped}", it in names)
        }
        assertTrue("imported ${names.size}: $names", result.sources.size >= 9)
        assertEquals(22, result.sources.size + result.skipped.size)
        // No skip is a raw parser error any more; each names its reason in parentheses.
        result.skipped.forEach {
            assertFalse(it, it.contains("Unexpected JSON token"))
            assertTrue(it, it.contains("（"))
        }
        // Script-only sources stay out with an explicit reason.
        assertTrue(result.skipped.any { it.startsWith("起点中文") && it.contains("JavaScript") })
        // 69书吧 needs a script for its table-of-contents URL, an essential rule.
        assertTrue(result.skipped.any { it.startsWith("69书吧（") && it.contains("目录地址") })
        assertTrue(result.skipped.any { it.startsWith("酷我小说") && it.contains("JSON") })
    }

    @Test fun singleQuotedPostOptionsBuildTheRealRequest() {
        val sources = parseBookSourcesV36(fixture("legado/xiu2-shuyuan.json")).sources
        val gbk = sources.first { it.name == "手机小说" }
        val request = buildSearchRequestV36(gbk, "剑来")
        assertEquals("POST", request.method)
        assertEquals("gbk", request.charset)
        assertTrue(request.url, request.url.endsWith("/modules/article/search.php"))
        assertEquals("searchkey=%BD%A3%C0%B4", request.body)
        // The cookie header was a single-quoted object literal too.
        assertTrue(gbk.headers.keys.any { it.equals("cookie", true) })
        // 速读谷: lower-case 'post'; its optional cover rule needed @js: and is dropped.
        val sudugu = sources.first { it.name == "速读谷" }
        assertEquals("POST", buildSearchRequestV36(sudugu, "剑来").method)
        assertEquals("", sudugu.searchCover)
        assertEquals("class.bookbox", sudugu.searchList)
    }

    @Test fun objectLiteralConversionKeepsEscapesAndDoubleQuotes() {
        assertEquals("""{"method":"POST","body":"a=\"b\""}""", jsObjectLiteralToJsonV94("""{'method':'POST','body':'a="b"'}"""))
        assertEquals("""{"k":"it's"}""", jsObjectLiteralToJsonV94("""{'k':'it\'s'}"""))
        assertEquals("POST", parseSourceOptionsV94("{'method': 'POST','body': 'keyword={{key}}'}")["method"].toString().trim('"'))
        assertEquals("gbk", parseSourceOptionsV94("""{"charset":"gbk"}""")["charset"].toString().trim('"'))
        assertTrue(runCatching { parseSourceOptionsV94("not an object") }.exceptionOrNull()?.message.orEmpty().contains("JSON 对象"))
    }

    @Test fun optionalScriptRulesAreDroppedButEssentialOnesSkipWithTheField() {
        val base = """"bookSourceName":"甲","bookSourceUrl":"https://a.example","searchUrl":"/s?q={{key}}""""
        val optional = parseBookSourcesV36("""{$base,"ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href","coverUrl":"tag.img@src@js:result+'x'","intro":"//div[@class='i']/text()"}}""")
        val imported = optional.sources.single()
        assertEquals("", imported.searchCover)
        assertEquals("", imported.searchIntro)
        assertTrue(optional.warnings.single(), optional.warnings.single().contains("搜索封面"))
        assertTrue(optional.warnings.single().contains("XPath"))

        val essential = parseBookSourcesV36("""{$base,"ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href"},"ruleContent":{"content":"<js>java.ajax(baseUrl)</js>"}}""")
        assertTrue(essential.sources.isEmpty())
        assertTrue(essential.skipped.single(), essential.skipped.single().contains("正文"))
    }

    @Test fun brokenDiscoveryDisablesDiscoveryInsteadOfLosingTheSource() {
        val raw = """{"bookSourceName":"乙","bookSourceUrl":"https://b.example","searchUrl":"/s?q={{key}}",
            "exploreUrl":"@js:java.ajax('x')","ruleExplore":{"bookList":"li"},
            "ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href"}}"""
        val result = parseBookSourcesV36(raw)
        val source = result.sources.single()
        assertEquals("", source.exploreUrl)
        assertTrue(result.warnings.single().contains("停用发现"))
        // Title-only Legado headings in an explore array are skipped, real categories kept.
        val headings = parseBookSourcesV36("""{"bookSourceName":"丙","bookSourceUrl":"https://c.example","searchUrl":"/s?q={{key}}",
            "exploreUrl":"[{\"title\":\"榜单\",\"url\":\"\"},{\"title\":\"总榜\",\"url\":\"/top-{{page}}.html\"}]",
            "ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href"}}""").sources.single()
        assertEquals(listOf("总榜"), sourceDiscoveriesV41(headings).map { it.label })
    }

    @Test fun inputShapesAndErrorsAreFriendly() {
        val bom = "\uFEFF" + """[{"bookSourceName":"丁","bookSourceUrl":"https://d.example","searchUrl":"/s?q={{key}}","ruleSearch":{"bookList":"li","name":"a@text","bookUrl":"a@href"}}]"""
        assertEquals("丁", parseBookSourcesV36(bom).sources.single().name)
        assertTrue(runCatching { parseBookSourcesV36("<html><body>404</body></html>") }.exceptionOrNull()!!.message!!.contains("网页"))
        assertTrue(runCatching { parseBookSourcesV36("") }.exceptionOrNull()!!.message!!.contains("为空"))
        val bad = runCatching { parseBookSourcesV36("""[{"bookSourceName":"x",}""") }.exceptionOrNull()!!
        assertTrue(sourceJsonErrorV94(bad), sourceJsonErrorV94(bad).contains("不是有效的书源 JSON"))
        val audio = parseBookSourcesV36("""{"bookSourceName":"听书","bookSourceType":1,"bookSourceUrl":"https://e.example","searchUrl":"/s?q={{key}}","ruleSearch":{"bookList":"li"}}""")
        assertTrue(audio.skipped.single().contains("音频"))
    }

    @Test fun shareLinksUnwrapToTheirSourceUrl() {
        assertEquals("https://x.example/a.json", sourceImportUrlV94("legado://import/bookSource?src=https%3A%2F%2Fx.example%2Fa.json"))
        assertEquals("https://x.example/b.json", sourceImportUrlV94("yuedu://booksource/importonline?src=https://x.example/b.json"))
        assertEquals("https://x.example/c.json", sourceImportUrlV94("  https://x.example/c.json "))
        assertTrue(sourceInputIsUrlV94("legado://import/bookSource?src=x"))
        assertFalse(sourceInputIsUrlV94("""[{"bookSourceUrl":"https://x"}]"""))
    }

    @Test fun handMadeSourceValidatesSavesAndSearches() {
        // The 新建书源 form edits a BookSourceV36 and serialises it as the draft.
        var draft = newSourceTemplateV94()
        assertEquals(listOf("书源名称", "网站地址", "搜索地址与书籍列表（或发现地址与列表）"), sourceFormMissingV94(draft.copy(searchUrl = "")))
        draft = draft.copy(name = "手工书源", baseUrl = "https://books.example", searchList = "class.item", searchName = "tag.a@text", searchBookUrl = "tag.a@href",
            tocList = "id.list@tag.dd", tocName = "tag.a@text", tocUrl = "tag.a@href", contentText = "id.content@html")
        assertTrue(sourceFormMissingV94(draft).isEmpty())
        val validated = validateSourceDraftV94(encodeSourceDraftV94(draft))
        assertEquals("手工书源", validated.name)
        assertEquals("https://books.example", manualSourceIdV94(validated.baseUrl + "/"))
        val html = "<div class=item><a href='/b/1'>长夜</a></div><div class=item><a href='/b/2'>白昼</a></div>"
        val books = searchSourceV36(validated.copy(id = manualSourceIdV94(validated.baseUrl)), "长") { _, request -> Jsoup.parse(html, request.url) }
        assertEquals(listOf("长夜", "白昼"), books.map { it.name })
        assertEquals("https://books.example/b/1", books.first().bookUrl)
    }

    @Test fun editorAcceptsOneLegadoObjectAndRejectsBatches() {
        val legado = """{"bookSourceName":"戊","bookSourceUrl":"https://f.example","searchUrl":"/s?q={{key}}","ruleSearch":{"bookList":"li","name":"a@text","bookUrl":"a@href"}}"""
        assertEquals("https://f.example", validateSourceDraftV94(legado).id)
        assertNotNull(sourceDraftFormV94(legado))
        assertTrue(runCatching { validateSourceDraftV94("[$legado,$legado]") }.exceptionOrNull()!!.message!!.contains("一次只能编辑一个"))
        assertTrue(runCatching { validateSourceDraftV94("""{"bookSourceName":"x","bookSourceUrl":"https://g.example"}""") }.exceptionOrNull()!!.message!!.startsWith("无法保存"))
    }

    @Test fun manageGroupsComeFromTheSources() {
        val a = BookSourceV36("a", "A", "https://a.example", group = "精品,男频")
        val b = BookSourceV36("b", "B", "https://b.example", group = "精品")
        val c = BookSourceV36("c", "C", "https://c.example")
        assertEquals(listOf("男频", "精品", SOURCE_GROUP_UNGROUPED_V94), sourceManageGroupsV94(listOf(a, b, c)))
        assertTrue(sourceManageGroupsV94(listOf(c)).isEmpty())
    }
}

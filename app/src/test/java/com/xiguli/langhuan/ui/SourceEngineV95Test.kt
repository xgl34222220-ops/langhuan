package com.xiguli.langhuan.ui

import java.io.File
import java.nio.file.Files
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V95: Legado rules that used to be dropped or rejected — XPath, JSONPath, sandboxed scripts —
 * and online covers that never reached the shelf.
 */
class SourceEngineV95Test {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun source(json: String) = parseBookSourcesV36(json).also { assertTrue(it.skipped.toString(), it.skipped.isEmpty()) }.sources.single()

    private val listHtml = """
        <html><body>
          <div class="item"><h3><a href="/book/1">长夜</a></h3><p class="a">作者：甲</p><img data-original="/cover/1.jpg" src="/lazy.gif"></div>
          <div class="item"><h3><a href="/book/2">未央</a></h3><p class="a">作者：乙</p><img data-original="//img.example.net/2.webp" src="/lazy.gif"></div>
        </body></html>
    """.trimIndent()

    // ---- XPath ---------------------------------------------------------------------------------

    @Test fun xpathRulesSelectListsAndValues() {
        val doc = Jsoup.parse(listHtml, "https://www.example.com/s")
        val items = ruleElementsV36(doc, "//div[@class='item']")
        assertEquals(2, items.size)
        assertEquals("长夜", ruleStringV36(items[0], "//h3/a/text()"))
        assertEquals("/book/2", ruleStringV36(items[1], "@XPath://h3/a/@href"))
        assertEquals("/cover/1.jpg", ruleStringV36(items[0], "//img/@data-original"))
        assertEquals("甲", ruleStringV36(items[0], "//p[@class='a']/text()##作者："))
        // XPath as an alternative after a default rule
        assertEquals("长夜", ruleStringV36(items[0], "class.missing@text||//h3/a/text()"))
    }

    // ---- JSONPath ------------------------------------------------------------------------------

    private val apiJson = """{"code":0,"data":{"list":[
        {"id":11,"name":"长夜","author":"甲","cover":"https://img.example.net/11.jpg","tags":["玄幻","长篇"]},
        {"id":12,"name":"未央","author":"乙","cover":"","tags":[]}
    ],"total":2}}"""

    @Test fun jsonApiSearchReadsJsonPathAndTemplates() {
        val src = source("""{"bookSourceName":"接口站","bookSourceUrl":"https://api.example.com","searchUrl":"/search?kw={{key}}&p={{page}}",
            "ruleSearch":{"bookList":"$.data.list[*]","name":"$.name","author":"author","coverUrl":"$.cover",
            "kind":"$.tags[*]","bookUrl":"/book/{$.id}?src=api"}}""")
        val books = searchSourceV36(src, "长夜") { _, request ->
            assertTrue(request.url, request.url.startsWith("https://api.example.com/search?kw=%E9%95%BF%E5%A4%9C&p=1"))
            parseSourceDocumentV44(apiJson.toByteArray(), request.url, type = "application/json")
        }
        assertEquals(listOf("长夜", "未央"), books.map { it.name })
        assertEquals("甲", books[0].author)
        assertEquals("https://img.example.net/11.jpg", books[0].cover)
        assertEquals("https://api.example.com/book/11?src=api", books[0].bookUrl)
    }

    @Test fun jsonPathSubsetMatchesJaywayBasics() {
        val root = BookSourceJsonV36.parseToJsonElement(apiJson)
        assertEquals(2, jsonPathV95(root, "$.data.list[*].name").size)
        assertEquals("未央", jsonTextV95(jsonPathV95(root, "$.data.list[-1].name").single()))
        assertEquals("长夜", jsonTextV95(jsonPathV95(root, "$..list[?(@.id == 11)].name").single()))
        assertEquals(listOf("11", "12"), jsonPathV95(root, "$..id").map(::jsonTextV95))
        assertEquals("2", jsonTextV95(jsonPathV95(root, "data.total").single()))
        assertEquals(1, jsonPathV95(root, "$.data.list[0:1]").size)
        assertEquals(2, jsonPathV95(root, "$['data']['list'][?(@.cover)]").size)
    }

    @Test fun detailInitAndJsonTocFollowScriptedPages() {
        val src = source("""{"bookSourceName":"接口站","bookSourceUrl":"https://api.example.com","searchUrl":"/s?q={{key}}",
            "ruleSearch":{"bookList":"$.list","name":"$.name","bookUrl":"$.url"},
            "ruleBookInfo":{"init":"$.data","name":"$.title","coverUrl":"$.img","intro":"$.desc","tocUrl":"/toc/{$.id}?page=1"},
            "ruleToc":{"chapterList":"$.chapters[*]","chapterName":"$.t","chapterUrl":"/c/{$.cid}","nextTocUrl":"@js:JSON.parse(src).more ? baseUrl.replace('page=1','page=2') : ''"},
            "ruleContent":{"content":"$.text"}}""")
        val book = OnlineBookV36(src.id, src.name, "旧名", "", "", "", "", "https://api.example.com/book/7")
        val pages = mapOf(
            "https://api.example.com/book/7" to """{"data":{"id":7,"title":"长夜","img":"/covers/7.png","desc":"简介"}}""",
            "https://api.example.com/toc/7?page=1" to """{"more":true,"chapters":[{"t":"第一章","cid":1},{"t":"第二章","cid":2}]}""",
            "https://api.example.com/toc/7?page=2" to """{"more":false,"chapters":[{"t":"第三章","cid":3}]}""",
            "https://api.example.com/c/1" to """{"text":"<p>第一段</p><p>第二段</p>"}""",
        )
        val fetch: (BookSourceV36, SourceRequestV36) -> Document = { _, request ->
            parseSourceDocumentV44(pages.getValue(request.url).toByteArray(), request.url)
        }
        val catalogue = loadBookCatalogueV50(src, book, fetch)
        assertEquals("长夜", catalogue.book.name)
        assertEquals("https://api.example.com/covers/7.png", catalogue.book.cover)
        assertEquals("简介", catalogue.book.intro)
        assertEquals(listOf("第一章", "第二章", "第三章"), catalogue.chapters.map { it.title })
        assertEquals("https://api.example.com/c/3", catalogue.chapters.last().url)
        val text = loadChapterTextV36(src, catalogue.chapters.first(), catalogue.chapters.map { it.url }.toSet(), fetch)
        assertEquals("第一段\n第二段", text)
    }

    // ---- Scripts ---------------------------------------------------------------------------------

    @Test fun scriptRulesTransformValuesWithJsLibAndJavaHelpers() {
        val src = source("""{"bookSourceName":"脚本站","bookSourceUrl":"https://www.example.com","searchUrl":"/s?q={{key}}",
            "jsLib":"function tidy(s){ return String(s).replace(/作者：/,'').trim(); }",
            "ruleSearch":{"bookList":"class.item","name":"tag.h3@text@js:result+'·'+java.md5Encode16('a').length",
            "author":"class.a@text@js:tidy(result)","coverUrl":"tag.img@data-original",
            "intro":"<js>java.base64Decode('5aSc')</js>","bookUrl":"tag.a@href@js:result.replace('/book/','/b/')"}}""")
        val books = searchSourceV36(src, "x") { _, request -> Jsoup.parse(listHtml, request.url) }
        assertEquals("长夜·16", books[0].name)
        assertEquals("甲", books[0].author)
        assertEquals("夜", books[0].intro)
        assertEquals("https://www.example.com/b/1", books[0].bookUrl)
        assertEquals("https://img.example.net/2.webp", books[1].cover)
    }

    @Test fun scriptedSearchUrlsPageListsAndHeaderOptions() {
        val base = """"bookSourceName":"甲","bookSourceUrl":"https://www.example.com","ruleSearch":{"bookList":"li","name":"a@text","bookUrl":"a@href"}"""
        val js = source("""{$base,"searchUrl":"@js:(page == 1) ? '/s/{{key}}.html' : '/s/{{key}}_' + page + '.html'"}""")
        assertEquals("https://www.example.com/s/%E9%95%BF%E5%A4%9C.html", buildSearchRequestV36(js, "长夜", 1).url)
        assertEquals("https://www.example.com/s/%E9%95%BF%E5%A4%9C_3.html", buildSearchRequestV36(js, "长夜", 3).url)
        val expression = source("""{$base,"searchUrl":"/s?q={{key}}&start={{(page-1)*20}}&k={{java.encodeURI(key)}}"}""")
        assertEquals("https://www.example.com/s?q=%E9%95%BF&start=40&k=%E9%95%BF", buildSearchRequestV36(expression, "长", 3).url)
        val pages = source("""{$base,"searchUrl":"/list<,_2,_3>.html?q={{key}}"}""")
        assertEquals("https://www.example.com/list.html?q=a", buildSearchRequestV36(pages, "a", 1).url)
        assertEquals("https://www.example.com/list_3.html?q=a", buildSearchRequestV36(pages, "a", 9).url)
        val headers = source("""{$base,"searchUrl":"/api,{\"method\":\"POST\",\"body\":{\"kw\":\"{{key}}\"},\"headers\":{\"X-Token\":\"t\"}}"}""")
        val request = buildSearchRequestV36(headers, "k", 1)
        assertEquals("POST", request.method)
        assertEquals("""{"kw":"k"}""", request.body)
        assertEquals("t", request.headers["X-Token"])
        // Hop-by-hop / credential headers never cross origins.
        assertFalse(sourceRequestHeadersV95(mapOf("Host" to "evil", "Cookie" to "c", "Referer" to "r"), sameOrigin = false).containsKey("Cookie"))
        assertTrue(sourceRequestHeadersV95(mapOf("Referer" to "r"), sameOrigin = false).containsKey("Referer"))
    }

    @Test fun javaAjaxGoesThroughTheSourceClientAndPutGetPersist() {
        val src = source("""{"bookSourceName":"脚本站","bookSourceUrl":"https://www.example.com","searchUrl":"/s?q={{key}}",
            "ruleSearch":{"bookList":"class.item","name":"@put:{t:'tag.h3@text'}tag.a@text","bookUrl":"tag.a@href",
            "intro":"@js:java.ajax('/intro/'+java.get('t'))"}}""")
        val requested = ArrayList<String>()
        val scope = SourceRuleScopeV95(src, "x", fetchText = { _, request ->
            requested += request.url
            SourceTextResponseV95(request.url, "简介：" + request.url.substringAfterLast('/'))
        })
        val books = withSourceRuleScopeV95(scope) { searchSourceV36(src, "x") { _, request -> Jsoup.parse(listHtml, request.url) } }
        assertTrue(books[0].intro, books[0].intro.startsWith("简介："))
        assertTrue(requested.toString(), requested.all { it.startsWith("https://www.example.com/intro/") })
    }

    @Test fun sandboxBlocksJavaClassesAndStopsRunawayScripts() {
        val doc = Jsoup.parse("<p>x</p>", "https://www.example.com/")
        // No Java packages, no reflection: the script errors and the value reads as empty.
        assertEquals("", ruleStringV36(doc, "@js:java.lang.System.getProperty('user.home')"))
        assertEquals("", ruleStringV36(doc, "@js:Packages.java.io.File('/').list()"))
        assertEquals("undefined", ruleStringV36(doc, "@js:typeof Packages"))
        val started = System.nanoTime()
        val budget = SourceJsEngineV95.TIMEOUT_MS
        SourceJsEngineV95.TIMEOUT_MS = 1_000
        val failure = try { runCatching { ruleStringV36(doc, "@js:while(true){}") }.exceptionOrNull() } finally { SourceJsEngineV95.TIMEOUT_MS = budget }
        assertNotNull(failure)
        assertTrue(failure!!.message, failure.message.orEmpty().contains("超时"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 10_000)
        // A try/catch inside the script cannot swallow the timeout.
        SourceJsEngineV95.TIMEOUT_MS = 1_000
        try { assertTrue(runCatching { ruleStringV36(doc, "@js:try{while(true){}}catch(e){'caught'}") }.isFailure) }
        finally { SourceJsEngineV95.TIMEOUT_MS = budget }
        // Import refuses scripts that need Java classes or a WebView, and names why.
        assertEquals(null, sourceRuleUnsupportedV94("@js:java.ajax(baseUrl)"))
        assertTrue(sourceRuleUnsupportedV94("@js:org.jsoup.Jsoup.parse(result)").orEmpty().contains("Java"))
        assertTrue(sourceRuleUnsupportedV94("@js:java.webView('', url, '')").orEmpty().contains("网页视图"))
    }

    @Test fun cryptoHelpersMatchLegado() {
        val doc = Jsoup.parse("", "https://www.example.com/")
        // AES-128-CBC of "琅嬛" with key/iv "0123456789abcdef"
        val encrypted = ruleStringV36(doc, "@js:java.aesEncodeToBase64String('琅嬛','0123456789abcdef','AES/CBC/PKCS5Padding','0123456789abcdef')")
        assertTrue(encrypted.isNotBlank())
        assertEquals("琅嬛", ruleStringV36(doc, "@js:java.aesBase64DecodeToString('$encrypted','0123456789abcdef','AES/CBC/PKCS5Padding','0123456789abcdef')"))
        assertEquals("琅嬛", ruleStringV36(doc, "@js:java.createSymmetricCrypto('AES/CBC/PKCS5Padding','0123456789abcdef','0123456789abcdef').decryptStr('$encrypted')"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ruleStringV36(doc, "@js:java.md5Encode('abc')"))
        assertEquals("YWJj", ruleStringV36(doc, "@js:java.base64Encode('abc')"))
    }

    @Test fun scriptedDiscoveryBuildsCategoriesOffline() {
        val src = source("""{"bookSourceName":"发现站","bookSourceUrl":"https://www.example.com","searchUrl":"/s?q={{key}}",
            "ruleSearch":{"bookList":"li","name":"a@text","bookUrl":"a@href"},
            "exploreUrl":"@js:var o=[];['玄幻','都市'].forEach(function(n,i){o.push({title:n,url:'/sort/'+(i+1)+'/{{(page-1)*20}}.html'})});JSON.stringify(o)",
            "ruleExplore":{"bookList":"li","name":"a@text","bookUrl":"a@href"}}""")
        val sections = sourceDiscoveriesV41(src)
        assertEquals(listOf("玄幻", "都市"), sections.map { it.label })
        assertEquals("https://www.example.com/sort/1/0.html", sections[0].url)
        assertTrue(discoveryTemplatePagedV95(sections[0].template))
        val second = buildSearchRequestV36(discoveryRuleSourceV41(src, sections[1]), "", 3)
        assertEquals("https://www.example.com/sort/2/40.html", second.url)
        // A category script that needs the network is reported, not run while composing.
        val online = src.copy(exploreUrl = "@js:java.ajax('/cats')")
        val catalog = sourceDiscoveryCatalogV41(online)
        assertTrue(catalog.sections.isEmpty())
        assertTrue(catalog.issues.single(), catalog.issues.single().contains("联网"))
    }

    // ---- Import counts ---------------------------------------------------------------------------

    @Test fun realLegadoListsNowImportScriptedAndXpathSources() {
        val report = StringBuilder()
        listOf("legado/xiu2-shuyuan.json", "legado/kooofu-biquge.json", "legado/yolo52-shuyuan.json").forEach { name ->
            val result = parseBookSourcesV36(fixture(name))
            report.append("$name: imported ${result.sources.size}, skipped ${result.skipped.size}\n")
            result.skipped.forEach { report.append("  skip ").append(it).append('\n') }
            // Kept rules: imported sources no longer lose XPath / JS / JSON rules.
            result.warnings.forEach { warning ->
                assertFalse(warning, warning.contains("（XPath）") || warning.contains("（JavaScript）") || warning.contains("（JSON 接口）"))
            }
        }
        println(report)
        val xiu2 = parseBookSourcesV36(fixture("legado/xiu2-shuyuan.json"))
        assertTrue(report.toString(), xiu2.sources.size >= 18)
        assertTrue(report.toString(), parseBookSourcesV36(fixture("legado/yolo52-shuyuan.json")).sources.size >= 14)
        assertTrue(report.toString(), parseBookSourcesV36(fixture("legado/kooofu-biquge.json")).sources.size >= 20)
        // Whatever is still refused says exactly why.
        (xiu2.skipped).forEach { assertTrue(it, it.contains("（")) }
    }

    // ---- Covers ----------------------------------------------------------------------------------

    @Test fun coverRequestCarriesSourceRefererAndLegadoHeaderOptions() {
        val src = BookSourceV36(id = "s", name = "站", baseUrl = "https://www.example.com", headers = mapOf("User-Agent" to "UA-1", "Cookie" to "secret"))
        val plain = coverRequestV95(src, "https://img.cdn.example.net/c.jpg")
        assertEquals("https://img.cdn.example.net/c.jpg", plain.url)
        assertEquals("https://www.example.com/", plain.headers["Referer"])
        assertEquals("UA-1", plain.headers["User-Agent"])
        assertFalse(plain.headers.containsKey("Cookie"))
        val withOptions = coverRequestV95(src, "https://img.cdn.example.net/c.jpg,{\"headers\":{\"Referer\":\"https://m.example.com/\"}}")
        assertEquals("https://img.cdn.example.net/c.jpg", withOptions.url)
        assertEquals("https://m.example.com/", withOptions.headers["Referer"])
        val relative = coverRequestV95(src, "/covers/1.jpg")
        assertEquals("https://www.example.com/covers/1.jpg", relative.url)
    }

    @Test fun coverDownloadValidatesImagesAndPersistsTheShelfFile() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 'I'.code.toByte())
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 16, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1)
        assertTrue(looksLikeImageV95(png) && looksLikeImageV95(webp) && looksLikeImageV95(jpeg))
        assertFalse(looksLikeImageV95("<html>403 Forbidden</html>".toByteArray()))
        val src = BookSourceV36(id = "s", name = "站", baseUrl = "https://www.example.com")
        val seen = ArrayList<SourceRequestV36>()
        val bytes = downloadOnlineCoverV95("https://img.example.net/a.webp", src) { _, request, _ -> seen += request; webp }
        assertEquals("https://www.example.com/", seen.single().headers["Referer"])
        assertEquals("webp", imageExtensionV95(bytes))
        // An HTML error page is not saved as a cover.
        assertTrue(runCatching { downloadOnlineCoverV95("https://img.example.net/a.jpg", src) { _, _, _ -> "<html/>".toByteArray() } }.isFailure)
        // A host that rejects the Referer gets one retry without it.
        var attempts = 0
        downloadOnlineCoverV95("https://img.example.net/a.jpg", src) { _, request, _ ->
            attempts++
            if (request.headers.containsKey("Referer")) throw java.io.IOException("403") else jpeg
        }
        assertEquals(2, attempts)
        // data: URIs decode locally.
        val inline = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png)
        assertTrue(downloadOnlineCoverV95(inline, src) { _, _, _ -> error("no network") }.contentEquals(png))
        val dir = Files.createTempDirectory("covers").toFile()
        val path = persistOnlineCoverFileV95(dir, "novel-1", jpeg)
        assertEquals(File(dir, "novel-1-online.jpg").absolutePath, path)
        assertTrue(File(path).readBytes().contentEquals(jpeg))
        assertTrue(onlineCoverMissingV95(""))
        assertFalse(onlineCoverMissingV95(path))
        dir.deleteRecursively()
    }

    @Test fun searchCoverRuleUsingXpathOrScriptSurvivesImport() {
        val src = source("""{"bookSourceName":"站","bookSourceUrl":"https://www.example.com","searchUrl":"/s?q={{key}}",
            "ruleSearch":{"bookList":"//div[@class='item']","name":"//h3/a/text()","bookUrl":"//h3/a/@href",
            "coverUrl":"//img/@data-original@js:result.replace('.jpg','_big.jpg')"},
            "ruleBookInfo":{"coverUrl":"@css:.cover img@src"}}""")
        assertTrue(src.searchCover.isNotBlank())
        val books = searchSourceV36(src, "x") { _, request -> Jsoup.parse(listHtml, request.url) }
        assertEquals("https://www.example.com/cover/1_big.jpg", books[0].cover)
        // Detail page cover wins when present; search cover is the fallback.
        val detail = bookDetailFromPageV95(src, books[0], Jsoup.parse("<div class='cover'><img src='/d/1.png'></div>", "https://www.example.com/book/1"))
        assertEquals("https://www.example.com/d/1.png", detail.cover)
        val fallback = bookDetailFromPageV95(src, books[0], Jsoup.parse("<p>no cover</p>", "https://www.example.com/book/1"))
        assertEquals(books[0].cover, fallback.cover)
    }
}

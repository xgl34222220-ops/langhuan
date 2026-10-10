package com.xiguli.langhuan.ui

import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.domain.GeneratedChapter
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test

class SourceDnsPolicyV54Test {
    private fun ips(vararg values: String) = values.map { InetAddress.getByName(it) }
    private fun delegate(block: () -> List<InetAddress>): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = block()
    }
    private fun resolver(vararg values: String): Dns = delegate { ips(*values) }
    private fun error(dns: Dns): SourceDnsBlockedV54 = assertThrows(SourceDnsBlockedV54::class.java) {
        checkedSourceDnsV54(dns).lookup("books.example")
    }

    @Test fun proxyFakeIpAnswersAreConnectedAsResolved() {
        for (value in listOf("198.18.0.0","198.18.0.1","198.19.255.254","198.19.255.255","::ffff:198.18.0.2")) {
            val address=InetAddress.getByName(value)
            assertTrue(value,sourceBenchmarkAddressV54(address))
            assertTrue(value,connectableSourceAddressV54(address))
            assertEquals(listOf(address),checkedSourceDnsV54(resolver(value)).lookup("shuyuan.nyasama.net"))
        }
        assertEquals(ips("198.18.0.1","1.1.1.1"),checkedSourceDnsV54(resolver("198.18.0.1","1.1.1.1")).lookup("books.example"))
        assertEquals("http://198.18.0.1/",publicSourceUrlV36("http://198.18.0.1/").toString())
        assertFalse(sourceBenchmarkAddressV54(InetAddress.getByName("198.17.255.255")))
        assertFalse(sourceBenchmarkAddressV54(InetAddress.getByName("198.20.0.0")))
        assertFalse(connectableSourceAddressV54(InetAddress.getByName("198.51.100.1")))
    }

    @Test fun genuinePrivateMixedAndIpv6AddressesRemainBlocked() {
        val private=listOf("127.0.0.1","10.0.0.1","172.16.0.1","172.31.255.254","192.168.1.1","169.254.169.254",
            "100.64.0.1","0.0.0.0","fc00::1","fd12:3456::1","fe80::1","::1","2001:db8::1")
        for (value in private) {
            assertFalse(value,connectableSourceAddressV54(InetAddress.getByName(value)))
            assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(resolver(value)).reason)
            assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(resolver("1.1.1.1",value)).reason)
        }
        assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(resolver("198.18.0.1","127.0.0.1")).reason)
        for (literal in listOf("http://127.0.0.1/","http://10.0.0.1/","http://192.168.1.1/","http://[::1]/","http://[fe80::1]/")) {
            assertThrows(IllegalArgumentException::class.java) { publicSourceUrlV36(literal) }
        }
    }

    @Test fun emptyAnswerHasItsOwnDiagnosisAndResolverFailuresAreNotReplaced() {
        assertEquals(SourceDnsFailureV54.EMPTY,error(resolver()).reason)
        val original=UnknownHostException("synthetic lookup failure")
        val dns=checkedSourceDnsV54(delegate { throw original })
        assertSame(original,runCatching { dns.lookup("books.example") }.exceptionOrNull())
    }

    @Test fun publicAnswerIsPinnedWithoutSecondLookupOrMutableDelegateAlias() {
        var calls=0
        val answer=ips("1.1.1.1","2606:4700:4700::1111").toMutableList()
        val dns=checkedSourceDnsV54(delegate { calls++; answer })
        val checked=dns.lookup("books.example")
        assertEquals(1,calls)
        assertEquals(answer,checked)
        answer.clear();answer+=InetAddress.getByName("127.0.0.1")
        assertEquals(ips("1.1.1.1","2606:4700:4700::1111"),checked)
        assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(dns).reason)
    }

    @Test fun noResolverRetryOrSuccessOccursWhenCancelled() {
        var calls=0
        val cancel=CancellationException("fixture cancellation")
        val dns=checkedSourceDnsV54(delegate { calls++;throw cancel })
        assertSame(cancel,runCatching { dns.lookup("books.example") }.exceptionOrNull())
        assertEquals(1,calls)
    }

    @Test fun blockedDnsStopsRemainingAiRequestsWithoutNetworkOrModelRetries() {
        var calls=0
        val failure=SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC)
        val session=SourceRequestSessionV46 { _, _ -> calls++;throw failure }
        val source=BookSourceV36("fixture","fixture","https://books.example")
        val first=runCatching { session.document(source,SourceRequestV36("https://books.example/")) }
        val second=runCatching { session.document(source,SourceRequestV36("https://books.example/rank")) }
        assertSame(failure,first.exceptionOrNull());assertSame(failure,second.exceptionOrNull())
        assertEquals(1,calls)
    }
    @Test fun homepageDnsFailureEndsTheVisibleStepAndNeverCallsAi() = runBlocking {
        var calls=0
        var modelCalls=0
        var steps=emptyList<AiSourceStepV37>()
        val gateway=object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter { modelCalls++;error("unexpected AI call") }
            override suspend fun generateText(prompt: PromptBundle): String { modelCalls++;error("unexpected AI call") }
        }
        val result=runCatching {
            BookSourceAiBuilderV37(gateway,{ steps=it }) { _, _ ->
                calls++;throw SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC)
            }.build("https://books.example/", "示例小说")
        }
        assertTrue(result.isFailure)
        assertEquals(1,calls);assertEquals(0,modelCalls)
        assertEquals(1,steps.size)
        assertEquals("读取网站首页",steps.single().label)
        assertTrue(steps.single().completed)
        assertEquals(false,steps.single().ok)
        assertTrue(steps.single().detail.contains("非公网地址"))
        assertEquals(steps.single().detail,result.exceptionOrNull()!!.message)
    }

    @Test fun diagnosticsNameTheBlockedHostAndOnlyTheObservedRedirects() {
        val failure=error(resolver("10.0.0.2"))
        assertEquals("books.example",failure.blockedHost)
        assertTrue(failure.message!!.contains("books.example"))
        assertEquals(listOf("books.example"),failure.routeHosts)
        val redirected=sourceDnsWithRouteV55(failure,listOf("old.example","books.example")) as SourceDnsBlockedV54
        assertEquals(listOf("old.example","books.example"),redirected.routeHosts)
        assertSame(failure,redirected.cause)
        assertEquals(SourceDnsFailureV54.NON_PUBLIC,redirected.reason)
    }

    @Test fun diagnosticRoutesCannotEchoCredentialsPathsOrQueries() {
        val failure=SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC,
            "https://name:secret@books.example/path?token=private",
            listOf("old.example","Cookie=secret","https://books.example/?token=private","books.example"))
        assertNull(failure.blockedHost)
        assertEquals(listOf("old.example","books.example"),failure.routeHosts)
        assertFalse(failure.message!!.contains("secret"))
        assertFalse(failure.message!!.contains("private"))
    }

    @Test fun redirectDecorationNeverConvertsCancellationOrOtherFailures() {
        val dns=SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC,"books.example")
        val cancel=CancellationException("cancel").apply { initCause(dns) }
        assertSame(cancel,sourceDnsWithRouteV55(cancel,listOf("old.example","books.example")))
        val ordinary=java.io.IOException("ordinary")
        assertSame(ordinary,sourceDnsWithRouteV55(ordinary,listOf("old.example")))
    }

    @Test fun failedRedirectShowsBothHostsWithoutPretendingTheHomepageLoaded() = runBlocking {
        var steps=emptyList<AiSourceStepV37>()
        val gateway=object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("No AI before homepage")
            override suspend fun generateText(prompt: PromptBundle): String = error("No AI before homepage")
        }
        val failure=SourceDnsBlockedV54(SourceDnsFailureV54.NON_PUBLIC,"new.example",listOf("old.example","new.example"))
        val result=runCatching {
            BookSourceAiBuilderV37(gateway,{steps=it}) { _, _ -> throw failure }.build("https://old.example/","原创小说")
        }
        assertTrue(result.isFailure)
        assertEquals(1,steps.size)
        assertTrue(steps.single().completed)
        assertEquals(false,steps.single().ok)
        assertEquals(listOf("已观察到的域名跳转：old.example → new.example"),steps.single().details)
        assertTrue(steps.single().detail.contains("new.example"))
    }

}

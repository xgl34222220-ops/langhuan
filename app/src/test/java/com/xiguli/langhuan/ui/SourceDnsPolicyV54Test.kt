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

    @Test fun benchmarkFakeIpPossibilityIsExplainedButNeverConnected() {
        for (value in listOf("198.18.0.0","198.18.0.1","198.19.255.255","::ffff:198.18.0.2")) {
            val failure=error(resolver(value))
            assertEquals(SourceDnsFailureV54.BENCHMARK_RANGE,failure.reason)
            assertTrue(failure.message!!.contains("可能"))
            assertTrue(failure.message!!.contains("尚未连接"))
            assertFalse(publicSourceAddressV36(InetAddress.getByName(value)))
        }
        assertFalse(sourceBenchmarkAddressV54(InetAddress.getByName("198.17.255.255")))
        assertFalse(sourceBenchmarkAddressV54(InetAddress.getByName("198.20.0.0")))
    }

    @Test fun genuinePrivateMixedAndIpv6AddressesRemainBlocked() {
        for (value in listOf("127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","100.64.0.1",
            "0.0.0.0","fc00::1","fe80::1","::1","2001:db8::1")) {
            assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(resolver("1.1.1.1",value)).reason)
        }
        assertEquals(SourceDnsFailureV54.NON_PUBLIC,error(resolver("198.18.0.1","127.0.0.1")).reason)
        assertEquals(SourceDnsFailureV54.BENCHMARK_RANGE,error(resolver("1.1.1.1","198.18.0.1")).reason)
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
        val failure=SourceDnsBlockedV54(SourceDnsFailureV54.BENCHMARK_RANGE)
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
                calls++;throw SourceDnsBlockedV54(SourceDnsFailureV54.BENCHMARK_RANGE)
            }.build("https://books.example/", "示例小说")
        }
        assertTrue(result.isFailure)
        assertEquals(1,calls);assertEquals(0,modelCalls)
        assertEquals(1,steps.size)
        assertEquals("读取网站首页",steps.single().label)
        assertTrue(steps.single().completed)
        assertEquals(false,steps.single().ok)
        assertTrue(steps.single().detail.contains("Fake-IP"))
        assertEquals(steps.single().detail,result.exceptionOrNull()!!.message)
    }

}

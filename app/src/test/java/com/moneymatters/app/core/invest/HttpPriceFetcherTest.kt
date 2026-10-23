package com.moneymatters.app.core.invest

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpPriceFetcherTest {

    private lateinit var server: HttpServer
    private val hits = AtomicInteger()
    private var lastUserAgent: String? = null
    private val pauses = mutableListOf<Long>()
    private var statusFor: (path: String, hit: Int) -> Int = { _, _ -> 200 }

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val hit = hits.incrementAndGet()
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            val path = exchange.requestURI.path
            if (path == "/moved") {
                exchange.responseHeaders.add("Location", "$base/file")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val status = statusFor(path, hit)
                val body = if (status == 200) "hello".toByteArray() else ByteArray(0)
                exchange.sendResponseHeaders(status, if (status == 200) body.size.toLong() else -1)
                if (status == 200) exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun fetcher() = HttpPriceFetcher(retries = 2, backoffMillis = 100, minGapMillis = 0, pause = { pauses += it })

    @Test fun `a 200 returns the bytes, sent with a browser-like user agent`() = runBlocking {
        val result = fetcher().fetch("$base/file") as FetchResult.Ok
        assertEquals("hello", String(result.bytes))
        assertTrue(lastUserAgent!!.startsWith("Mozilla/5.0"))
    }

    @Test fun `a redirect is followed`() = runBlocking {
        assertEquals("hello", String((fetcher().fetch("$base/moved") as FetchResult.Ok).bytes))
    }

    @Test fun `a 404 is final, with a plain reason, and is not retried`() = runBlocking {
        statusFor = { _, _ -> 404 }
        val failed = fetcher().fetch("$base/file") as FetchResult.Failed
        assertEquals(404, failed.httpStatus)
        assertTrue(failed.reason, failed.reason.startsWith("File not found (404)"))
        assertEquals(1, hits.get())
    }

    @Test fun `a 403 is final and says it was blocked`() = runBlocking {
        statusFor = { _, _ -> 403 }
        val failed = fetcher().fetch("$base/file") as FetchResult.Failed
        assertEquals(403, failed.httpStatus)
        assertTrue(failed.reason.startsWith("Blocked (403)"))
        assertEquals(1, hits.get())
    }

    @Test fun `a server error is retried with growing pauses, then reported`() = runBlocking {
        statusFor = { _, _ -> 503 }
        val failed = fetcher().fetch("$base/file") as FetchResult.Failed
        assertEquals(503, failed.httpStatus)
        assertEquals(3, hits.get()) // the first try and two retries
        assertEquals(listOf(100L, 200L), pauses)
    }

    @Test fun `a flaky server that recovers is a success`() = runBlocking {
        statusFor = { _, hit -> if (hit < 3) 500 else 200 }
        assertEquals("hello", String((fetcher().fetch("$base/file") as FetchResult.Ok).bytes))
        assertEquals(3, hits.get())
    }

    @Test fun `an unreachable host is reported as a connection problem after retrying`() = runBlocking {
        val port = server.address.port
        server.stop(0)
        val failed = fetcher().fetch("http://127.0.0.1:$port/file") as FetchResult.Failed
        assertEquals(null, failed.httpStatus)
        assertTrue(failed.reason, failed.reason.startsWith("Couldn't connect"))
        assertEquals(listOf(100L, 200L), pauses)
    }

    @Test fun `requests are spaced by the minimum gap`() = runBlocking {
        var now = 1_000L
        val spaced = HttpPriceFetcher(retries = 0, minGapMillis = 1_000, pause = { now += it; pauses += it }, clock = { now })
        spaced.fetch("$base/file")
        now += 300
        spaced.fetch("$base/file")
        assertEquals(listOf(700L), pauses) // 300 ms passed of the 1,000 ms gap
    }

    @Test fun `an empty file is a failure, not an empty success`() = runBlocking {
        server.removeContext("/")
        server.createContext("/") { it.sendResponseHeaders(200, -1); it.close() }
        val failed = fetcher().fetch("$base/file") as FetchResult.Failed
        assertTrue(failed.reason, failed.reason.contains("empty"))
    }
}

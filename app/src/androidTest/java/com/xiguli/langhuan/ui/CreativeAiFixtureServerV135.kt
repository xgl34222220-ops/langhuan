package com.xiguli.langhuan.ui

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Device-local OpenAI-compatible fixture. Never opens a non-loopback connection or needs a key. */
internal class CreativeAiFixtureServerV135(
    private val respond: (JSONObject) -> String,
) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<JSONObject>()
    val failures = CopyOnWriteArrayList<Throwable>()
    val completedResponses = AtomicInteger()
    val baseUrl: String = "http://127.0.0.1:${socket.localPort}/v1"

    init {
        executor.execute {
            while (!closed.get()) {
                val client = try { socket.accept() } catch (error: SocketException) {
                    if (!closed.get()) failures += error
                    break
                }
                clients += client
                try {
                    executor.execute { serve(client) }
                } catch (error: RejectedExecutionException) {
                    clients -= client
                    runCatching { client.close() }
                    if (!closed.get()) failures += error
                    break
                }
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = readLine(input)
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                require(colon > 0) { "Malformed synthetic request header" }
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            if (requestLine.startsWith("GET ") && requestLine.contains("/models")) {
                val models = JSONArray().put(JSONObject().put("id", "fixture-default"))
                write(client, "application/json", JSONObject().put("data", models).toString())
                return
            }
            require(requestLine.startsWith("POST ") && requestLine.contains("/chat/completions")) { "Unexpected fixture path: $requestLine" }
            val size = headers["content-length"]?.toIntOrNull() ?: 0
            require(size in 1..2_000_000) { "Synthetic request body must have a bounded Content-Length" }
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = input.read(bytes, offset, size - offset)
                check(count > 0) { "Incomplete synthetic request body" }
                offset += count
            }
            val request = JSONObject(String(bytes, Charsets.UTF_8))
            requests += request
            val content = respond(request)
            if (request.optBoolean("stream")) {
                val events = content.chunked(32).joinToString("") { chunk ->
                    val delta = JSONObject().put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject().put("content", chunk))))
                    "data: $delta\n\n"
                } + "data: [DONE]\n\n"
                write(client, "text/event-stream", events)
            } else {
                val message = JSONObject().put("role", "assistant").put("content", content)
                write(client, "application/json", JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message))).toString())
            }
            completedResponses.incrementAndGet()
        } catch (error: Throwable) {
            // Cancellation can close a client while a deliberately delayed response is released.
            if (!closed.get() && error !is SocketException) failures += error
        } finally {
            clients -= client
            runCatching { client.close() }
        }
    }

    private fun write(client: Socket, contentType: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        client.getOutputStream().apply {
            write("HTTP/1.1 200 OK\r\nContent-Type: $contentType; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            check(next >= 0) { "Unexpected end of fixture headers" }
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.write(next)
            require(bytes.size() <= 16_384) { "Fixture header is too long" }
        }
        return bytes.toString("US-ASCII")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        socket.close()
        clients.forEach { runCatching { it.close() } }
        executor.shutdownNow()
    }
}

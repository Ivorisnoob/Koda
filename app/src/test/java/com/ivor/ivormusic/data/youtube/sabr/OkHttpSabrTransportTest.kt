package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.session.OkHttpSabrTransport
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class OkHttpSabrTransportTest {
    @Test fun `posts protobuf and cancel aborts a stalled body read`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val requestHead = StringBuilder()
            val serverDone = CountDownLatch(1)
            val serverThread = Thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    // Read request headers, then the 3-byte body.
                    while (!requestHead.endsWith("\r\n\r\n")) requestHead.append(input.read().toChar())
                    repeat(3) { input.read() }
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.yt-ump\r\n" +
                            "Content-Length: 1000000\r\n\r\n").toByteArray())
                        write(ByteArray(4) { 1 })
                        flush()
                    }
                    serverDone.await(10, TimeUnit.SECONDS)
                }
            }
            serverThread.start()

            // Long read timeout: only cancel can end the stalled read inside this test.
            val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
            val call = OkHttpSabrTransport(client).newCall("http://127.0.0.1:${server.localPort}/videoplayback?rn=0",
                mapOf("Content-Type" to "application/x-protobuf", "User-Agent" to "ua-test"), byteArrayOf(1, 2, 3))
            val response = call.execute()
            assertEquals(200, response.status)
            assertEquals("application/vnd.yt-ump", response.contentType)
            assertTrue(requestHead.startsWith("POST /videoplayback?rn=0 "))
            assertTrue(requestHead.contains("User-Agent: ua-test"))

            val body = response.body
            repeat(4) { assertEquals(1, body.read()) }
            val started = System.nanoTime()
            Thread { Thread.sleep(100); call.cancel() }.start()
            assertThrows(IOException::class.java) { body.read() }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
            response.close()
            serverDone.countDown()
            serverThread.join(5_000)
        }
    }
}

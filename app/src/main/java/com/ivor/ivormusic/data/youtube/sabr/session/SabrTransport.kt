package com.ivor.ivormusic.data.youtube.sabr.session

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A streaming protobuf POST. Seam for tests; production uses [OkHttpSabrTransport]. */
internal interface SabrTransport {
    fun newCall(url: String, headers: Map<String, String>, body: ByteArray): SabrCall
}

internal interface SabrCall {
    /** Blocks until headers arrive. The body is read by the caller and closed with the response. */
    fun execute(): SabrHttpResponse

    /** Safe from any thread, before or during [execute] and body reads. Aborts the socket. */
    fun cancel()
}

internal class SabrHttpResponse(
    val status: Int,
    val contentType: String?,
    val body: InputStream,
    private val onClose: () -> Unit = { body.close() },
) : Closeable {
    override fun close() = onClose()
}

/**
 * [client] should be the caller's shared transport with a read timeout; SABR responses
 * stream media for many seconds, so a whole-call timeout does not fit.
 */
internal class OkHttpSabrTransport(private val client: OkHttpClient) : SabrTransport {
    override fun newCall(url: String, headers: Map<String, String>, body: ByteArray): SabrCall {
        val contentType = headers["Content-Type"] ?: "application/x-protobuf"
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.post(body.toRequestBody(contentType.toMediaType())).build()
        val call = client.newCall(request)
        return object : SabrCall {
            override fun execute(): SabrHttpResponse {
                val response: Response = call.execute()
                val responseBody = response.body ?: run {
                    response.close()
                    return SabrHttpResponse(response.code, null, ByteArrayInputStream(ByteArray(0)))
                }
                return SabrHttpResponse(response.code, response.header("Content-Type"), responseBody.byteStream()) {
                    response.close()
                }
            }

            override fun cancel() = call.cancel()
        }
    }
}

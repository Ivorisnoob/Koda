package com.ivor.ivormusic.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

internal class DesktopDownloaderImpl(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: ExtractorRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, if (dataToSend != null) {
                okhttp3.RequestBody.create(null, dataToSend)
            } else if (httpMethod == "POST" || httpMethod == "PUT") {
                okhttp3.RequestBody.create(null, ByteArray(0))
            } else null)

        headers.forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        if (!headers.containsKey("User-Agent")) {
            requestBuilder.addHeader("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("Rate limited", url)
            }
            val responseBodyString = response.body?.string() ?: ""
            val responseHeaders = mutableMapOf<String, MutableList<String>>()
            response.headers.forEach { (name, value) ->
                responseHeaders.getOrPut(name) { mutableListOf() }.add(value)
            }
            return Response(response.code, response.message, responseHeaders, responseBodyString, url)
        } catch (e: IOException) {
            throw e
        }
    }
}

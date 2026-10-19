package com.ivor.ivormusic.data.stream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Log tag for everything under `data/stream`. */
internal const val STREAM_TAG = "YouTubeStreams"

/**
 * One InnerTube client identity a `/player` call can be made as.
 *
 * A stream URL is bound to the client that resolved it: googlevideo tags the
 * URL with [name] in `c=` and can answer 403 to a fetch whose User-Agent
 * belongs to a different client family. So the identity is one value, and the
 * User-Agent for a fetch is read back from the URL ([userAgentForStreamUrl])
 * rather than chosen by whoever happens to be fetching.
 */
internal class PlayerClient(
    /** `context.client.clientName`, and the `c=` on every URL this client resolves. */
    val name: String,
    val version: String,
    /** `X-YouTube-Client-Name`. */
    val id: Int,
    val userAgent: String,
    /** Device fields added to `context.client` beside the name and version. */
    val fields: Map<String, Any>,
    /**
     * Whether the request carries a `contentPlaybackNonce` and a `t` query
     * value, as the Apple native clients do.
     */
    val sendsPlaybackNonce: Boolean = false,
)

internal object PlayerClients {
    /**
     * The primary client for music, video and Shorts: one call under Koda's
     * own visitorData, plain URLs on every format, and the whole file served
     * in bounded ranges.
     *
     * [verified September 2026, `.probe/visionos_reuse_probe.py`] One
     * WEB-minted visitorData reused across videos: OK with a plain URL on
     * every format (none ciphered, none SABR-only) for ordinary, 2-hour and
     * live videos; ranges served at 0/25/50/90% and the tail; a 141-minute
     * audio track downloaded whole in 10 MB ranges; live returned an HLS
     * master with every variant; HDR itags 330-337 present; caption URLs serve
     * WebVTT without a PO token.
     *
     * It is not unconditional. [verified October 2026,
     * `.probe/visionos_wall_probe.py`] About one fresh visitorData in ten is
     * refused past the opening of a stream; see [StreamProbe].
     */
    const val VISION_OS_USER_AGENT =
        "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS " +
            "25_6_0 like Mac OS X; GB)"
    val VISION_OS = PlayerClient(
        name = "VISIONOS",
        version = "1.02",
        id = 101,
        userAgent = VISION_OS_USER_AGENT,
        fields = mapOf(
            "clientScreen" to "WATCH",
            "platform" to "MOBILE",
            "deviceMake" to "Apple",
            "deviceModel" to "RealityDevice14,1",
            "osName" to "visionOS",
            "osVersion" to "25.6.0.23O471",
        ),
        sendsPlaybackNonce = true,
    )

    /**
     * Last resort only. It answers signed out, needs no player JS and returns
     * unciphered URLs, which made it the primary client until it stopped
     * being able to serve a whole file.
     *
     * [verified August 2026] googlevideo serves the first ~1.1 MiB of a
     * stream and answers 403 for every byte past it, on this client and IOS
     * alike. The verdict is keyed on the visitorData the `/player` call
     * carried, not on the video or the client: it is stable for a given token
     * and roughly half of freshly minted tokens are refused. Do not read a 403
     * here as a User-Agent or client problem. It can also return only format
     * 18, a muxed MP4 (since March 2026, yt-dlp issue #16150), and omits
     * "made for kids" videos.
     */
    const val ANDROID_VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; " +
            "eureka-user Build/SQ3A.220605.009.A1) gzip"
    val ANDROID_VR = PlayerClient(
        name = "ANDROID_VR",
        version = "1.65.10",
        id = 28,
        userAgent = ANDROID_VR_USER_AGENT,
        fields = mapOf(
            "androidSdkVersion" to 32,
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "osName" to "Android",
            "osVersion" to "12L",
        ),
    )

    /** Tried after [ANDROID_VR], for the rare videos that client cannot serve. */
    const val IOS_USER_AGENT =
        "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
    val IOS = PlayerClient(
        name = "IOS",
        version = "21.02.3",
        id = 5,
        userAgent = IOS_USER_AGENT,
        fields = mapOf(
            "deviceMake" to "Apple",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.1.0.22B83",
        ),
    )

    /**
     * NewPipe Extractor v0.26.5's Android client, which Koda never calls
     * itself but whose URLs reach playback through the NewPipe fallback.
     */
    const val NEWPIPE_ANDROID_USER_AGENT =
        "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"

    /**
     * Kept only for [userAgentForStreamUrl]: URLs resolved by a TV client may
     * still sit in a saved queue or URI cache.
     */
    const val TV_EMBED_USER_AGENT =
        "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.0 Safari/605.1.15"
}

/**
 * The User-Agent a fetch of [url] must send, chosen by the client named in the
 * URL's `c=`. [browserUserAgent] answers for the web clients and for a URL
 * that names none.
 */
internal fun userAgentForStreamUrl(url: String, browserUserAgent: String): String =
    userAgentForStreamClient(url.toHttpUrlOrNull()?.queryParameter("c"), browserUserAgent)

/** [userAgentForStreamUrl] for a caller that has already read `c=`. */
internal fun userAgentForStreamClient(client: String?, browserUserAgent: String): String =
    when (client?.uppercase()) {
        "IOS" -> PlayerClients.IOS_USER_AGENT
        "ANDROID_VR" -> PlayerClients.ANDROID_VR_USER_AGENT
        "ANDROID", "ANDROID_TESTSUITE", "ANDROID_MUSIC" -> PlayerClients.NEWPIPE_ANDROID_USER_AGENT
        "VISIONOS" -> PlayerClients.VISION_OS_USER_AGENT
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY_EMBEDDED", "TVHTML5" ->
            PlayerClients.TV_EMBED_USER_AGENT
        else -> browserUserAgent
    }

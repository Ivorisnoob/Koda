package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.data.VideoStreamResolutionCache
import com.ivor.ivormusic.util.KLog
import org.json.JSONObject

/**
 * A visionOS resolution: the playable answer if there was one, the token it
 * was resolved under, and whether the bot check is why there was none.
 */
internal class VisionOsOutcome(
    val answer: PlayerAnswer.Playable?,
    /** True when even a fresh identity was refused, so the fallbacks need not remint. */
    val botChecked: Boolean,
    /** The token [answer] was resolved under; blank when there is no answer. */
    val visitorData: String = "",
)

/**
 * `/player` calls made as Koda: the client order, and what happens to the
 * identity when one is refused.
 *
 * Both entry points follow one rule. A refusal of the current token is worth
 * exactly one replacement: a fresh token that is refused too means the
 * verdict is on the address, which [BotCheckVerdict] then holds so nothing
 * automatic repeats it. Minting again there cost a mint plus more refused
 * calls on every song the player moved on to.
 *
 * Shared by audio and video resolution, which read different things out of
 * the same answer.
 *
 * @param probe asks googlevideo whether a new token's URLs play to the end;
 * see [visionOsVetted].
 */
internal class PlayerSession(
    private val identity: VisitorIdentity,
    private val api: PlayerApi,
    private val probe: StreamProbe,
) {
    /**
     * [visionOs], with a token vetted before a URL resolved under it reaches
     * a player; see [StreamProbe] for what is being caught.
     *
     * The probe is spent once per token, not per video: a token that passes
     * is remembered. A refused one is replaced and the video resolved again,
     * for up to [PROBE_ROUNDS] tokens. Past that, or when the probe says
     * nothing either way, the answer is returned as it is and playback's own
     * 403 recovery remains the backstop.
     *
     * @param probeUrl the URL out of an answer's `streamingData` whose last
     * byte settles it, or null to take the answer unprobed. Any format will
     * do, since the verdict is on the token; one that states its length and
     * duration is needed for the probe to say anything ([lastByteOffset]).
     */
    suspend fun visionOsVetted(
        videoId: String,
        probeUrl: (streamingData: JSONObject) -> String?,
    ): VisionOsOutcome {
        for (round in 1..PROBE_ROUNDS) {
            val direct = visionOs(videoId)
            val answer = direct.answer ?: return direct
            val token = direct.visitorData
            if (token.isBlank() || identity.isVetted(token)) return direct
            val url = probeUrl(answer.streamingData) ?: return direct
            when (probe.servesLastByte(url)) {
                true -> {
                    identity.markVetted(token)
                    return direct
                }
                null -> return direct
                false -> {
                    KLog.w(
                        STREAM_TAG,
                        "Resolve[visionOS] googlevideo refuses this visitorData past the opening " +
                            "(round $round/$PROBE_ROUNDS) videoId=$videoId",
                    )
                    if (round == PROBE_ROUNDS) return direct
                    // Ladders other surfaces resolved under this token are as
                    // dead. Not clear(): a video resolution runs this inside
                    // the cache, and clear() would cancel it.
                    VideoStreamResolutionCache.dropResolved()
                    identity.replace(token) ?: return direct
                }
            }
        }
        error("unreachable: the last round always returns")
    }

    /**
     * The primary resolution: one visionOS call under the identity Koda
     * already holds.
     *
     * It replaced a NewPipe extraction for what it does not do. That was
     * eight requests and three brand-new anonymous visitor ids per video
     * [verified September 2026 on device through YouTubeRequestLedger], so
     * every song played showed YouTube three new visitors from one address -
     * the pattern its bot check is built to catch.
     */
    suspend fun visionOs(videoId: String): VisionOsOutcome {
        val token = currentOrMinted()
        when (val first = player(videoId, PlayerClients.VISION_OS, token)) {
            is PlayerAnswer.Playable -> return VisionOsOutcome(first, botChecked = false, token)
            is PlayerAnswer.IdentityRefused -> Unit
            else -> return VisionOsOutcome(null, botChecked = false)
        }
        if (BotCheckVerdict.isActive()) return VisionOsOutcome(null, botChecked = true)

        KLog.w(STREAM_TAG, "Player[VISIONOS] visitorData refused, replacing it videoId=$videoId")
        val fresh = identity.replace(token)
        if (fresh == null || fresh == token) return VisionOsOutcome(null, botChecked = false)
        return when (val retry = player(videoId, PlayerClients.VISION_OS, fresh)) {
            is PlayerAnswer.Playable -> VisionOsOutcome(retry, botChecked = false, fresh)
            is PlayerAnswer.IdentityRefused -> {
                BotCheckVerdict.note(videoId)
                VisionOsOutcome(null, botChecked = true)
            }
            else -> VisionOsOutcome(null, botChecked = false)
        }
    }

    /**
     * The last resort: ANDROID_VR, then IOS.
     *
     * It can still cover a failure specific to visionOS, but never the normal
     * path - see [PlayerClients.ANDROID_VR] for why its URLs cannot be relied
     * on for a whole file.
     *
     * @param botCheckedAlready a fresh identity was refused moments ago, by
     * visionOS or by NewPipe (which mints its own per client). The verdict is
     * then on the network and a replacement here would only add requests.
     * @param onResponse every response that reached status OK, playable or
     * not, for the caller to harvest captions and loudness from.
     */
    suspend fun nativeFallback(
        videoId: String,
        botCheckedAlready: Boolean = false,
        onResponse: (JSONObject) -> Unit = {},
    ): PlayerAnswer.Playable? {
        val token = currentOrMinted()
        if (token.isBlank()) {
            KLog.w(STREAM_TAG, "No visitorData available for videoId=$videoId, bot check will refuse")
        }
        val first = androidVrThenIos(videoId, token, onResponse)
        first.playable?.let {
            BotCheckVerdict.clearOnSuccess()
            return it
        }
        if (!first.identityRefused) return null

        if (botCheckedAlready || BotCheckVerdict.isActive()) {
            KLog.w(STREAM_TAG, "Bot check refused a fresh identity already, not replacing videoId=$videoId")
            BotCheckVerdict.note(videoId)
            return null
        }

        KLog.w(STREAM_TAG, "visitorData refused by the bot check, replacing and retrying videoId=$videoId")
        val fresh = identity.replace(token) ?: return null
        if (fresh == token) return null
        val retry = androidVrThenIos(videoId, fresh, onResponse)
        retry.playable?.let {
            BotCheckVerdict.clearOnSuccess()
            return it
        }
        if (retry.identityRefused) BotCheckVerdict.note(videoId)
        return null
    }

    /** One client's answer under the current identity, with no fallback or replacement. */
    suspend fun single(videoId: String, client: PlayerClient): PlayerAnswer =
        player(videoId, client, identity.current())

    /**
     * A blank token is not "no identity, carry on": the bot check refuses a
     * token-less call outright, so the mint happens before the request rather
     * than after its refusal.
     */
    private suspend fun currentOrMinted(): String =
        identity.current().ifBlank { identity.replace(refused = "").orEmpty() }

    /**
     * Every `/player` call made as Koda goes through here, so each playable
     * answer's URLs are traced back to the token that signed them; see
     * [VisitorIdentity.signedBySupersededToken].
     */
    private suspend fun player(videoId: String, client: PlayerClient, token: String): PlayerAnswer =
        api.player(videoId, client, token).also {
            if (it is PlayerAnswer.Playable) identity.noteSigned(it.streamingData, token)
        }

    private companion object {
        /** Fresh tokens one video may go through before playback's own recovery takes over. */
        const val PROBE_ROUNDS = 3
    }

    private class PairOutcome(val playable: PlayerAnswer.Playable?, val identityRefused: Boolean)

    private suspend fun androidVrThenIos(
        videoId: String,
        token: String,
        onResponse: (JSONObject) -> Unit,
    ): PairOutcome {
        val vr = player(videoId, PlayerClients.ANDROID_VR, token)
        vr.okRoot()?.let(onResponse)
        if (vr is PlayerAnswer.Playable) return PairOutcome(vr, identityRefused = false)

        val ios = player(videoId, PlayerClients.IOS, token)
        ios.okRoot()?.let(onResponse)
        return PairOutcome(
            playable = ios as? PlayerAnswer.Playable,
            identityRefused = vr is PlayerAnswer.IdentityRefused || ios is PlayerAnswer.IdentityRefused,
        )
    }
}

/** The response body of an answer that reached status OK, with or without streams. */
internal fun PlayerAnswer.okRoot(): JSONObject? = when (this) {
    is PlayerAnswer.Playable -> root
    is PlayerAnswer.IdentityRefused -> root
    else -> null
}

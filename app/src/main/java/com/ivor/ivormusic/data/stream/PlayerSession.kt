package com.ivor.ivormusic.data.stream

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
 */
internal class PlayerSession(
    private val identity: VisitorIdentity,
    private val api: PlayerApi,
) {
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
        when (val first = api.player(videoId, PlayerClients.VISION_OS, token)) {
            is PlayerAnswer.Playable -> return VisionOsOutcome(first, botChecked = false, token)
            is PlayerAnswer.IdentityRefused -> Unit
            else -> return VisionOsOutcome(null, botChecked = false)
        }
        if (BotCheckVerdict.isActive()) return VisionOsOutcome(null, botChecked = true)

        KLog.w(STREAM_TAG, "Player[VISIONOS] visitorData refused, replacing it videoId=$videoId")
        val fresh = identity.replace(token)
        if (fresh == null || fresh == token) return VisionOsOutcome(null, botChecked = false)
        return when (val retry = api.player(videoId, PlayerClients.VISION_OS, fresh)) {
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
        api.player(videoId, client, identity.current())

    /**
     * A blank token is not "no identity, carry on": the bot check refuses a
     * token-less call outright, so the mint happens before the request rather
     * than after its refusal.
     */
    private suspend fun currentOrMinted(): String =
        identity.current().ifBlank { identity.replace(refused = "").orEmpty() }

    private class PairOutcome(val playable: PlayerAnswer.Playable?, val identityRefused: Boolean)

    private suspend fun androidVrThenIos(
        videoId: String,
        token: String,
        onResponse: (JSONObject) -> Unit,
    ): PairOutcome {
        val vr = api.player(videoId, PlayerClients.ANDROID_VR, token)
        vr.okRoot()?.let(onResponse)
        if (vr is PlayerAnswer.Playable) return PairOutcome(vr, identityRefused = false)

        val ios = api.player(videoId, PlayerClients.IOS, token)
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

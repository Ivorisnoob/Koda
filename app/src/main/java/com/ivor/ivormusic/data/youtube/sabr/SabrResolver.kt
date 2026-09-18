/*
 * One-shot token-bound MWEB resolution: mint, fetch, parse, construct. Rotation
 * policy is deliberately not here - a stale or refused resolution throws and the
 * caller invalidates the minter and resolves again, so token bytes are never
 * swapped under a live session (see SabrAttestation).
 */
package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrAttestationException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.parseSabrResolution
import com.ivor.ivormusic.data.youtube.sabr.model.toDescriptor
import com.ivor.ivormusic.data.youtube.sabr.session.SabrAttestation
import com.ivor.ivormusic.data.youtube.sabr.session.SabrRequestEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SabrResolver(
    private val repository: YouTubeRepository,
    private val attestation: SabrAttestation,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /**
     * Resolves a verified playback snapshot. Safe from any thread; the mint and
     * the fetch both run on IO. Local Only refuses inside the mint, before any
     * network runs. Throws [SabrAttestationException] when the server wants
     * fresh attestation (bot-check verdict included) and [SabrProtocolException]
     * for transport or envelope failures.
     */
    suspend fun resolve(videoId: String): SabrDescriptor = withContext(Dispatchers.IO) {
        require(videoId.isNotBlank()) { "Missing SABR video id" }
        val token = attestation.minter.mint(videoId)
        val parts = token.requestParts(
            SabrRequestEncoder.MWEB_USER_AGENT, signatureTimestampPlaceholder())
        val cpn = repository.generateCpn()
        val response = repository.fetchPlayerResponse(
            videoId = videoId,
            clientName = SabrRequestEncoder.MWEB_CLIENT_NAME,
            clientVersion = token.clientVersion,
            clientNameId = SabrRequestEncoder.MWEB_CLIENT_ID,
            userAgent = SabrRequestEncoder.MWEB_USER_AGENT,
            visitorData = token.visitorData,
            extraClientFields = parts.extraClientFields,
            contentPlaybackNonce = cpn,
            playbackSignatureTimestamp = parts.signatureTimestamp,
            serviceIntegrityDimensions = parts.serviceIntegrityDimensions,
        )
        val root = response.root
        if (root == null) {
            if (response.visitorDataSuspect) {
                throw SabrAttestationException("MWEB resolving call hit the bot check")
            }
            throw SabrProtocolException("MWEB resolving call failed")
        }
        val at = nowMs()
        parseSabrResolution(root, videoId, cpn, token, at)
            .toDescriptor(videoId, cpn, token, attestation.currentIdentity(), at)
    }

    /**
     * [judgement] The player-JS signature timestamp until the signature decoder
     * lands. Epoch seconds, matching Koda's established WEB_REMIX placeholder -
     * the resolving call is about attestation, not ciphered URLs.
     */
    private fun signatureTimestampPlaceholder(): Int = (System.currentTimeMillis() / 1000).toInt()
}

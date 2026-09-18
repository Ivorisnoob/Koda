/*
 * Bridges the attested MWEB resolving call onto YouTubeRepository.fetchPlayerResponse.
 * Request shape adapted from PipePipeExtractor (GPL-3.0), commit
 * c0cd0d61863f430af86475aaac968fbef245f507 (createMwebPlayerRequest):
 * token-bound client version and visitor data, MWEB user agent, playback
 * context with the player-JS signature timestamp, and serviceIntegrityDimensions
 * carrying the base64url PO token. Copyright the PipePipeExtractor contributors.
 * See THIRD_PARTY_NOTICES.md.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.model

import org.json.JSONObject

/**
 * What the PO-token minter (stage 5b) returns for one video: the token with the
 * visitor data and client version it was minted under. The token string is the
 * base64url form that travels in JSON; raw bytes stay in [SabrDescriptor].
 */
internal data class SabrMintedToken(
    val visitorData: String,
    val clientVersion: String,
    val poTokenBase64Url: String,
) {
    init {
        require(visitorData.isNotBlank() && clientVersion.isNotBlank() &&
            poTokenBase64Url.isNotBlank()) { "Incomplete SABR token" }
    }

    /**
     * Splits this token into the exact fragments fetchPlayerResponse needs:
     * client extras (`userAgent`, `timeZone`), the integrity dimensions and the
     * caller-supplied player-JS signature timestamp. Pure so the shape stays
     * unit-testable without network.
     */
    fun requestParts(userAgent: String, signatureTimestamp: Int): SabrPlayerRequestParts {
        require(userAgent.isNotBlank() && signatureTimestamp >= 0) {
            "Incomplete SABR player request"
        }
        return SabrPlayerRequestParts(
            extraClientFields = JSONObject()
                .put("userAgent", userAgent)
                .put("timeZone", "UTC"),
            serviceIntegrityDimensions = JSONObject()
                .put("poToken", poTokenBase64Url),
            signatureTimestamp = signatureTimestamp,
        )
    }
}

/** Fragments of the token-bound MWEB /player body; assembled by fetchPlayerResponse. */
internal data class SabrPlayerRequestParts(
    val extraClientFields: JSONObject,
    val serviceIntegrityDimensions: JSONObject,
    val signatureTimestamp: Int,
)

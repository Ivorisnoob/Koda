/*
 * Protocol field mapping adapted from PipePipeExtractor's generated/SabrMediaHeader.java,
 * commit c0cd0d61863f430af86475aaac968fbef245f507 (GPL-3.0).
 * Copyright the upstream contributors. See THIRD_PARTY_NOTICES.md.
 */
package com.ivor.ivormusic.data.youtube.sabr.media

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.protocol.SabrProto

/** Header fields needed to route and validate a segment. Other metadata stays in control parts. */
internal class SabrMediaHeader private constructor(
    val headerId: Int,
    val itag: Int,
    val lastModified: Long?,
    val xtags: String?,
    val videoId: String?,
    val initialization: Boolean,
    val sequence: Int,
    val compression: Int,
    val wireLength: Long?,
) {
    override fun toString() = "SabrMediaHeader(id=$headerId, itag=$itag, init=$initialization, sequence=$sequence)"

    companion object {
        fun decode(bytes: ByteArray): SabrMediaHeader {
            val fields = SabrProto.readFields(bytes).associateBy { it.number }
            fun Map<Int, SabrProto.Field>.number(key: Int): Long? = get(key)?.let {
                if (it.wireType != SabrProto.WIRE_VARINT || it.varint < 0) {
                    throw SabrProtocolException("Invalid SABR header numeric field $key")
                }
                it.varint
            }
            fun Map<Int, SabrProto.Field>.data(key: Int): ByteArray? = get(key)?.let {
                if (it.wireType != SabrProto.WIRE_LENGTH_DELIMITED) {
                    throw SabrProtocolException("Invalid SABR header bytes field $key")
                }
                it.bytes
            }
            fun Map<Int, SabrProto.Field>.text(key: Int): String? = data(key)?.toString(Charsets.UTF_8)
            fun integer(value: Long?, maximum: Int, name: String): Int {
                if (value == null || value > maximum) throw SabrProtocolException("Invalid SABR $name")
                return value.toInt()
            }
            val nested = fields.data(13)?.let { SabrProto.readFields(it).associateBy { f -> f.number } }.orEmpty()
            val id = integer(fields.number(1), 255, "header id")
            val itag = integer(fields.number(3) ?: nested.number(1), Int.MAX_VALUE, "itag")
            if (itag == 0) throw SabrProtocolException("Invalid SABR itag")
            val initFlag = fields.number(8) ?: 0
            if (initFlag > 1) throw SabrProtocolException("Invalid SABR initialization flag")
            val init = initFlag == 1L
            val sequence = integer(fields.number(9) ?: if (init) 0 else null, Int.MAX_VALUE, "sequence")
            if (!init && sequence == 0) throw SabrProtocolException("Invalid SABR media sequence")
            return SabrMediaHeader(id, itag, fields.number(4) ?: nested.number(2),
                fields.text(5) ?: nested.text(3), fields.text(2), init, sequence,
                integer(fields.number(7) ?: 0, 2, "compression"), fields.number(14))
        }
    }
}

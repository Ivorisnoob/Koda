/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: isolated models, strict bounds and validated timelines.
 */
package com.ivor.ivormusic.data.youtube.sabr.media;

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;

import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SabrMp4SegmentIndexParser {
    private static final String SIDX_BOX = "sidx";

    private SabrMp4SegmentIndexParser() {
    }

    @NonNull
    public static SabrSegmentIndex parse(@NonNull final byte[] initData)
            throws SabrProtocolException {
        if (initData.length > 4 * 1024 * 1024) {
            throw new SabrProtocolException("MP4 initialization exceeds limit");
        }
        final int sidxOffset = findSidxBox(initData, 0, initData.length);
        return parseSidx(initData, sidxOffset, initData.length);
    }

    @NonNull
    private static SabrSegmentIndex parseSidx(@NonNull final byte[] initData,
                                              final int sidxOffset,
                                              final int rangeEnd)
            throws SabrProtocolException {
        final long shortSize = readUint32(initData, sidxOffset);
        final int headerSize = shortSize == 1 ? 16 : 8;
        final long boxSize = shortSize == 1 ? readUint64(initData, sidxOffset + 8)
                : shortSize == 0 ? rangeEnd - sidxOffset : shortSize;
        final int boxEnd = checkedBoxEnd(sidxOffset, boxSize, rangeEnd);
        int cursor = sidxOffset + headerSize;
        if (boxEnd - cursor < 24) throw new SabrProtocolException("Truncated MP4 SIDX header");
        final int version = initData[cursor] & 0xff;
        if (version == 1 && boxEnd - cursor < 32) {
            throw new SabrProtocolException("Truncated MP4 SIDX v1 header");
        }
        cursor += 4; // reference_ID

        cursor += 4;
        final long timescale = readUint32(initData, cursor);
        cursor += 4;
        if (timescale <= 0) {
            throw new SabrProtocolException("Invalid MP4 SIDX timescale");
        }

        final long earliestPresentationTime;
        if (version == 0) {
            earliestPresentationTime = readUint32(initData, cursor);
            cursor += 8; // earliest_presentation_time + first_offset
        } else if (version == 1) {
            earliestPresentationTime = readUint64(initData, cursor);
            cursor += 16; // earliest_presentation_time + first_offset
        } else {
            throw new SabrProtocolException("Unsupported MP4 SIDX version: " + version);
        }

        cursor += 2; // reserved
        final int referenceCount = readUint16(initData, cursor);
        cursor += 2;
        final List<SabrSegmentIndex.Entry> entries = new ArrayList<>(referenceCount);
        long unscaledStart = earliestPresentationTime;
        for (int i = 0; i < referenceCount; i++) {
            if (cursor + 12 > boxEnd) {
                throw new SabrProtocolException("Truncated MP4 SIDX references");
            }
            final long reference = readUint32(initData, cursor);
            cursor += 4;
            final boolean nestedSidx = (reference & 0x80000000L) != 0;
            if (nestedSidx) {
                throw new SabrProtocolException("Nested MP4 SIDX references are unsupported");
            }
            final long duration = readUint32(initData, cursor);
            cursor += 8; // subsegment_duration + SAP flags
            if (duration == 0 || unscaledStart > Long.MAX_VALUE - duration) {
                throw new SabrProtocolException("Invalid MP4 SIDX duration");
            }
            final long startMs = scaleToMs(unscaledStart, timescale);
            unscaledStart += duration;
            final long endMs = scaleToMs(unscaledStart, timescale);
            entries.add(new SabrSegmentIndex.Entry(i + 1, startMs, endMs - startMs));
        }
        return new SabrSegmentIndex(entries);
    }

    private static int findSidxBox(@NonNull final byte[] data,
                                   final int start,
                                   final int end) throws SabrProtocolException {
        int offset = start;
        while (end - offset >= 8) {
            final long shortSize = readUint32(data, offset);
            final long size = shortSize == 1 ? readUint64(data, offset + 8)
                    : shortSize == 0 ? end - offset : shortSize;
            if (shortSize == 1 && size < 16) throw new SabrProtocolException("Invalid MP4 box header");
            final int boxEnd = checkedBoxEnd(offset, size, end);
            if (SIDX_BOX.equals(new String(data, offset + 4, 4, StandardCharsets.US_ASCII))) {
                return offset;
            }
            offset = boxEnd;
        }
        throw new SabrProtocolException("MP4 SIDX box not found");
    }

    private static int checkedBoxEnd(final int offset,
                                     final long boxSize,
                                     final int rangeEnd) throws SabrProtocolException {
        if (boxSize < 8 || boxSize > Integer.MAX_VALUE || offset + boxSize > rangeEnd) {
            throw new SabrProtocolException("Invalid MP4 SIDX box size");
        }
        return offset + (int) boxSize;
    }

    private static int readUint16(@NonNull final byte[] data,
                                  final int offset) throws SabrProtocolException {
        checkAvailable(data, offset, 2);
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static long readUint32(@NonNull final byte[] data,
                                   final int offset) throws SabrProtocolException {
        checkAvailable(data, offset, 4);
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }

    private static long readUint64(@NonNull final byte[] data,
                                   final int offset) throws SabrProtocolException {
        final long high = readUint32(data, offset);
        final long low = readUint32(data, offset + 4);
        if ((high & 0x80000000L) != 0) throw new SabrProtocolException("MP4 time or size overflow");
        return (high << 32) | low;
    }

    private static void checkAvailable(@NonNull final byte[] data,
                                       final int offset,
                                       final int length) throws SabrProtocolException {
        if (offset < 0 || length > data.length - offset) {
            throw new SabrProtocolException("Unexpected EOF while reading MP4 SIDX");
        }
    }

    private static long scaleToMs(final long value, final long timescale) throws SabrProtocolException {
        return SabrTimeScale.round(value, 1000L, timescale);
    }
}

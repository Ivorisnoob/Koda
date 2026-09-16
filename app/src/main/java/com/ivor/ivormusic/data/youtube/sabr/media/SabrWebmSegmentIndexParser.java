/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: isolated models, strict bounds and validated timelines.
 */
package com.ivor.ivormusic.data.youtube.sabr.media;

import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat;
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

public final class SabrWebmSegmentIndexParser {
    private static final long SEGMENT_ID = 0x18538067L;
    private static final long INFO_ID = 0x1549a966L;
    private static final long TIMECODE_SCALE_ID = 0x2ad7b1L;
    private static final long CUES_ID = 0x1c53bb6bL;
    private static final long CUE_POINT_ID = 0xbbL;
    private static final long CUE_TIME_ID = 0xb3L;
    private static final long DEFAULT_TIMECODE_SCALE_NANOS = 1000000L;

    private SabrWebmSegmentIndexParser() {
    }

    @NonNull
    public static SabrSegmentIndex parse(@NonNull final byte[] initData,
                                         @NonNull final SabrFormat format)
            throws SabrProtocolException {
        if (initData.length > 4 * 1024 * 1024) {
            throw new SabrProtocolException("WebM initialization exceeds limit");
        }
        final long totalDurationMs = format.getDurationMs();
        final Element segment = findElement(initData, 0, initData.length, SEGMENT_ID);
        final long timecodeScaleNanos = readTimecodeScale(initData, segment);
        final Element cues = findElement(initData, segment.contentStart, segment.contentEnd, CUES_ID);
        final List<Long> cueTimes = readCueTimes(initData, cues, timecodeScaleNanos);
        if (cueTimes.isEmpty()) {
            throw new SabrProtocolException("WebM cues contain no cue times");
        }

        final List<SabrSegmentIndex.Entry> entries = new ArrayList<>(cueTimes.size());
        for (int i = 0; i < cueTimes.size(); i++) {
            final long startMs = cueTimes.get(i);
            final long endMs;
            if (i + 1 < cueTimes.size()) {
                endMs = cueTimes.get(i + 1);
            } else if (totalDurationMs > startMs) {
                endMs = totalDurationMs;
            } else {
                throw new SabrProtocolException("Missing or invalid WebM final duration");
            }
            if (endMs <= startMs) throw new SabrProtocolException("Unordered WebM cues");
            entries.add(new SabrSegmentIndex.Entry(i + 1, startMs, endMs - startMs));
        }
        return new SabrSegmentIndex(entries);
    }

    private static long readTimecodeScale(@NonNull final byte[] data,
                                          @NonNull final Element segment)
            throws SabrProtocolException {
        final Element info = findElement(data, segment.contentStart, segment.contentEnd, INFO_ID);
        int offset = info.contentStart;
        while (offset < info.contentEnd) {
            final Element element = readElement(data, offset, info.contentEnd);
            if (element.id == TIMECODE_SCALE_ID) {
                return readUnsignedInteger(data, element.contentStart,
                        element.contentEnd - element.contentStart);
            }
            offset = element.contentEnd;
        }
        return DEFAULT_TIMECODE_SCALE_NANOS;
    }

    @NonNull
    private static List<Long> readCueTimes(@NonNull final byte[] data,
                                           @NonNull final Element cues,
                                           final long timecodeScaleNanos)
            throws SabrProtocolException {
        final List<Long> cueTimes = new ArrayList<>();
        int offset = cues.contentStart;
        while (offset < cues.contentEnd) {
            final Element cuePoint = readElement(data, offset, cues.contentEnd);
            if (cuePoint.id == CUE_POINT_ID) {
                final long cueTime = readCueTime(data, cuePoint);
                if (cueTime >= 0) {
                    if (cueTimes.size() >= 65536) throw new SabrProtocolException("Too many WebM cues");
                    cueTimes.add(scaleWebmTimeToMs(cueTime, timecodeScaleNanos));
                }
            }
            offset = cuePoint.contentEnd;
        }
        return cueTimes;
    }

    private static long readCueTime(@NonNull final byte[] data,
                                    @NonNull final Element cuePoint)
            throws SabrProtocolException {
        int offset = cuePoint.contentStart;
        while (offset < cuePoint.contentEnd) {
            final Element element = readElement(data, offset, cuePoint.contentEnd);
            if (element.id == CUE_TIME_ID) {
                return readUnsignedInteger(data, element.contentStart,
                        element.contentEnd - element.contentStart);
            }
            offset = element.contentEnd;
        }
        throw new SabrProtocolException("WebM cue has no time");
    }

    @NonNull
    private static Element findElement(@NonNull final byte[] data,
                                       final int start,
                                       final int end,
                                       final long id) throws SabrProtocolException {
        int offset = start;
        while (offset < end) {
            final Element element = readElement(data, offset, end);
            if (element.id == id) {
                return element;
            }
            offset = element.contentEnd;
        }
        throw new SabrProtocolException("WebM element not found: " + Long.toHexString(id));
    }

    @NonNull
    private static Element readElement(@NonNull final byte[] data,
                                       final int offset,
                                       final int containerEnd) throws SabrProtocolException {
        final Varint id = readElementId(data, offset, containerEnd);
        final Varint size = readElementSize(data, offset + id.length, containerEnd);
        final int contentStart = offset + id.length + size.length;
        final int contentEnd;
        if (size.unknown || size.value > containerEnd - contentStart) {
            // A Segment master may describe media beyond the initialization buffer.
            // Leaf/cue truncation is malformed, not a smaller valid element.
            if (id.value != SEGMENT_ID) throw new SabrProtocolException("Truncated WebM element");
            contentEnd = containerEnd;
        } else {
            contentEnd = contentStart + (int) size.value;
        }
        return new Element(id.value, contentStart, contentEnd);
    }

    @NonNull
    private static Varint readElementId(@NonNull final byte[] data,
                                        final int offset,
                                        final int end) throws SabrProtocolException {
        final int length = readVintLength(data, offset, end);
        if (length > 4) throw new SabrProtocolException("Invalid WebM element ID width");
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xffL);
        }
        return new Varint(value, length, false);
    }

    @NonNull
    private static Varint readElementSize(@NonNull final byte[] data,
                                          final int offset,
                                          final int end) throws SabrProtocolException {
        final int length = readVintLength(data, offset, end);
        long value = data[offset] & (0xffL >> length);
        for (int i = 1; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xffL);
        }
        final long unknownValue = (1L << (7 * length)) - 1L;
        return new Varint(value, length, value == unknownValue);
    }

    private static int readVintLength(@NonNull final byte[] data,
                                      final int offset,
                                      final int end) throws SabrProtocolException {
        if (offset >= end) {
            throw new SabrProtocolException("Unexpected EOF while reading WebM vint");
        }
        final int first = data[offset] & 0xff;
        for (int length = 1; length <= 8; length++) {
            if ((first & (0x80 >> (length - 1))) != 0) {
                if (offset + length > end) {
                    throw new SabrProtocolException("Truncated WebM vint");
                }
                return length;
            }
        }
        throw new SabrProtocolException("Invalid WebM vint");
    }

    private static long readUnsignedInteger(@NonNull final byte[] data,
                                            final int offset,
                                            final int length) throws SabrProtocolException {
        if (length <= 0 || length > 8 || offset < 0 || length > data.length - offset) {
            throw new SabrProtocolException("Invalid WebM unsigned integer length");
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (data[offset + i] & 0xffL);
        }
        if (value < 0) throw new SabrProtocolException("WebM unsigned integer overflow");
        return value;
    }

    private static long scaleWebmTimeToMs(final long cueTime,
                                          final long timecodeScaleNanos) throws SabrProtocolException {
        return SabrTimeScale.round(cueTime, timecodeScaleNanos, 1000000L);
    }

    private static final class Element {
        private final long id;
        private final int contentStart;
        private final int contentEnd;

        private Element(final long id, final int contentStart, final int contentEnd) {
            this.id = id;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
        }
    }

    private static final class Varint {
        private final long value;
        private final int length;
        private final boolean unknown;

        private Varint(final long value, final int length, final boolean unknown) {
            this.value = value;
            this.length = length;
            this.unknown = unknown;
        }
    }
}

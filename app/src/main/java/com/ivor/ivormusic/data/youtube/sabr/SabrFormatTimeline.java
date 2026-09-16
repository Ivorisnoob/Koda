/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: isolated models, strict bounds and validated timelines.
 */
package com.ivor.ivormusic.data.youtube.sabr;

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;
import com.ivor.ivormusic.data.youtube.sabr.media.SabrMp4SegmentIndexParser;
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegmentIndex;
import com.ivor.ivormusic.data.youtube.sabr.media.SabrWebmSegmentIndexParser;

import androidx.annotation.NonNull;
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat;

/** Immutable segment timeline parsed from one format's initialization data. */
public final class SabrFormatTimeline {
    @NonNull private final SabrSegmentIndex index;

    private SabrFormatTimeline(@NonNull final SabrSegmentIndex index) {
        this.index = index;
    }

    @NonNull
    public static SabrFormatTimeline parse(
            @NonNull final SabrFormat format,
            @NonNull final byte[] initializationData) throws SabrProtocolException {
        final String mimeType = format.getMimeType();
        if (mimeType == null) {
            throw new SabrProtocolException("Missing SABR format MIME type: itag="
                    + format.getId().getItag());
        }
        final SabrSegmentIndex index;
        if (mimeType.contains("mp4")) {
            index = SabrMp4SegmentIndexParser.parse(initializationData);
        } else if (mimeType.contains("webm")) {
            index = SabrWebmSegmentIndexParser.parse(initializationData, format);
        } else {
            throw new SabrProtocolException("Unsupported SABR initialization format: " + mimeType);
        }
        if (index.size() == 0) {
            throw new SabrProtocolException("Empty SABR segment index: itag=" + format.getId().getItag());
        }
        return new SabrFormatTimeline(index);
    }

    public int getEndSequence() { return index.size(); }

    public long getStartMs(final int sequenceNumber) {
        final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber);
        return entry == null ? -1 : entry.getStartMs();
    }

    public long getEndMs(final int sequenceNumber) {
        final SabrSegmentIndex.Entry entry = index.getEntry(sequenceNumber);
        return entry == null ? -1 : entry.getEndMs();
    }

    public int getSequenceAt(final long timeMs) {
        if (timeMs <= 0) return 1;
        int low = 1;
        int high = index.size();
        while (low <= high) {
            final int middle = low + (high - low) / 2;
            if (index.getEntry(middle).getEndMs() > timeMs) high = middle - 1;
            else low = middle + 1;
        }
        if (low <= index.size()) return low;
        return index.size() == Integer.MAX_VALUE ? Integer.MAX_VALUE : index.size() + 1;
    }
}

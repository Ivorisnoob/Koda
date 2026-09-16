/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: isolated models, strict bounds and validated timelines.
 */
package com.ivor.ivormusic.data.youtube.sabr.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;

public final class SabrSegmentIndex {
    @NonNull
    private final List<Entry> entries;

    SabrSegmentIndex(@NonNull final List<Entry> entries) throws SabrProtocolException {
        if (entries.isEmpty() || entries.size() > 65536) {
            throw new SabrProtocolException("Invalid SABR segment count");
        }
        long previousEnd = -1;
        for (int i = 0; i < entries.size(); i++) {
            final Entry entry = entries.get(i);
            if (entry.sequenceNumber != i + 1 || entry.startMs < 0
                    || entry.durationMs <= 0 || entry.startMs > Long.MAX_VALUE - entry.durationMs
                    || (i > 0 && entry.startMs != previousEnd)) {
                throw new SabrProtocolException("Invalid SABR segment timeline");
            }
            previousEnd = entry.getEndMs();
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    @Nullable
    public Entry getEntry(final int sequenceNumber) {
        if (sequenceNumber <= 0 || sequenceNumber > entries.size()) {
            return null;
        }
        return entries.get(sequenceNumber - 1);
    }

    public int size() {
        return entries.size();
    }

    public static final class Entry {
        private final int sequenceNumber;
        private final long startMs;
        private final long durationMs;

        Entry(final int sequenceNumber,
              final long startMs,
              final long durationMs) {
            this.sequenceNumber = sequenceNumber;
            this.startMs = startMs;
            this.durationMs = durationMs;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }

        public long getStartMs() {
            return startMs;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public long getEndMs() {
            return startMs + durationMs;
        }
    }
}

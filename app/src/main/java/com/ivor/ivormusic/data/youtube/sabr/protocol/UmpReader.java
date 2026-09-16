/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: package isolation, bounded decoding and strict wire validation.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.protocol;

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;

import androidx.annotation.NonNull;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for YouTube's UMP envelope. UMP uses its own compact integer format, not protobuf varints.
 */
public final class UmpReader {
    public static final int MAX_BUFFERED_PART_BYTES = 1024 * 1024;
    public static final int MAX_STREAMED_PART_BYTES = 64 * 1024 * 1024;
    public static final long MAX_RESPONSE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_PARTS = 16384;
    private UmpReader() {
    }

    /** Receives one UMP part at a time (used by {@link #readStreaming}). */
    @FunctionalInterface
    public interface PartConsumer {
        void accept(int type, @NonNull byte[] payload) throws SabrProtocolException;
    }

    /** Receives one UMP part and returns false when the caller has enough data. */
    @FunctionalInterface
    public interface StoppablePartConsumer {
        boolean accept(int type, @NonNull byte[] payload) throws SabrProtocolException;
    }

    /** Receives one UMP part payload as a bounded stream. The consumer may stop at part boundary. */
    @FunctionalInterface
    public interface StoppablePayloadConsumer {
        boolean accept(int type, int size, @NonNull InputStream payload)
                throws SabrProtocolException, IOException;
    }

    /**
     * Stream the UMP envelope: read one part (type, size, payload) at a time from {@code in} and
     * hand it to {@code consumer}, so the whole response body is never held in memory at once. Peak
     * transient is a single part's payload instead of the entire body (50-150MB at 4K). The stream
     * is consumed but NOT closed (caller owns it).
     */
    public static void readStreaming(@NonNull final InputStream in,
                                     @NonNull final PartConsumer consumer)
            throws SabrProtocolException, IOException {
        readStreamingUntil(in, (type, payload) -> {
            consumer.accept(type, payload);
            return true;
        });
    }

    /**
     * Like {@link #readStreaming(InputStream, PartConsumer)}, but stops at a part boundary when
     * {@code consumer} returns false. The caller owns and closes the stream.
     */
    public static void readStreamingUntil(@NonNull final InputStream in,
                                          @NonNull final StoppablePartConsumer consumer)
            throws SabrProtocolException, IOException {
        readPayloadsUntil(in, (type, size, payload) -> consumer.accept(type,
                readExactly(payload, size)));
    }

    /**
     * Stream the UMP envelope while exposing each payload as a bounded stream. This lets callers
     * consume large MEDIA parts without allocating one byte[] for the whole part.
     */
    public static void readPayloadsUntil(@NonNull final InputStream in,
                                         @NonNull final StoppablePayloadConsumer consumer)
            throws SabrProtocolException, IOException {
        long totalBytes = 0;
        int parts = 0;
        while (true) {
            throwIfInterrupted();
            final int first = in.read();
            if (first < 0) {
                return; // clean EOF at a part boundary -> done
            }
            final int type = readUmpInt(in, first);
            final int size = readUmpInt(in, readByteOrThrow(in));
            if (type < 0 || size < 0) {
                throw new SabrProtocolException("Invalid UMP part header");
            }
            totalBytes += size;
            if (++parts > MAX_PARTS || size > MAX_STREAMED_PART_BYTES
                    || totalBytes > MAX_RESPONSE_BYTES) {
                throw new SabrProtocolException("UMP response exceeds limit");
            }
            final BoundedInputStream payload = new BoundedInputStream(in, size);
            final boolean keepGoing = consumer.accept(type, size, payload);
            payload.drain();
            if (!keepGoing) {
                return;
            }
        }
    }

    // UMP compact int, given the already-read first byte. Mirrors Cursor.readUmpInt.
    private static int readUmpInt(@NonNull final InputStream in, final int first)
            throws SabrProtocolException, IOException {
        if (first < 0) {
            throw new EOFException("Unexpected EOF in UMP integer");
        }
        if (first < 128) {
            return first;
        }
        if (first < 192) {
            return (first & 0x3f) + 64 * readByteOrThrow(in);
        }
        if (first < 224) {
            return (first & 0x1f) + 32 * (readByteOrThrow(in) + 256 * readByteOrThrow(in));
        }
        if (first < 240) {
            return (first & 0x0f) + 16 * (readByteOrThrow(in)
                    + 256 * (readByteOrThrow(in) + 256 * readByteOrThrow(in)));
        }
        return readByteOrThrow(in) + 256 * (readByteOrThrow(in)
                + 256 * (readByteOrThrow(in) + 256 * readByteOrThrow(in)));
    }

    private static int readByteOrThrow(@NonNull final InputStream in)
            throws SabrProtocolException, IOException {
        throwIfInterrupted();
        final int b = in.read();
        if (b < 0) {
            throw new EOFException("Unexpected EOF in UMP integer");
        }
        return b;
    }

    @NonNull
    private static byte[] readExactly(@NonNull final InputStream in, final int length)
            throws SabrProtocolException, IOException {
        if (length < 0 || length > MAX_BUFFERED_PART_BYTES) {
            throw new SabrProtocolException("Invalid UMP part length");
        }
        throwIfInterrupted();
        final byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            throwIfInterrupted();
            final int read = in.read(result, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected EOF while reading UMP part data");
            }
            offset += read;
        }
        return result;
    }

    private static void throwIfInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Interrupted while reading UMP stream");
        }
    }

    private static final class BoundedInputStream extends InputStream {
        @NonNull
        private final InputStream source;
        private int remaining;

        private BoundedInputStream(@NonNull final InputStream source, final int size) {
            this.source = source;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            throwIfInterrupted();
            if (remaining <= 0) {
                return -1;
            }
            final int value = source.read();
            if (value < 0) {
                throw new EOFException("Unexpected EOF while reading UMP part data");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(@NonNull final byte[] buffer, final int offset, final int length)
                throws IOException {
            throwIfInterrupted();
            if (offset < 0 || length < 0 || offset > buffer.length - length) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;
            if (remaining <= 0) return -1;
            final int read = source.read(buffer, offset, Math.min(length, remaining));
            if (read == 0) {
                buffer[offset] = (byte) read();
                return 1;
            }
            if (read < 0) {
                throw new EOFException("Unexpected EOF while reading UMP part data");
            }
            remaining -= read;
            return read;
        }

        private void drain() throws IOException {
            final byte[] buffer = new byte[8192];
            while (remaining > 0) {
                read(buffer, 0, Math.min(buffer.length, remaining));
            }
        }
    }

    @NonNull
    public static List<UmpPart> readAll(@NonNull final byte[] data) throws SabrProtocolException {
        if (data.length > MAX_BUFFERED_PART_BYTES) {
            throw new SabrProtocolException("Buffered UMP input exceeds limit");
        }
        final Cursor cursor = new Cursor(data);
        final List<UmpPart> parts = new ArrayList<>();
        while (!cursor.isDone()) {
            final int type = cursor.readUmpInt();
            final int size = cursor.readUmpInt();
            if (type < 0 || size < 0) {
                throw new SabrProtocolException("Invalid UMP part header");
            }
            if (parts.size() >= MAX_PARTS) {
                throw new SabrProtocolException("Too many UMP parts");
            }
            parts.add(new UmpPart(type, size, cursor.readBytes(size)));
        }
        return parts;
    }

    public static final class UmpPart {
        private final int type;
        private final int size;
        @NonNull private final byte[] data;

        public UmpPart(final int type, final int size, @NonNull final byte[] data) {
            this.type = type;
            this.size = size;
            this.data = data;
        }

        public int getType() { return type; }
        public int getSize() { return size; }
        @NonNull public byte[] getData() { return data.clone(); }
        @NonNull public byte[] getRawData() { return data; }
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        private Cursor(@NonNull final byte[] data) {
            this.data = data;
        }

        boolean isDone() {
            return offset >= data.length;
        }

        int readUmpInt() throws SabrProtocolException {
            final int first = readUnsignedByte();
            if (first < 128) {
                return first;
            }
            if (first < 192) {
                return (first & 0x3f) + 64 * readUnsignedByte();
            }
            if (first < 224) {
                return (first & 0x1f) + 32 * (readUnsignedByte()
                        + 256 * readUnsignedByte());
            }
            if (first < 240) {
                return (first & 0x0f) + 16 * (readUnsignedByte()
                        + 256 * (readUnsignedByte() + 256 * readUnsignedByte()));
            }
            return readUnsignedByte()
                    + 256 * (readUnsignedByte()
                    + 256 * (readUnsignedByte() + 256 * readUnsignedByte()));
        }

        @NonNull
        byte[] readBytes(final int length) throws SabrProtocolException {
            if (length < 0 || length > data.length - offset) {
                throw new SabrProtocolException("Unexpected EOF while reading UMP part data");
            }
            final byte[] result = new byte[length];
            System.arraycopy(data, offset, result, 0, length);
            offset += length;
            return result;
        }

        private int readUnsignedByte() throws SabrProtocolException {
            if (offset >= data.length) {
                throw new SabrProtocolException("Unexpected EOF in UMP integer");
            }
            return data[offset++] & 0xff;
        }
    }
}

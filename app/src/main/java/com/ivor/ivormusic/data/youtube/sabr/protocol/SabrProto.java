/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: package isolation, bounded decoding and strict wire validation.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.protocol;

import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId;
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal protobuf wire reader/writer used by the experimental YouTube SABR probe.
 */
public final class SabrProto {
    public static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private static final int MAX_FIELDS = 65536;
    private static final long MAX_FIELD_NUMBER = (1L << 29) - 1;
    public static final int WIRE_VARINT = 0;
    public static final int WIRE_FIXED64 = 1;
    public static final int WIRE_LENGTH_DELIMITED = 2;
    public static final int WIRE_FIXED32 = 5;

    private SabrProto() {
    }

    @NonNull
    public static List<Field> readFields(@NonNull final byte[] data) throws SabrProtocolException {
        final Cursor cursor = new Cursor(data);
        final List<Field> fields = new ArrayList<>();
        while (!cursor.isDone()) {
            final long tag = cursor.readVarint();
            if (tag <= 0 || (tag >>> 3) > MAX_FIELD_NUMBER) {
                throw new SabrProtocolException("Invalid protobuf tag");
            }
            if (fields.size() >= MAX_FIELDS) {
                throw new SabrProtocolException("Too many protobuf fields");
            }
            final int number = (int) (tag >>> 3);
            final int wireType = (int) (tag & 0x07);
            if (number <= 0) {
                throw new SabrProtocolException("Invalid protobuf field number: " + number);
            }

            switch (wireType) {
                case WIRE_VARINT:
                    fields.add(Field.varint(number, cursor.readVarint()));
                    break;
                case WIRE_FIXED64:
                    fields.add(Field.bytes(number, wireType, cursor.readBytes(8)));
                    break;
                case WIRE_LENGTH_DELIMITED:
                    fields.add(Field.bytes(number, wireType,
                            cursor.readBytes(cursor.readLength())));
                    break;
                case WIRE_FIXED32:
                    fields.add(Field.bytes(number, wireType, cursor.readBytes(4)));
                    break;
                default:
                    throw new SabrProtocolException("Unsupported protobuf wire type: " + wireType);
            }
        }
        return fields;
    }

    @NonNull
    public static byte[] formatId(@NonNull final SabrFormatId format) {
        final Writer writer = new Writer();
        writer.writeInt32(1, format.getItag());
        if (format.getLastModified() > 0) {
            writer.writeUInt64(2, format.getLastModified());
        }
        writer.writeStringIfNotEmpty(3, format.getXtags());
        return writer.toByteArray();
    }

    @NonNull
    public static String asString(@NonNull final byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    @NonNull
    public static List<Long> readPackedVarints(@NonNull final byte[] data)
            throws SabrProtocolException {
        final Cursor cursor = new Cursor(data);
        final List<Long> values = new ArrayList<>();
        while (!cursor.isDone()) {
            if (values.size() >= MAX_FIELDS) {
                throw new SabrProtocolException("Too many packed protobuf values");
            }
            values.add(cursor.readVarint());
        }
        return values;
    }

    public static int asFixed32LittleEndian(@NonNull final byte[] data)
            throws SabrProtocolException {
        if (data.length != 4) {
            throw new SabrProtocolException("Expected fixed32 length 4, got " + data.length);
        }
        return (data[0] & 0xff)
                | ((data[1] & 0xff) << 8)
                | ((data[2] & 0xff) << 16)
                | ((data[3] & 0xff) << 24);
    }

    @NonNull
    public static String summarizeFields(@NonNull final byte[] data) throws SabrProtocolException {
        return summarizeFields(data, new int[0]);
    }

    @NonNull
    public static String summarizeUnknownFields(@NonNull final byte[] data,
                                         final int... knownFieldNumbers)
            throws SabrProtocolException {
        return summarizeFields(data, knownFieldNumbers);
    }

    @NonNull
    private static String summarizeFields(@NonNull final byte[] data,
                                          @NonNull final int[] skippedFieldNumbers)
            throws SabrProtocolException {
        final Map<String, Integer> fields = new LinkedHashMap<>();
        for (final Field field : readFields(data)) {
            if (contains(skippedFieldNumbers, field.getNumber())) {
                continue;
            }
            final String key = summarizeField(field);
            final Integer count = fields.get(key);
            fields.put(key, count == null ? 1 : count + 1);
        }

        if (fields.isEmpty()) {
            return "none";
        }
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, Integer> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey());
            if (entry.getValue() > 1) {
                builder.append('x').append(entry.getValue());
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String summarizeField(@NonNull final Field field) throws SabrProtocolException {
        final StringBuilder builder = new StringBuilder();
        builder.append(field.getNumber()).append('=');
        if (field.getWireType() == WIRE_VARINT) {
            builder.append("varint");
        } else {
            builder.append("bytes(").append(field.getBytes().length).append(')');
        }
        return builder.toString();
    }

    private static boolean contains(@NonNull final int[] values, final int value) {
        for (final int current : values) {
            if (current == value) {
                return true;
            }
        }
        return false;
    }

    public static final class Field {
        private final int number;
        private final int wireType;
        private final long varint;
        @Nullable
        private final byte[] bytes;

        private Field(final int number,
                      final int wireType,
                      final long varint,
                      @Nullable final byte[] bytes) {
            this.number = number;
            this.wireType = wireType;
            this.varint = varint;
            this.bytes = bytes;
        }

        @NonNull
        static Field varint(final int number, final long value) {
            return new Field(number, WIRE_VARINT, value, null);
        }

        @NonNull
        static Field bytes(final int number, final int wireType, @NonNull final byte[] value) {
            return new Field(number, wireType, 0, value);
        }

        public int getNumber() {
            return number;
        }

        public int getWireType() {
            return wireType;
        }

        public long getVarint() {
            return varint;
        }

        @NonNull
        public byte[] getBytes() throws SabrProtocolException {
            if (bytes == null) {
                throw new SabrProtocolException("Field " + number + " is not length-delimited");
            }
            return bytes;
        }

        @NonNull
        public String getString() throws SabrProtocolException {
            return asString(getBytes());
        }
    }

    public static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        public Writer() { }

        public void writeInt32(final int fieldNumber, final int value) {
            writeUInt64(fieldNumber, value);
        }

        public void writeUInt64(final int fieldNumber, final long value) {
            writeTag(fieldNumber, WIRE_VARINT);
            writeVarint(value);
        }

        public void writeBool(final int fieldNumber, final boolean value) {
            writeUInt64(fieldNumber, value ? 1 : 0);
        }

        public void writeFloat(final int fieldNumber, final float value) {
            writeTag(fieldNumber, WIRE_FIXED32);
            writeFixed32(Float.floatToIntBits(value));
        }

        public void writeFixed64(final int fieldNumber, final long value) {
            writeTag(fieldNumber, WIRE_FIXED64);
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                output.write((int) (value >> shift) & 0xff);
            }
        }

        public void writeBytes(final int fieldNumber, @NonNull final byte[] bytes) {
            writeTag(fieldNumber, WIRE_LENGTH_DELIMITED);
            writeVarint(bytes.length);
            writeRaw(bytes);
        }

        public void writeStringIfNotEmpty(final int fieldNumber, @Nullable final String value) {
            if (value != null && !value.isEmpty()) {
                writeBytes(fieldNumber, value.getBytes(StandardCharsets.UTF_8));
            }
        }

        public void writeMessage(final int fieldNumber, @NonNull final byte[] bytes) {
            writeBytes(fieldNumber, bytes);
        }

        @NonNull
        public byte[] toByteArray() {
            return output.toByteArray();
        }

        public int size() {
            return output.size();
        }

        private void writeTag(final int fieldNumber, final int wireType) {
            if (fieldNumber <= 0 || fieldNumber > MAX_FIELD_NUMBER) {
                throw new IllegalArgumentException("Invalid protobuf field number");
            }
            writeVarint(((long) fieldNumber << 3) | wireType);
        }

        private void writeVarint(final long value) {
            long remaining = value;
            while ((remaining & ~0x7fL) != 0) {
                output.write((int) ((remaining & 0x7f) | 0x80));
                remaining >>>= 7;
            }
            output.write((int) remaining);
        }

        private void writeFixed32(final int value) {
            output.write(value & 0xff);
            output.write((value >> 8) & 0xff);
            output.write((value >> 16) & 0xff);
            output.write((value >> 24) & 0xff);
        }

        private void writeRaw(@NonNull final byte[] bytes) {
            try {
                output.write(bytes);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        private Cursor(@NonNull final byte[] data) throws SabrProtocolException {
            if (data.length > MAX_MESSAGE_BYTES) {
                throw new SabrProtocolException("Protobuf message exceeds limit");
            }
            this.data = data;
        }

        boolean isDone() {
            return offset >= data.length;
        }

        long readVarint() throws SabrProtocolException {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (offset >= data.length) {
                    throw new SabrProtocolException("Unexpected EOF in protobuf varint");
                }
                final int current = data[offset++] & 0xff;
                if (shift == 63 && (current & 0xfe) != 0) {
                    throw new SabrProtocolException("Protobuf varint overflow");
                }
                result |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new SabrProtocolException("Protobuf varint is too long");
        }

        int readLength() throws SabrProtocolException {
            final long length = readVarint();
            if (length < 0 || length > data.length - offset) {
                throw new SabrProtocolException("Invalid protobuf byte length");
            }
            return (int) length;
        }

        @NonNull
        byte[] readBytes(final int length) throws SabrProtocolException {
            if (length < 0 || length > data.length - offset) {
                throw new SabrProtocolException("Unexpected EOF while reading " + length + " bytes");
            }
            final byte[] result = new byte[length];
            System.arraycopy(data, offset, result, 0, length);
            offset += length;
            return result;
        }
    }
}

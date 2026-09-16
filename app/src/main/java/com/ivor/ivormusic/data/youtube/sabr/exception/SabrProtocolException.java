/*
 * Adapted from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507.
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: package isolation, bounded decoding and strict wire validation.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.exception;

import java.io.IOException;

public class SabrProtocolException extends IOException {
    public SabrProtocolException(final String message) {
        super(message);
    }

    public SabrProtocolException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

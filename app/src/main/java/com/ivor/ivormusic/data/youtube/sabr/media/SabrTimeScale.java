package com.ivor.ivormusic.data.youtube.sabr.media;

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException;
import java.math.BigInteger;

/** Initialization-only time conversion; reject wraparound rather than inventing a timeline. */
final class SabrTimeScale {
    private SabrTimeScale() { }

    static long round(final long value, final long multiplier, final long divisor)
            throws SabrProtocolException {
        if (value < 0 || multiplier <= 0 || divisor <= 0) {
            throw new SabrProtocolException("Invalid SABR time scale");
        }
        final BigInteger result = BigInteger.valueOf(value).multiply(BigInteger.valueOf(multiplier))
                .add(BigInteger.valueOf(divisor / 2)).divide(BigInteger.valueOf(divisor));
        if (result.bitLength() > 63) throw new SabrProtocolException("SABR timeline overflow");
        return result.longValue();
    }
}

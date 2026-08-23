package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Directional reading of a piece of evidence, a horizon or the cross-horizon conclusion. Distinct from
 * the V1 {@code MarketBias}: {@link #MIXED} means the inputs disagree (a real, interpreted state) and
 * {@link #UNKNOWN} means nothing could be interpreted (not eligible / not available) — the two must
 * never be collapsed into {@link #NEUTRAL}, which is an interpreted "no direction".
 */
public enum InterpretationDirection {
    BULLISH,
    BEARISH,
    NEUTRAL,
    MIXED,
    UNKNOWN;

    /** BULLISH or BEARISH — a reading a side could later be derived from. */
    public boolean isDirectional() {
        return this == BULLISH || this == BEARISH;
    }
}

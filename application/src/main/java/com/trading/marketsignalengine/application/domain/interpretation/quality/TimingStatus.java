package com.trading.marketsignalengine.application.domain.interpretation.quality;

/**
 * Freshness verdict of one feature snapshot at an explicit assessment instant. Precedence:
 * {@link #CLOCK_SKEW} (any negative age / latency — the source claims an instant in the engine's
 * future) over {@link #STALE} (age or latency beyond the policy threshold) over {@link #FRESH}.
 * {@link #UNKNOWN} is fail-closed vocabulary only: it is never produced for validated input (the
 * validator guarantees the source instants are present), but a consumer that meets it must treat it
 * like a hard gate.
 */
public enum TimingStatus {
    FRESH,
    STALE,
    CLOCK_SKEW,
    UNKNOWN;

    public boolean isFresh() {
        return this == FRESH;
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.volatility;

/**
 * Typed volatility regime of one horizon, classified against the horizon's
 * {@code VolatilityHorizonPolicy} bounds (inclusive upper boundaries):
 * {@code value <= lowUpperBoundBps → LOW}, {@code <= normalUpperBoundBps → NORMAL},
 * {@code <= highUpperBoundBps → HIGH}, above → {@link #EXTREME}. {@link #UNKNOWN} is reserved for
 * non-AVAILABLE evidence (missing / untrusted / failed input) — it is a typed model value, never
 * derived by parsing reason codes. A level is context, not a directional vote and not a gate at this
 * stage.
 */
public enum VolatilityLevel {
    LOW,
    NORMAL,
    HIGH,
    EXTREME,
    /** The level could not be computed (non-AVAILABLE evidence); never a fifth regime band. */
    UNKNOWN;

    /** A real, computed regime band (everything except {@link #UNKNOWN}). */
    public boolean isClassified() {
        return this != UNKNOWN;
    }
}

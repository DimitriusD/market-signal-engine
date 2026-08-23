package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Overall quality verdict of one market interpretation snapshot (contract:
 * {@code InterpretationQualityEvent.status}). Consumers gate on it — and on
 * {@link InterpretationQuality#eligibleForTrading()} — before using any assessment or the opportunity.
 * Only {@link #DEGRADED} leaves {@code eligibleForTrading} to engine policy; every other status fixes it
 * (see {@link InterpretationQuality}).
 */
public enum InterpretationQualityStatus {
    /** All horizons eligible and source quality OK; {@code eligibleForTrading} must be {@code true}. */
    OK,
    /** Some horizons not eligible or source quality degraded; partial interpretation; eligibility is policy-dependent. */
    DEGRADED,
    /** Source quality or safety gates forbid using the snapshot for trading; {@code eligibleForTrading} = false. */
    BLOCKED,
    /** No usable market data; same consequences as {@link #BLOCKED}. */
    NO_DATA,
    /** Fallback; same consequences as {@link #BLOCKED}. */
    UNKNOWN;

    /** Whether this status permits {@code eligibleForTrading = true} at all. */
    public boolean mayBeEligibleForTrading() {
        return this == OK || this == DEGRADED;
    }
}

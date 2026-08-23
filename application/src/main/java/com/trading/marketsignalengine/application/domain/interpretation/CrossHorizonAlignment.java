package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Cross-horizon alignment verdict over the ELIGIBLE horizons of one snapshot (contract:
 * {@code CrossHorizonAssessmentEvent.alignment}). The verdict fixes the net direction and what the
 * horizon lists may contain; see {@link CrossHorizonAssessment} for the enforced table.
 */
public enum CrossHorizonAlignment {
    /** All participating horizons are bullish. */
    ALIGNED_BULLISH,
    /** All participating horizons are bearish. */
    ALIGNED_BEARISH,
    /** A dominant direction exists, at least one participating horizon is NEUTRAL or MIXED, none is opposite. */
    PARTIALLY_ALIGNED,
    /** At least one participating horizon has the direction opposite to another. */
    CONFLICTING,
    /** Participating horizons are neutral. */
    NEUTRAL,
    /** Too few eligible horizons to conclude. */
    INSUFFICIENT_DATA,
    /** Fallback. */
    UNKNOWN;

    /** Alignments that conclude nothing: direction UNKNOWN, no strength, no dominant horizon. */
    public boolean isInconclusive() {
        return this == INSUFFICIENT_DATA || this == UNKNOWN;
    }
}

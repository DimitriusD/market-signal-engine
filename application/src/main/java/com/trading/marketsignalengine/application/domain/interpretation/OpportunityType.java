package com.trading.marketsignalengine.application.domain.interpretation;

/** Opportunity pattern type (contract: {@code MarketOpportunityEvent.type}). */
public enum OpportunityType {
    /** Longer horizons set the direction and shorter horizons confirm continuation. */
    MOMENTUM_CONTINUATION,
    /** Short horizons signal a reversal against the longer-horizon move. */
    SHORT_TERM_REVERSAL,
    /** No opportunity (NO_OPPORTUNITY / BLOCKED). */
    NONE,
    /** Fallback. */
    UNKNOWN;

    /** A real, identified pattern — what a CANDIDATE must carry. */
    public boolean isPattern() {
        return this != NONE && this != UNKNOWN;
    }
}

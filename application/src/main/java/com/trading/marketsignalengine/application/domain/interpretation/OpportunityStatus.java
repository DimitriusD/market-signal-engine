package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Status of the interpreted market opportunity (contract: {@code MarketOpportunityEvent.status}).
 * {@link #NO_OPPORTUNITY} and {@link #BLOCKED} must never be conflated: the former is an honest negative
 * result of a permitted search (counts in detection analytics), the latter marks a snapshot the engine
 * was not allowed to use for a trading decision.
 */
public enum OpportunityStatus {
    /** The market was interpreted and an opportunity pattern was identified: a candidate for strategy evaluation, not an instruction. */
    CANDIDATE,
    /** The engine was allowed to search and found no opportunity pattern. */
    NO_OPPORTUNITY,
    /** The engine was not allowed to use the snapshot for a trading decision ({@code eligibleForTrading = false}). */
    BLOCKED,
    /** Fallback. */
    UNKNOWN;

    /** Statuses that are only legal when the snapshot is {@code eligibleForTrading}. */
    public boolean requiresEligibleForTrading() {
        return this == CANDIDATE || this == NO_OPPORTUNITY;
    }
}

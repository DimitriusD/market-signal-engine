package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * A group of related features assessed as one piece of evidence inside a horizon (contract:
 * {@code EvidenceAssessmentEvent.dimension}). Declaration order is the canonical order of evidence
 * assessments within a {@link HorizonAssessment}; dimensions are unique per horizon.
 */
public enum EvidenceDimension {
    /** Aggressive trade flow / signed imbalance. */
    FLOW,
    /** Short-term price momentum / return. */
    MOMENTUM,
    /** Realised volatility / range regime. */
    VOLATILITY,
    /** Order-book imbalance, depth, spread. */
    BOOK
}

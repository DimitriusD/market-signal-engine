package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Whether one evidence dimension of one horizon could be computed and used (contract:
 * {@code EvidenceAssessmentEvent.status}). Anything other than {@link #AVAILABLE} means the dimension
 * carries direction UNKNOWN and no strength.
 */
public enum EvidenceAvailabilityStatus {
    /** The evidence was computed and used. */
    AVAILABLE,
    /** The backing feature values are absent. */
    UNAVAILABLE,
    /** Values are present but quality forbids using them. */
    UNTRUSTED,
    /** The backing feature group failed to compute. */
    FAILED,
    /** Fallback. */
    UNKNOWN;

    public boolean isAvailable() {
        return this == AVAILABLE;
    }
}

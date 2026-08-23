package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Policy verdict on whether one whole horizon may be interpreted (contract:
 * {@code HorizonEligibilityEvent.status}). This is <b>not</b> input-feature availability
 * ({@code FeatureAvailabilityStatus}): availability is a fact about one feature window; eligibility is
 * a decision about the horizon as a whole, which later depends on the set of required feature groups
 * and the quality policy. Only {@link #ELIGIBLE} horizons take part in interpretation; every other
 * status means the horizon carries no usable market view (direction UNKNOWN, strength absent) — it is
 * never substituted with a neutral state.
 */
public enum HorizonEligibilityStatus {
    /** The horizon may be used for interpretation. */
    ELIGIBLE,
    /** Not enough data yet: the required rolling window is still being accumulated. */
    WARMING_UP,
    /** The feature data backing this horizon is absent (null windows, no trade/book input). */
    UNAVAILABLE,
    /** Data is present but source quality does not allow using it (stale / out-of-sync / untrusted). */
    UNTRUSTED,
    /** The backing feature group was not computed because its calculator failed. */
    FAILED,
    /** Fallback for an unrecognised state. */
    UNKNOWN;

    public boolean isEligible() {
        return this == ELIGIBLE;
    }
}

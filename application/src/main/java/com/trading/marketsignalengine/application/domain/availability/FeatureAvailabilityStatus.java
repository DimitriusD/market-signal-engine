package com.trading.marketsignalengine.application.domain.availability;

/**
 * Input-side availability of one windowed feature group for one horizon. Resolution precedence
 * (first match wins): {@link #FAILED} → {@link #UNTRUSTED} → {@link #WARMING_UP} → {@link #UNAVAILABLE}
 * → {@link #AVAILABLE}. {@code null} is never read as zero: a real numeric zero is an
 * {@link #AVAILABLE} value, an absent value is one of the four non-available states.
 */
public enum FeatureAvailabilityStatus {
    /** The window was computed and may be read; its values (including real zeros) are usable. */
    AVAILABLE,
    /** The window was not computed because the required history is still being accumulated. */
    WARMING_UP,
    /** The window was not computed and the producer gave no warm-up / failure reason (no data). */
    UNAVAILABLE,
    /** Data is present but quality forbids using it (stale trades, known trade-history gap). */
    UNTRUSTED,
    /** The producing calculator failed for this snapshot; the group was published as absent. */
    FAILED;

    public boolean isAvailable() {
        return this == AVAILABLE;
    }
}

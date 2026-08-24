package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Cross-horizon alignment verdict over the ELIGIBLE horizons of one snapshot (contract:
 * {@code CrossHorizonAssessmentEvent.alignment}). The verdict fixes the net direction and what the
 * horizon lists may contain; see {@link CrossHorizonAssessment} for the enforced table.
 *
 * <p>Alignment is decided by the <b>structural</b> horizons H60S (senior context), H15S (structure)
 * and H5S (trigger); H1S is micro/execution context only. H1S is never listed among
 * {@code conflictingHorizons} — when it is opposite or MIXED it only downgrades a full alignment to
 * {@link #PARTIALLY_ALIGNED}; a NEUTRAL or non-participating H1S does not break {@code ALIGNED_*}.
 */
public enum CrossHorizonAlignment {
    /** H60S, H15S and H5S are all bullish; a neutral or unavailable H1S does not break the alignment. */
    ALIGNED_BULLISH,
    /** H60S, H15S and H5S are all bearish; a neutral or unavailable H1S does not break the alignment. */
    ALIGNED_BEARISH,
    /**
     * A dominant structural direction exists but not a full alignment: a structural confirmation is
     * missing or a structural horizon is neutral, or H1S is adverse (opposite/MIXED). No structural
     * horizon is opposite to the dominant direction.
     */
    PARTIALLY_ALIGNED,
    /** At least one structural horizon (H60S/H15S/H5S) is opposite to the anchor or MIXED; H1S never conflicts. */
    CONFLICTING,
    /** Participating structural horizons are neutral. */
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

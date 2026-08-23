package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

/**
 * The single, shared projection of a non-ELIGIBLE {@link HorizonEligibility} onto an evidence
 * dimension, used by every evidence evaluator (FLOW, MOMENTUM, VOLATILITY, BOOK) so they can never
 * disagree about what a non-eligible horizon means:
 *
 * <pre>
 *   WARMING_UP / UNAVAILABLE → UNAVAILABLE
 *   UNTRUSTED                → UNTRUSTED
 *   FAILED                   → FAILED
 *   UNKNOWN                  → UNKNOWN
 * </pre>
 *
 * The projected assessment always has direction {@code UNKNOWN}, no evidence strength, and keeps the
 * eligibility reason codes verbatim (never renamed or re-encoded). Warm-up / missing / untrusted /
 * failed / unknown are never turned into NEUTRAL — that would be an interpreted reading of data the
 * quality layer said not to read. ELIGIBLE is never projected: an eligible horizon is evaluated, and
 * asking to project it is a programming error.
 */
public final class EvidenceEligibilityProjection {

    private EvidenceEligibilityProjection() {
    }

    /** Eligibility status → evidence availability for a non-ELIGIBLE horizon (ELIGIBLE is never projected). */
    public static EvidenceAvailabilityStatus statusOf(HorizonEligibility eligibility) {
        requireNonNull(eligibility, "eligibility");
        return switch (eligibility.status()) {
            case ELIGIBLE -> throw new IllegalArgumentException("ELIGIBLE horizons are evaluated, not projected");
            case WARMING_UP, UNAVAILABLE -> EvidenceAvailabilityStatus.UNAVAILABLE;
            case UNTRUSTED -> EvidenceAvailabilityStatus.UNTRUSTED;
            case FAILED -> EvidenceAvailabilityStatus.FAILED;
            case UNKNOWN -> EvidenceAvailabilityStatus.UNKNOWN;
        };
    }

    /**
     * The full projected {@link EvidenceAssessment} of a non-ELIGIBLE horizon for {@code dimension}:
     * projected availability, direction UNKNOWN, no strength, eligibility reasons kept verbatim.
     */
    public static EvidenceAssessment project(EvidenceDimension dimension, HorizonEligibility eligibility) {
        requireNonNull(dimension, "dimension");
        requireNonNull(eligibility, "eligibility");
        require(!eligibility.isEligible(), "ELIGIBLE horizons are evaluated, not projected");
        return EvidenceAssessment.notAvailable(dimension, statusOf(eligibility), eligibility.reasonCodes());
    }
}

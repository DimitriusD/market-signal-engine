package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceEligibilityProjection;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * Typed volatility evidence of one horizon: the generic {@code VOLATILITY} {@link EvidenceAssessment}
 * plus the {@link VolatilityLevel} the generic model has no field for. The generic model stays
 * untouched on purpose — a volatility-specific field on {@code EvidenceAssessment} would leak one
 * dimension's vocabulary into all of them.
 *
 * <p>Invariants: the evidence has dimension {@code VOLATILITY}; volatility is never a directional
 * vote, so the direction is always {@code UNKNOWN} and the strength always absent (even when
 * AVAILABLE); AVAILABLE evidence carries a classified level (LOW / NORMAL / HIGH / EXTREME) and
 * non-AVAILABLE evidence carries level {@code UNKNOWN} — a level is never parsed out of reason codes
 * and never invented for data that was not read.
 */
public record VolatilityAssessment(
        EvidenceAssessment evidence,
        VolatilityLevel level) {

    public VolatilityAssessment {
        requireNonNull(evidence, "volatility evidence");
        requireNonNull(level, "volatility level");
        require(evidence.dimension() == EvidenceDimension.VOLATILITY,
                "volatility assessment must have dimension VOLATILITY, got " + evidence.dimension());
        require(evidence.direction() == InterpretationDirection.UNKNOWN,
                "volatility is not a directional vote and must have direction UNKNOWN, got " + evidence.direction());
        require(evidence.evidenceStrength() == null,
                "volatility is not a directional vote and must not carry an evidence strength");
        if (evidence.isAvailable()) {
            require(level.isClassified(), "AVAILABLE volatility evidence must carry a classified level");
        } else {
            require(level == VolatilityLevel.UNKNOWN,
                    evidence.availabilityStatus() + " volatility evidence must have level UNKNOWN, got " + level);
        }
    }

    public EvidenceAvailabilityStatus availabilityStatus() {
        return evidence.availabilityStatus();
    }

    public boolean isAvailable() {
        return evidence.isAvailable();
    }

    public List<ReasonCode> reasonCodes() {
        return evidence.reasonCodes();
    }

    /** Computed volatility evidence with a classified level; direction UNKNOWN, no strength, by construction. */
    public static VolatilityAssessment available(VolatilityLevel level, List<ReasonCode> reasonCodes) {
        return new VolatilityAssessment(
                EvidenceAssessment.available(EvidenceDimension.VOLATILITY, InterpretationDirection.UNKNOWN,
                        null, reasonCodes),
                level);
    }

    /** Evidence that could not be used ({@code status} must not be AVAILABLE); level UNKNOWN by construction. */
    public static VolatilityAssessment notAvailable(EvidenceAvailabilityStatus status, List<ReasonCode> reasonCodes) {
        return new VolatilityAssessment(
                EvidenceAssessment.notAvailable(EvidenceDimension.VOLATILITY, status, reasonCodes),
                VolatilityLevel.UNKNOWN);
    }

    /** The shared eligibility projection of a non-ELIGIBLE horizon, wrapped with level UNKNOWN. */
    public static VolatilityAssessment projected(HorizonEligibility eligibility) {
        return new VolatilityAssessment(
                EvidenceEligibilityProjection.project(EvidenceDimension.VOLATILITY, eligibility),
                VolatilityLevel.UNKNOWN);
    }
}

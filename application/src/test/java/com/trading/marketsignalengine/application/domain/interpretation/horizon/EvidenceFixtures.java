package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityLevel;
import java.util.List;

/**
 * Hand-built typed evidence for the package-private resolver unit tests. Reason codes are synthetic
 * markers ({@code TEST_*}) so tests can also assert that resolvers never read or copy nested reason
 * codes.
 */
final class EvidenceFixtures {

    static final ReasonCode NESTED_REASON = ReasonCode.of("TEST_NESTED_EVIDENCE_REASON");

    private EvidenceFixtures() {
    }

    static EvidenceAssessment available(EvidenceDimension dimension, InterpretationDirection direction,
                                        String strength) {
        return EvidenceAssessment.available(dimension, direction,
                strength == null ? null : EvidenceStrength.of(strength), List.of(NESTED_REASON));
    }

    static EvidenceAssessment neutral(EvidenceDimension dimension) {
        return EvidenceAssessment.available(dimension, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, List.of(NESTED_REASON));
    }

    /** AVAILABLE but not interpretable (e.g. flow insufficient activity): direction UNKNOWN, no strength. */
    static EvidenceAssessment availableUnknown(EvidenceDimension dimension) {
        return EvidenceAssessment.available(dimension, InterpretationDirection.UNKNOWN, null, List.of(NESTED_REASON));
    }

    static EvidenceAssessment notAvailable(EvidenceDimension dimension, EvidenceAvailabilityStatus status) {
        return EvidenceAssessment.notAvailable(dimension, status, List.of(NESTED_REASON));
    }

    static EvidenceAssessment unavailable(EvidenceDimension dimension) {
        return notAvailable(dimension, EvidenceAvailabilityStatus.UNAVAILABLE);
    }

    // ------------------------------------------------------------------ volatility

    static VolatilityAssessment volatility(VolatilityLevel level) {
        return VolatilityAssessment.available(level, List.of(NESTED_REASON));
    }

    static VolatilityAssessment volatilityNotAvailable(EvidenceAvailabilityStatus status) {
        return VolatilityAssessment.notAvailable(status, List.of(NESTED_REASON));
    }
}

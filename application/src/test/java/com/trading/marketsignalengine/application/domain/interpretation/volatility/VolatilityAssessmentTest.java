package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The typed volatility wrapper: VOLATILITY dimension only, never directional, never a strength,
 * classified level iff AVAILABLE, level UNKNOWN otherwise — the level is a typed model value, not a
 * parsed reason code.
 */
class VolatilityAssessmentTest {

    private static final List<ReasonCode> REASONS = List.of(VolatilityReasonCodes.VOLATILITY_NORMAL);

    @ParameterizedTest
    @EnumSource(value = VolatilityLevel.class, names = {"LOW", "NORMAL", "HIGH", "EXTREME"})
    void availableEvidenceCarriesAClassifiedLevelAndNoDirectionOrStrength(VolatilityLevel level) {
        VolatilityAssessment assessment = VolatilityAssessment.available(level, REASONS);

        assertTrue(assessment.isAvailable());
        assertEquals(level, assessment.level());
        assertEquals(EvidenceDimension.VOLATILITY, assessment.evidence().dimension());
        assertEquals(InterpretationDirection.UNKNOWN, assessment.evidence().direction());
        assertNull(assessment.evidence().evidenceStrength());
        assertEquals(REASONS, assessment.reasonCodes());
    }

    @Test
    void availableEvidenceMustNotHaveLevelUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityAssessment.available(VolatilityLevel.UNKNOWN, REASONS));
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceAvailabilityStatus.class, names = {"UNAVAILABLE", "UNTRUSTED", "FAILED", "UNKNOWN"})
    void nonAvailableEvidenceHasLevelUnknown(EvidenceAvailabilityStatus status) {
        VolatilityAssessment assessment = VolatilityAssessment.notAvailable(status, REASONS);

        assertEquals(status, assessment.availabilityStatus());
        assertEquals(VolatilityLevel.UNKNOWN, assessment.level());

        // a classified level on non-available evidence is rejected
        EvidenceAssessment evidence = EvidenceAssessment.notAvailable(EvidenceDimension.VOLATILITY, status, REASONS);
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessment(evidence, VolatilityLevel.NORMAL));
    }

    @Test
    void wrongDimensionDirectionOrStrengthIsRejected() {
        EvidenceAssessment flow = EvidenceAssessment.available(EvidenceDimension.FLOW,
                InterpretationDirection.UNKNOWN, null, REASONS);
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessment(flow, VolatilityLevel.NORMAL));

        EvidenceAssessment directional = EvidenceAssessment.available(EvidenceDimension.VOLATILITY,
                InterpretationDirection.BULLISH, null, REASONS);
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessment(directional, VolatilityLevel.NORMAL),
                "volatility never votes BULLISH");

        EvidenceAssessment withStrength = EvidenceAssessment.available(EvidenceDimension.VOLATILITY,
                InterpretationDirection.UNKNOWN, EvidenceStrength.of("0.5"), REASONS);
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessment(withStrength, VolatilityLevel.NORMAL),
                "volatility never carries a strength");
    }

    @Test
    void nullsAreRejected() {
        EvidenceAssessment evidence = EvidenceAssessment.available(EvidenceDimension.VOLATILITY,
                InterpretationDirection.UNKNOWN, null, REASONS);

        assertThrows(IllegalArgumentException.class, () -> new VolatilityAssessment(null, VolatilityLevel.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityAssessment(evidence, null));
    }

    @Test
    void projectedWrapsTheSharedEligibilityProjectionWithLevelUnknown() {
        List<ReasonCode> eligibilityReasons = List.of(ReasonCode.of("STALE_TRADES"));
        VolatilityAssessment projected = VolatilityAssessment.projected(HorizonEligibility.untrusted(eligibilityReasons));

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, projected.availabilityStatus());
        assertEquals(VolatilityLevel.UNKNOWN, projected.level());
        assertEquals(eligibilityReasons, projected.reasonCodes(), "eligibility reasons are kept verbatim");
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityAssessment.projected(HorizonEligibility.eligible()));
    }

    @Test
    void valueEquality() {
        VolatilityAssessment a = VolatilityAssessment.available(VolatilityLevel.HIGH, REASONS);
        VolatilityAssessment b = VolatilityAssessment.available(VolatilityLevel.HIGH, REASONS);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

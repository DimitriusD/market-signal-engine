package com.trading.marketsignalengine.application.domain.interpretation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The shared eligibility → evidence-availability projection: one fixed mapping for every evaluator,
 * direction always UNKNOWN, never a strength, eligibility reasons kept verbatim, ELIGIBLE never
 * projected and no status ever collapsed to NEUTRAL.
 */
class EvidenceEligibilityProjectionTest {

    private static final List<ReasonCode> REASONS =
            List.of(ReasonCode.of("SOME_REASON"), ReasonCode.of("ANOTHER_REASON"));

    @Test
    void mapsEveryNonEligibleStatusToItsEvidenceAvailability() {
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE,
                EvidenceEligibilityProjection.statusOf(HorizonEligibility.warmingUp(REASONS)));
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE,
                EvidenceEligibilityProjection.statusOf(HorizonEligibility.unavailable(REASONS)));
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED,
                EvidenceEligibilityProjection.statusOf(HorizonEligibility.untrusted(REASONS)));
        assertEquals(EvidenceAvailabilityStatus.FAILED,
                EvidenceEligibilityProjection.statusOf(HorizonEligibility.failed(REASONS)));
        assertEquals(EvidenceAvailabilityStatus.UNKNOWN,
                EvidenceEligibilityProjection.statusOf(HorizonEligibility.unknown(REASONS)));
    }

    @Test
    void eligibleIsNeverProjected() {
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceEligibilityProjection.statusOf(HorizonEligibility.eligible()));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceEligibilityProjection.project(EvidenceDimension.FLOW, HorizonEligibility.eligible()));
    }

    @ParameterizedTest
    @EnumSource(EvidenceDimension.class)
    void projectedAssessmentHasUnknownDirectionNoStrengthAndVerbatimReasonsForEveryDimension(EvidenceDimension dimension) {
        EvidenceAssessment projected =
                EvidenceEligibilityProjection.project(dimension, HorizonEligibility.untrusted(REASONS));

        assertEquals(dimension, projected.dimension());
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, projected.availabilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, projected.direction(),
                "a non-eligible horizon is never read as NEUTRAL");
        assertNull(projected.evidenceStrength());
        assertEquals(REASONS, projected.reasonCodes(), "eligibility reasons are kept verbatim");
        assertFalse(projected.isAvailable());
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceEligibilityProjection.statusOf(null));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceEligibilityProjection.project(null, HorizonEligibility.untrusted(REASONS)));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceEligibilityProjection.project(EvidenceDimension.FLOW, null));
    }
}

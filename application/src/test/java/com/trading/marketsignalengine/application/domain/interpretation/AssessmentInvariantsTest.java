package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.STALE_TRADES;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.WINDOW_WARMING_UP;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.bullishFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.strength;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** InterpretationQuality, HorizonEligibility, EvidenceAssessment and HorizonAssessment invariants. */
class AssessmentInvariantsTest {

    // ------------------------------------------------------------------ InterpretationQuality

    @Test
    void qualityStatusFixesEligibilityExceptForDegraded() {
        assertTrue(InterpretationQuality.ok(List.of()).eligibleForTrading());
        assertTrue(InterpretationQuality.degraded(true, List.of()).eligibleForTrading());
        assertFalse(InterpretationQuality.degraded(false, List.of()).eligibleForTrading());
        assertFalse(InterpretationQuality.blocked(List.of()).eligibleForTrading());
        assertFalse(InterpretationQuality.noData(List.of()).eligibleForTrading());
        assertFalse(InterpretationQuality.unknown(List.of()).eligibleForTrading());

        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationQuality(InterpretationQualityStatus.OK, false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationQuality(InterpretationQualityStatus.BLOCKED, true, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationQuality(InterpretationQualityStatus.NO_DATA, true, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new InterpretationQuality(InterpretationQualityStatus.UNKNOWN, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationQuality(null, true, List.of()));
        // DEGRADED is not promoted to BLOCKED by the model
        assertEquals(InterpretationQualityStatus.DEGRADED, InterpretationQuality.degraded(false, List.of()).status());
    }

    @Test
    void qualityReasonCodesAreImmutableAndDuplicateFree() {
        InterpretationQuality quality = InterpretationQuality.blocked(List.of(STALE_TRADES));
        assertThrows(UnsupportedOperationException.class, () -> quality.reasonCodes().add(STALE_TRADES));
        assertThrows(IllegalArgumentException.class,
                () -> InterpretationQuality.blocked(List.of(STALE_TRADES, STALE_TRADES)));
        assertEquals(List.of(), InterpretationQuality.ok(null).reasonCodes());
    }

    // ------------------------------------------------------------------ HorizonEligibility

    @Test
    void eligibilityFactoriesCarryTheirStatus() {
        assertTrue(HorizonEligibility.eligible().isEligible());
        assertEquals(HorizonEligibilityStatus.WARMING_UP, HorizonEligibility.warmingUp(List.of(WINDOW_WARMING_UP)).status());
        assertEquals(HorizonEligibilityStatus.UNAVAILABLE, HorizonEligibility.unavailable(List.of()).status());
        assertEquals(HorizonEligibilityStatus.UNTRUSTED, HorizonEligibility.untrusted(List.of(STALE_TRADES)).status());
        assertEquals(HorizonEligibilityStatus.FAILED, HorizonEligibility.failed(List.of()).status());
        assertEquals(HorizonEligibilityStatus.UNKNOWN, HorizonEligibility.unknown(List.of()).status());
        assertFalse(HorizonEligibility.unknown(List.of()).isEligible());
        assertThrows(IllegalArgumentException.class, () -> new HorizonEligibility(null, List.of()));
    }

    // ------------------------------------------------------------------ EvidenceAssessment

    @Test
    void availableEvidenceCarriesDirectionAndOptionalStrength() {
        EvidenceAssessment flow = bullishFlow();
        assertTrue(flow.isAvailable());
        assertEquals(InterpretationDirection.BULLISH, flow.direction());
        assertEquals(strength("0.6"), flow.evidenceStrength());

        EvidenceAssessment noStrength = EvidenceAssessment.available(EvidenceDimension.BOOK,
                InterpretationDirection.NEUTRAL, null, List.of());
        assertNull(noStrength.evidenceStrength());
        // available but not interpretable is allowed: UNKNOWN is not forced into NEUTRAL
        EvidenceAssessment unknownReading = EvidenceAssessment.available(EvidenceDimension.MOMENTUM,
                InterpretationDirection.UNKNOWN, null, List.of());
        assertEquals(InterpretationDirection.UNKNOWN, unknownReading.direction());
    }

    @Test
    void nonAvailableEvidenceMustBeUnknownWithoutStrength() {
        for (EvidenceAvailabilityStatus status : EvidenceAvailabilityStatus.values()) {
            if (status.isAvailable()) {
                continue;
            }
            EvidenceAssessment ok = EvidenceAssessment.notAvailable(EvidenceDimension.FLOW, status, List.of());
            assertEquals(InterpretationDirection.UNKNOWN, ok.direction());
            assertNull(ok.evidenceStrength());

            assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                    EvidenceDimension.FLOW, status, InterpretationDirection.BULLISH, null, List.of()), status + " + direction");
            assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                    EvidenceDimension.FLOW, status, InterpretationDirection.NEUTRAL, null, List.of()), status + " + NEUTRAL");
            assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                    EvidenceDimension.FLOW, status, InterpretationDirection.UNKNOWN, strength("0.1"), List.of()), status + " + strength");
        }
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceAssessment.notAvailable(EvidenceDimension.FLOW, EvidenceAvailabilityStatus.AVAILABLE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                null, EvidenceAvailabilityStatus.AVAILABLE, InterpretationDirection.BULLISH, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                EvidenceDimension.FLOW, null, InterpretationDirection.BULLISH, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceAssessment(
                EvidenceDimension.FLOW, EvidenceAvailabilityStatus.AVAILABLE, null, null, List.of()));
    }

    // ------------------------------------------------------------------ HorizonAssessment

    @Test
    void eligibleHorizonCarriesDirectionStrengthRegimeAndEvidence() {
        HorizonAssessment horizon = HorizonAssessment.eligible(H5S, InterpretationDirection.MIXED, strength("0.3"),
                MarketRegime.RANGING, List.of(bullishFlow()), List.of(ReasonCode.of("MOMENTUM_CONTRADICTS_FLOW")));
        assertTrue(horizon.isEligible());
        assertEquals(InterpretationDirection.MIXED, horizon.direction());
        assertEquals(MarketRegime.RANGING, horizon.regime());
        assertEquals(bullishFlow(), horizon.evidence(EvidenceDimension.FLOW).orElseThrow());
        assertTrue(horizon.evidence(EvidenceDimension.BOOK).isEmpty());
        // eligible horizons may legitimately read UNKNOWN, and are not forced to NEUTRAL
        assertEquals(InterpretationDirection.UNKNOWN,
                HorizonAssessment.eligible(H5S, InterpretationDirection.UNKNOWN, null, null, List.of(), List.of()).direction());
    }

    @Test
    void nonEligibleHorizonIsUnknownWithoutStrengthRegimeOrAvailableEvidence() {
        HorizonAssessment warming = HorizonAssessment.warmingUp(H15S, List.of(WINDOW_WARMING_UP));
        assertFalse(warming.isEligible());
        assertEquals(HorizonEligibilityStatus.WARMING_UP, warming.eligibilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, warming.direction());
        assertNull(warming.evidenceStrength());
        assertNull(warming.regime());
        assertEquals(List.of(), warming.evidenceAssessments());
        assertEquals(HorizonEligibilityStatus.UNAVAILABLE, HorizonAssessment.unavailable(H15S, List.of()).eligibilityStatus());
        assertEquals(HorizonEligibilityStatus.UNTRUSTED, HorizonAssessment.untrusted(H15S, List.of()).eligibilityStatus());
        assertEquals(HorizonEligibilityStatus.FAILED, HorizonAssessment.failed(H15S, List.of()).eligibilityStatus());
        assertEquals(HorizonEligibilityStatus.UNKNOWN, HorizonAssessment.unknown(H15S, List.of()).eligibilityStatus());

        // non-available evidence may explain the missing dimension
        HorizonAssessment explained = HorizonAssessment.notEligible(H15S, HorizonEligibility.untrusted(List.of(STALE_TRADES)),
                List.of(EvidenceAssessment.untrusted(EvidenceDimension.FLOW, List.of(STALE_TRADES))), List.of());
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, explained.evidence(EvidenceDimension.FLOW).orElseThrow().availabilityStatus());

        HorizonEligibility warmingUp = HorizonEligibility.warmingUp(List.of());
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H15S, warmingUp,
                InterpretationDirection.NEUTRAL, null, null, List.of(), List.of()), "non-eligible + NEUTRAL");
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H15S, warmingUp,
                InterpretationDirection.BULLISH, null, null, List.of(), List.of()), "non-eligible + BULLISH");
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H15S, warmingUp,
                InterpretationDirection.UNKNOWN, strength("0.2"), null, List.of(), List.of()), "non-eligible + strength");
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H15S, warmingUp,
                InterpretationDirection.UNKNOWN, null, MarketRegime.QUIET, List.of(), List.of()), "non-eligible + regime");
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H15S, warmingUp,
                InterpretationDirection.UNKNOWN, null, null, List.of(bullishFlow()), List.of()), "non-eligible + AVAILABLE evidence");
        assertThrows(IllegalArgumentException.class,
                () -> HorizonAssessment.notEligible(H15S, HorizonEligibility.eligible(), List.of(), List.of()));
    }

    @Test
    void horizonEvidenceDimensionsAreUniqueAndCanonicallyOrdered() {
        EvidenceAssessment book = EvidenceAssessment.available(EvidenceDimension.BOOK, InterpretationDirection.BULLISH, null, List.of());
        EvidenceAssessment flow = bullishFlow();
        HorizonAssessment horizon = HorizonAssessment.eligible(H5S, InterpretationDirection.BULLISH, null, null,
                List.of(book, flow), List.of());
        assertEquals(List.of(flow, book), horizon.evidenceAssessments(), "stored in FLOW, MOMENTUM, VOLATILITY, BOOK order");
        assertThrows(UnsupportedOperationException.class, () -> horizon.evidenceAssessments().add(book));

        assertThrows(IllegalArgumentException.class, () -> HorizonAssessment.eligible(H5S, InterpretationDirection.BULLISH,
                null, null, List.of(flow, flow), List.of()));
        assertThrows(IllegalArgumentException.class, () -> HorizonAssessment.eligible(H5S, InterpretationDirection.BULLISH,
                null, null, java.util.Arrays.asList(flow, null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(null, HorizonEligibility.eligible(),
                InterpretationDirection.BULLISH, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H5S, null,
                InterpretationDirection.BULLISH, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessment(H5S, HorizonEligibility.eligible(),
                null, null, null, List.of(), List.of()));
    }
}

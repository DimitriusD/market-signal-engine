package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shared snapshot ↔ assessment consistency guard: a pair from the real Stage 3 resolver passes;
 * every mismatched source fact — status, future-event flag, both source instants, failed groups and
 * the re-derived per-horizon eligibilities — fails fast with the fact named.
 */
class SnapshotQualityConsistencyGuardTest {

    private final SnapshotQualityConsistencyGuard guard = new SnapshotQualityConsistencyGuard();
    private final QualityAssessmentResolver resolver = new QualityAssessmentResolver();

    private QualityAssessment assess(MarketFeaturesSnapshot snapshot) {
        return resolver.resolve(snapshot, QualityFixtures.FRESH_ASSESSED_AT, QualityFixtures.POLICY);
    }

    private static MarketFeaturesSnapshot allComputed() {
        return QualityFixtures.allComputed();
    }

    @Test
    void matchingPairFromTheRealResolverPasses() {
        MarketFeaturesSnapshot snapshot = allComputed();
        assertDoesNotThrow(() -> guard.verify(snapshot, assess(snapshot)));

        // a degraded but honest pair passes as well
        MarketFeaturesSnapshot stale = QualityFixtures.snapshot(QualityFixtures.allWindows(),
                QualityFixtures.staleTradesQuality());
        assertDoesNotThrow(() -> guard.verify(stale, assess(stale)));
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = allComputed();
        QualityAssessment qa = assess(snapshot);

        assertThrows(IllegalArgumentException.class, () -> guard.verify(null, qa));
        assertThrows(IllegalArgumentException.class, () -> guard.verify(snapshot, null));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotQualityConsistencyGuard(null));
    }

    @Test
    void sourceQualityStatusMismatchIsRejected() {
        QualityAssessment ofOkSnapshot = assess(allComputed());
        MarketFeaturesSnapshot degraded = QualityFixtures.snapshot(QualityFixtures.allWindows(),
                QualityFixtures.degradedQuality(List.of("INCOMPLETE_BOOK")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(degraded, ofOkSnapshot));
        assertTrue(ex.getMessage().contains("sourceQualityStatus"), ex.getMessage());
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());
    }

    @Test
    void futureEventDetectedMismatchIsRejected() {
        QualityAssessment ofCleanSnapshot = assess(allComputed());
        // same OK status and same eligibilities — only the upstream future-event flag differs
        MarketFeaturesSnapshot futureEvent = QualityFixtures.snapshot(QualityFixtures.allWindows(),
                QualityFixtures.okQuality().toBuilder().futureEventDetected(true).build());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(futureEvent, ofCleanSnapshot));
        assertTrue(ex.getMessage().contains("futureEventDetected"), ex.getMessage());
    }

    @Test
    void sourceEvaluationAtMismatchIsRejected() {
        QualityAssessment qa = assess(allComputed());
        MarketFeaturesSnapshot otherAsOf = allComputed().toBuilder()
                .evaluationTs(QualityFixtures.EVENT_TIME.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(otherAsOf, qa));
        assertTrue(ex.getMessage().contains("timing.sourceEvaluationAt"), ex.getMessage());
    }

    @Test
    void sourceComputedAtMismatchIsRejected() {
        QualityAssessment qa = assess(allComputed());
        MarketFeaturesSnapshot otherComputedAt = allComputed().toBuilder()
                .computedAt(QualityFixtures.COMPUTED_AT.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(otherComputedAt, qa));
        assertTrue(ex.getMessage().contains("timing.sourceComputedAt"), ex.getMessage());
    }

    @Test
    void failedFeatureGroupsMismatchIsRejected() {
        QualityAssessment ofCleanSnapshot = assess(allComputed());
        // a failed bbo group does not touch trade-flow eligibilities, so only the groups fact differs
        MarketFeaturesSnapshot failedBbo = allComputed().toBuilder()
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("bbo")).totalFeatureGroups(4).build())
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(failedBbo, ofCleanSnapshot));
        assertTrue(ex.getMessage().contains("failedFeatureGroups"), ex.getMessage());
    }

    @Test
    void horizonEligibilitiesAreReDerivedThroughTheCanonicalResolverNotCopied() {
        // A: DEGRADED because the book is incomplete — trade-flow horizons stay ELIGIBLE.
        MarketFeaturesSnapshot bookDegraded = QualityFixtures.snapshot(QualityFixtures.allWindows(),
                QualityFixtures.degradedQuality(List.of("INCOMPLETE_BOOK")).toBuilder().incompleteBook(true).build());
        // B: DEGRADED because trades are stale — every horizon is UNTRUSTED.
        MarketFeaturesSnapshot staleTrades = QualityFixtures.snapshot(QualityFixtures.allWindows(),
                QualityFixtures.staleTradesQuality());
        QualityAssessment assessmentA = assess(bookDegraded);
        QualityAssessment assessmentB = assess(staleTrades);

        // Precondition: every fact checked before the eligibilities is identical between A and B.
        assertEquals(assessmentA.sourceQualityStatus(), assessmentB.sourceQualityStatus());
        assertEquals(FeatureQualityStatus.DEGRADED, assessmentA.sourceQualityStatus());
        assertEquals(assessmentA.futureEventDetected(), assessmentB.futureEventDetected());
        assertEquals(assessmentA.timing().sourceEvaluationAt(), assessmentB.timing().sourceEvaluationAt());
        assertEquals(assessmentA.timing().sourceComputedAt(), assessmentB.timing().sourceComputedAt());
        assertEquals(assessmentA.failedFeatureGroups(), assessmentB.failedFeatureGroups());
        assertTrue(assessmentA.horizonEligibilities().allEligible());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(HorizonEligibilityStatus.UNTRUSTED, assessmentB.horizonEligibilities().statusOf(horizon));
        }

        // Only the re-derived eligibilities separate the two — and the guard catches the swap.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.verify(staleTrades, assessmentA));
        assertTrue(ex.getMessage().contains("horizonEligibilities"), ex.getMessage());
        assertDoesNotThrow(() -> guard.verify(staleTrades, assessmentB));
        assertDoesNotThrow(() -> guard.verify(bookDegraded, assessmentA));
    }
}

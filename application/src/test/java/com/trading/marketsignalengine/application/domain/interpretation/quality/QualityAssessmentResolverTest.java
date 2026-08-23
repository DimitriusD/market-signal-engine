package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus.BLOCKED;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus.DEGRADED;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus.NO_DATA;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus.OK;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.ALLOW_FUTURE_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.COMPUTED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.EVENT_TIME;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.FRESH_ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.allComputed;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.allWindows;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.shortWindowsOnly;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The global quality policy: hard gates (NO_DATA / UNSAFE / clock skew / stale / blocked future
 * event) fail closed, partial horizon eligibility degrades without blocking, failed non-trade-flow
 * groups degrade without failing trade-flow horizons, collections are immutable and reason codes
 * deterministic and duplicate-free.
 */
class QualityAssessmentResolverTest {

    private final QualityAssessmentResolver resolver = new QualityAssessmentResolver();

    // ------------------------------------------------------------------ source OK

    @Test
    void okSourceAllEligibleFreshIsOkAndEligible() {
        QualityAssessment assessment = resolver.resolve(allComputed(), FRESH_ASSESSED_AT, POLICY);

        assertEquals(OK, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertEquals(FeatureQualityStatus.OK, assessment.sourceQualityStatus());
        assertEquals(TimingStatus.FRESH, assessment.timing().status());
        assertTrue(assessment.horizonEligibilities().allEligible());
        assertTrue(assessment.failedFeatureGroups().isEmpty());
        assertFalse(assessment.futureEventDetected());
        assertTrue(assessment.reasonCodes().isEmpty());
        assertTrue(assessment.interpretationQuality().reasonCodes().isEmpty());
    }

    @Test
    void okSourceWithPartialEligibilityIsDegradedButEligible() {
        // Source OK, but 15S/60S not computed (no warm-up / gap reason): UNAVAILABLE.
        QualityAssessment assessment = resolver.resolve(snapshot(shortWindowsOnly()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertEquals(List.of(H1S, H5S), assessment.eligibleHorizons());
        assertEquals(HorizonEligibilityStatus.UNAVAILABLE, assessment.eligibilityOf(H15S).status());
        assertEquals(List.of(QualityReasonCodes.HORIZONS_PARTIALLY_ELIGIBLE), assessment.reasonCodes());
    }

    @Test
    void okSourceWithNoEligibleHorizonIsDegradedAndNotEligible() {
        QualityAssessment assessment = resolver.resolve(snapshot(null), FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertEquals(List.of(QualityReasonCodes.NO_ELIGIBLE_HORIZONS), assessment.reasonCodes());
    }

    // ------------------------------------------------------------------ source DEGRADED

    @Test
    void degradedSourceWithShortHorizonsEligibleDuringWarmUpIsDegradedAndEligible() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.warmingUpQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertEquals(HorizonEligibilityStatus.ELIGIBLE, assessment.eligibilityOf(H1S).status());
        assertEquals(HorizonEligibilityStatus.ELIGIBLE, assessment.eligibilityOf(H5S).status());
        assertEquals(HorizonEligibilityStatus.WARMING_UP, assessment.eligibilityOf(H15S).status());
        assertEquals(HorizonEligibilityStatus.WARMING_UP, assessment.eligibilityOf(H60S).status());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.HORIZONS_PARTIALLY_ELIGIBLE),
                assessment.reasonCodes());
    }

    @Test
    void degradedSourceWithHistoryGapKeepsShortHorizonsEligible() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.historyGapQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertEquals(List.of(H1S, H5S), assessment.eligibleHorizons());
        assertEquals(HorizonEligibilityStatus.UNTRUSTED, assessment.eligibilityOf(H60S).status());
    }

    @Test
    void degradedSourceWithNoEligibleHorizonIsDegradedAndNotEligible() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.staleTradesQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertTrue(assessment.eligibleHorizons().isEmpty());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.NO_ELIGIBLE_HORIZONS),
                assessment.reasonCodes());
    }

    @Test
    void degradedSourceWithAllHorizonsEligibleIsDegradedAndEligible() {
        // e.g. incomplete book: degrades the source but leaves every trade-flow horizon usable.
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.degradedQuality(List.of("INCOMPLETE_BOOK")).toBuilder()
                        .incompleteBook(true).build()),
                FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertTrue(assessment.horizonEligibilities().allEligible());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED), assessment.reasonCodes());
    }

    // ------------------------------------------------------------------ hard gates

    @Test
    void unsafeSourceIsBlockedAndNotEligibleButHorizonsKeepTheirOwnVerdict() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.unsafeQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_UNSAFE), assessment.reasonCodes());
        // Overall hard gate ≠ horizon feature eligibility: the trade-flow windows are still there.
        assertTrue(assessment.horizonEligibilities().allEligible());
    }

    @Test
    void noDataSourceIsNoDataAndNotEligibleWithEveryHorizonUnavailable() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(null, QualityFixtures.noDataQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(NO_DATA, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertEquals(FeatureQualityStatus.NO_DATA, assessment.sourceQualityStatus());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(HorizonEligibilityStatus.UNAVAILABLE, assessment.eligibilityOf(horizon).status());
        }
        assertEquals(List.of(QualityReasonCodes.SOURCE_NO_DATA, QualityReasonCodes.NO_ELIGIBLE_HORIZONS),
                assessment.reasonCodes());
    }

    @Test
    void staleSnapshotIsBlockedAndNotEligible() {
        Instant lateAssessment = EVENT_TIME.plusSeconds(5);
        QualityAssessment assessment = resolver.resolve(allComputed(), lateAssessment, POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertEquals(TimingStatus.STALE, assessment.timing().status());
        assertEquals(5_000L, assessment.timing().featureAgeMs());
        assertEquals(List.of(QualityReasonCodes.FEATURE_SNAPSHOT_STALE, QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED),
                assessment.reasonCodes());
        assertTrue(assessment.horizonEligibilities().allEligible(), "horizon verdicts are not rewritten by the gate");
    }

    @Test
    void processingLatencyExceededAloneIsBlocked() {
        // Age inside the threshold (1025 ms), latency 1 ms over it.
        QualityAssessment assessment = resolver.resolve(allComputed(), COMPUTED_AT.plusMillis(1_001), POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertEquals(List.of(QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED), assessment.reasonCodes());
    }

    @Test
    void clockSkewIsBlockedAndNotEligibleWithoutClamping() {
        QualityAssessment assessment = resolver.resolve(allComputed(), EVENT_TIME.minusMillis(1), POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertEquals(TimingStatus.CLOCK_SKEW, assessment.timing().status());
        assertEquals(-1L, assessment.timing().featureAgeMs());
        assertEquals(-26L, assessment.timing().processingLatencyMs());
        assertEquals(List.of(QualityReasonCodes.SOURCE_CLOCK_SKEW, QualityReasonCodes.SOURCE_FUTURE_EVENT),
                assessment.reasonCodes());
    }

    @Test
    void futureEventWithBlockingPolicyIsBlocked() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.futureEventQuality()), FRESH_ASSESSED_AT, POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        assertTrue(assessment.futureEventDetected());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.SOURCE_FUTURE_EVENT),
                assessment.reasonCodes());
    }

    @Test
    void futureEventWithAllowingPolicyIsDegradedAndEligibleByHorizons() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.futureEventQuality()), FRESH_ASSESSED_AT, ALLOW_FUTURE_POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertTrue(assessment.futureEventDetected());
        assertTrue(assessment.reasonCodes().contains(QualityReasonCodes.SOURCE_FUTURE_EVENT), "reason kept even when allowed");

        QualityAssessment noHorizons = resolver.resolve(
                snapshot(null, QualityFixtures.futureEventQuality()), FRESH_ASSESSED_AT, ALLOW_FUTURE_POLICY);
        assertEquals(DEGRADED, noHorizons.status());
        assertFalse(noHorizons.eligibleForTrading());
    }

    @Test
    void futureEventReasonIsNotDuplicatedWhenUpstreamFlagAndTimingBothReportIt() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.futureEventQuality()), EVENT_TIME.minusMillis(5), POLICY);

        assertEquals(BLOCKED, assessment.status());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.SOURCE_FUTURE_EVENT,
                QualityReasonCodes.SOURCE_CLOCK_SKEW), assessment.reasonCodes());
    }

    // ------------------------------------------------------------------ failed feature groups

    @Test
    void failedNonTradeFlowGroupIsRecordedAndDegradesWithoutFailingHorizons() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.calculatorFailureQuality(), List.of("bbo", "short-term-regime")),
                FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertTrue(assessment.eligibleForTrading());
        assertTrue(assessment.horizonEligibilities().allEligible());
        assertEquals(List.of(FeatureGroupId.BBO, FeatureGroupId.SHORT_TERM_REGIME), assessment.failedFeatureGroups());
        assertTrue(assessment.hasFailedFeatureGroup(FeatureGroupId.BBO));
        assertFalse(assessment.hasFailedFeatureGroup(FeatureGroupId.TRADE_FLOW));
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.FEATURE_GROUP_FAILURE),
                assessment.reasonCodes());
    }

    @Test
    void failedTradeFlowGroupFailsEveryHorizonAndIsNotEligible() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.calculatorFailureQuality(), List.of("trade-flow")),
                FRESH_ASSESSED_AT, POLICY);

        assertEquals(DEGRADED, assessment.status());
        assertFalse(assessment.eligibleForTrading());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(HorizonEligibilityStatus.FAILED, assessment.eligibilityOf(horizon).status());
        }
        assertEquals(List.of(FeatureGroupId.TRADE_FLOW), assessment.failedFeatureGroups());
        assertEquals(List.of(QualityReasonCodes.SOURCE_QUALITY_DEGRADED, QualityReasonCodes.FEATURE_GROUP_FAILURE,
                QualityReasonCodes.NO_ELIGIBLE_HORIZONS), assessment.reasonCodes());
    }

    @Test
    void unknownFailedGroupIdIsPreservedVerbatimAndWireDuplicatesCollapse() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.calculatorFailureQuality(),
                        List.of("future-group", "bbo", "future-group")),
                FRESH_ASSESSED_AT, POLICY);

        assertEquals(List.of(FeatureGroupId.of("future-group"), FeatureGroupId.BBO), assessment.failedFeatureGroups());
        assertFalse(FeatureGroupId.of("future-group").isKnown());
        assertTrue(assessment.horizonEligibilities().allEligible());
    }

    // ------------------------------------------------------------------ immutability / determinism / fail-fast

    @Test
    void collectionsAreImmutable() {
        QualityAssessment assessment = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.calculatorFailureQuality(), List.of("bbo")),
                FRESH_ASSESSED_AT, POLICY);

        assertThrows(UnsupportedOperationException.class, () -> assessment.reasonCodes().add(ReasonCode.of("X")));
        assertThrows(UnsupportedOperationException.class, () -> assessment.failedFeatureGroups().add(FeatureGroupId.BBO));
        assertThrows(UnsupportedOperationException.class, () -> assessment.timing().reasonCodes().add(ReasonCode.of("X")));
        assertThrows(UnsupportedOperationException.class, () -> assessment.horizonEligibilities().asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> assessment.horizonEligibilities().asList().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessment.eligibleHorizons().add(H60S));
        assertThrows(UnsupportedOperationException.class,
                () -> assessment.eligibilityOf(H15S).reasonCodes().add(ReasonCode.of("X")));
        assertThrows(UnsupportedOperationException.class,
                () -> assessment.interpretationQuality().reasonCodes().add(ReasonCode.of("X")));
    }

    @Test
    void reasonCodesAreDeterministicAndDuplicateFreeAcrossScenarios() {
        List<MarketFeaturesSnapshot> snapshots = List.of(
                allComputed(),
                snapshot(shortWindowsOnly(), QualityFixtures.warmingUpQuality()),
                snapshot(allWindows(), QualityFixtures.staleTradesQuality()),
                snapshot(allWindows(), QualityFixtures.unsafeQuality()),
                snapshot(null, QualityFixtures.noDataQuality()),
                snapshot(allWindows(), QualityFixtures.futureEventQuality()),
                snapshot(allWindows(), QualityFixtures.calculatorFailureQuality(), List.of("trade-flow", "bbo")));
        List<Instant> instants = List.of(FRESH_ASSESSED_AT, EVENT_TIME.plusSeconds(5), EVENT_TIME.minusMillis(5));

        for (MarketFeaturesSnapshot snapshot : snapshots) {
            for (Instant assessedAt : instants) {
                QualityAssessment first = resolver.resolve(snapshot, assessedAt, POLICY);
                QualityAssessment second = new QualityAssessmentResolver().resolve(snapshot, assessedAt, POLICY);
                assertEquals(first, second, "deterministic for equal inputs");
                assertEquals(first.reasonCodes().size(), new HashSet<>(first.reasonCodes()).size(), "no duplicates");
                assertEquals(first.reasonCodes(), first.interpretationQuality().reasonCodes(),
                        "overall reasons and InterpretationQuality reasons are the same list");
            }
        }
    }

    @Test
    void resolverFailsFastOnMissingInputs() {
        MarketFeaturesSnapshot snapshot = allComputed();
        assertThrows(NullPointerException.class, () -> resolver.resolve(null, FRESH_ASSESSED_AT, POLICY));
        assertThrows(NullPointerException.class, () -> resolver.resolve(snapshot, null, POLICY));
        assertThrows(NullPointerException.class, () -> resolver.resolve(snapshot, FRESH_ASSESSED_AT, null));
        assertThrows(NullPointerException.class,
                () -> resolver.resolve(snapshot.toBuilder().quality(null).build(), FRESH_ASSESSED_AT, POLICY));
        assertThrows(NullPointerException.class,
                () -> resolver.resolve(snapshot.toBuilder().evaluationTs(null).build(), FRESH_ASSESSED_AT, POLICY));
        assertThrows(NullPointerException.class,
                () -> resolver.resolve(snapshot.toBuilder().computedAt(null).build(), FRESH_ASSESSED_AT, POLICY));
    }
}

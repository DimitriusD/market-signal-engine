package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.COMPUTED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.EVENT_TIME;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.FRESH_ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.POLICY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Structural invariants of {@link QualityAssessment} and {@link FeatureGroupId}. */
class QualityAssessmentInvariantsTest {

    private static final TimingAssessment FRESH = new TimingAssessmentResolver()
            .resolve(FRESH_ASSESSED_AT, EVENT_TIME, COMPUTED_AT, POLICY);
    private static final TimingAssessment STALE = new TimingAssessmentResolver()
            .resolve(EVENT_TIME.plusSeconds(30), EVENT_TIME, COMPUTED_AT, POLICY);
    private static final HorizonEligibilities ALL_ELIGIBLE = HorizonEligibilities.uniform(HorizonEligibility.eligible());
    private static final HorizonEligibilities NONE_ELIGIBLE = HorizonEligibilities.uniform(
            HorizonEligibility.unavailable(List.of(QualityReasonCodes.WINDOW_NOT_COMPUTED)));
    private static final HorizonEligibilities PARTIAL = HorizonEligibilities.of(
            HorizonEligibility.eligible(), HorizonEligibility.eligible(),
            HorizonEligibility.warmingUp(List.of(QualityReasonCodes.WINDOW_WARMING_UP)),
            HorizonEligibility.warmingUp(List.of(QualityReasonCodes.WINDOW_WARMING_UP)));

    @Test
    void eligibleForTradingRequiresAnEligibleHorizonFreshTimingAndUsableSource() {
        InterpretationQuality degradedEligible = InterpretationQuality.degraded(true, List.of());

        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.DEGRADED, degradedEligible, FRESH, NONE_ELIGIBLE, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.DEGRADED, degradedEligible, STALE, ALL_ELIGIBLE, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.UNSAFE, degradedEligible, FRESH, ALL_ELIGIBLE, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.NO_DATA, degradedEligible, FRESH, ALL_ELIGIBLE, List.of(), false));

        QualityAssessment legal = new QualityAssessment(
                FeatureQualityStatus.DEGRADED, degradedEligible, FRESH, PARTIAL, List.of(), false);
        assertTrue(legal.eligibleForTrading());
    }

    @Test
    void okRequiresAllEligibleNoFailedGroupsAndNoFutureEvent() {
        InterpretationQuality ok = InterpretationQuality.ok(List.of());

        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.OK, ok, FRESH, PARTIAL, List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.OK, ok, FRESH, ALL_ELIGIBLE, List.of(FeatureGroupId.BBO), false));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.OK, ok, FRESH, ALL_ELIGIBLE, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(
                FeatureQualityStatus.OK, ok, STALE, ALL_ELIGIBLE, List.of(), false));

        QualityAssessment legal = new QualityAssessment(
                FeatureQualityStatus.OK, ok, FRESH, ALL_ELIGIBLE, List.of(), false);
        assertEquals(List.of(), legal.failedFeatureGroups());
    }

    @Test
    void nonEligibleVerdictsAreAlwaysLegalToRecordButCollectionsMustBeClean() {
        InterpretationQuality blocked = InterpretationQuality.blocked(List.of(QualityReasonCodes.SOURCE_QUALITY_UNSAFE));

        QualityAssessment assessment = new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked, STALE, ALL_ELIGIBLE,
                List.of(FeatureGroupId.BBO), true);
        assertFalse(assessment.eligibleForTrading());

        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked,
                STALE, ALL_ELIGIBLE, List.of(FeatureGroupId.BBO, FeatureGroupId.BBO), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked,
                STALE, ALL_ELIGIBLE, Arrays.asList(FeatureGroupId.BBO, null), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(null, blocked, STALE, ALL_ELIGIBLE,
                List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(FeatureQualityStatus.UNSAFE, null,
                STALE, ALL_ELIGIBLE, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked,
                null, ALL_ELIGIBLE, List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked,
                STALE, null, List.of(), true));
    }

    @Test
    void overallReasonCodesHaveExactlyOneSourceOfTruth() {
        List<ReasonCode> reasons = List.of(QualityReasonCodes.SOURCE_QUALITY_UNSAFE, QualityReasonCodes.FEATURE_GROUP_FAILURE);
        InterpretationQuality blocked = InterpretationQuality.blocked(reasons);
        QualityAssessment assessment = new QualityAssessment(FeatureQualityStatus.UNSAFE, blocked, STALE, ALL_ELIGIBLE,
                List.of(FeatureGroupId.BBO), false);

        // The accessor reads through to InterpretationQuality: same list, same order, immutable.
        assertSame(assessment.interpretationQuality().reasonCodes(), assessment.reasonCodes());
        assertEquals(reasons, assessment.reasonCodes());
        assertThrows(UnsupportedOperationException.class, () -> assessment.reasonCodes().add(ReasonCode.of("X")));

        // No record component can hold a second, divergent copy of the overall reasons.
        List<String> components = Arrays.stream(QualityAssessment.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertFalse(components.contains("reasonCodes"),
                "QualityAssessment must not store overall reason codes next to interpretationQuality: " + components);
        assertEquals(List.of("sourceQualityStatus", "interpretationQuality", "timing", "horizonEligibilities",
                "failedFeatureGroups", "futureEventDetected"), components);
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        List<FeatureGroupId> groups = new ArrayList<>(List.of(FeatureGroupId.BBO));
        List<ReasonCode> reasons = new ArrayList<>(List.of(QualityReasonCodes.FEATURE_GROUP_FAILURE));
        QualityAssessment assessment = new QualityAssessment(FeatureQualityStatus.DEGRADED,
                InterpretationQuality.degraded(true, reasons), FRESH, ALL_ELIGIBLE, groups, false);

        groups.add(FeatureGroupId.ORDER_BOOK);
        reasons.add(QualityReasonCodes.SOURCE_QUALITY_DEGRADED);

        assertEquals(List.of(FeatureGroupId.BBO), assessment.failedFeatureGroups());
        assertEquals(List.of(QualityReasonCodes.FEATURE_GROUP_FAILURE), assessment.reasonCodes());
        assertThrows(UnsupportedOperationException.class, () -> assessment.failedFeatureGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessment.reasonCodes().clear());
    }

    @Test
    void featureGroupIdIsTypedNonBlankAndLossless() {
        assertThrows(NullPointerException.class, () -> FeatureGroupId.of(null));
        assertThrows(IllegalArgumentException.class, () -> FeatureGroupId.of(" "));
        assertEquals(FeatureGroupId.TRADE_FLOW, FeatureGroupId.of("trade-flow"));
        assertTrue(FeatureGroupId.TRADE_FLOW.isKnown());
        assertTrue(FeatureGroupId.of("order-book").isKnown());
        assertFalse(FeatureGroupId.of("Trade-Flow").isKnown(), "no case folding: drift stays visible");
        assertEquals("trade-flow", FeatureGroupId.TRADE_FLOW.toString());
        assertEquals(List.of(FeatureGroupId.BBO, FeatureGroupId.ORDER_BOOK, FeatureGroupId.TRADE_FLOW,
                FeatureGroupId.SHORT_TERM_REGIME), FeatureGroupId.KNOWN);

        List<FeatureGroupId> failed = FeatureGroupId.failedGroupsOf(FeatureDiagnostics.builder()
                .failedFeatureGroups(List.of("short-term-regime", "new-group", "short-term-regime"))
                .totalFeatureGroups(5)
                .build());
        assertEquals(List.of(FeatureGroupId.SHORT_TERM_REGIME, FeatureGroupId.of("new-group")), failed);
        assertThrows(UnsupportedOperationException.class, () -> failed.add(FeatureGroupId.BBO));
        assertEquals(List.of(), FeatureGroupId.failedGroupsOf(null));
        assertThrows(IllegalArgumentException.class, () -> FeatureGroupId.failedGroupsOf(
                FeatureDiagnostics.builder().failedFeatureGroups(List.of("")).totalFeatureGroups(1).build()));
    }
}

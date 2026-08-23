package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.active;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.allEligible;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.bd;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.tradeFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.uniform;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.window;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.withWindow;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_ACTIVITY_COUNTS_INVALID;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_ACTIVITY_COUNTS_MISSING;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_BEARISH_IMBALANCE;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_BULLISH_IMBALANCE;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_IMBALANCE_MISSING;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_IMBALANCE_OUT_OF_RANGE;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_INSUFFICIENT_ACTIVITY;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_NEUTRAL_IMBALANCE;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowReasonCodes.FLOW_WINDOW_MISSING;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Flow V1 evaluation: eligibility precedence, missing / invalid / low-activity / unknown-side /
 * neutral as distinct states, inclusive directional boundaries, strength formula, determinism and
 * horizon-specific verdicts under one explicit fixture policy.
 */
class FlowAssessmentEvaluatorTest {

    private static final BigDecimal EPS = bd("0.000001");

    private final FlowAssessmentEvaluator evaluator = new FlowAssessmentEvaluator();

    // ------------------------------------------------------------------ shape

    @Test
    void returnsExactlyFourFlowAssessmentsInCanonicalOrder() {
        MarketFeaturesSnapshot snapshot = snapshot(uniform(active("0.00")));

        FlowAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(assessments.asMap().keySet()));
        assertEquals(4, assessments.asList().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceDimension.FLOW, evidence.dimension());
            assertEquals(evidence, evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon),
                    "per-horizon entry point agrees with the aggregate");
        }
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot(uniform(active("0.00")));
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, POLICY, null));
    }

    @Test
    void qualityAssessmentOfAnotherSnapshotIsRejected() {
        // snapshot B: UNSAFE source with a strongly bullish flow; assessment A: all-ELIGIBLE, source OK.
        // Pairing them must fail fast instead of minting bullish evidence the quality layer never cleared.
        MarketFeaturesSnapshot unsafe = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE).qualityReasons(List.of("BOOK_UNTRUSTED")).build())
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(unsafe, allEligible(), POLICY));
        assertTrue(ex.getMessage().contains("sourceQualityStatus"), ex.getMessage());
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());

        // different market as-of / computed instants
        MarketFeaturesSnapshot otherAsOf = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90"))).evaluationTs(FlowFixtures.EVENT_TIME.plusMillis(7)).build();
        assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> evaluator.evaluate(otherAsOf, allEligible(), POLICY, H5S))
                .getMessage().contains("timing.sourceEvaluationAt"));
        MarketFeaturesSnapshot otherComputedAt = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90"))).computedAt(FlowFixtures.COMPUTED_AT.plusMillis(7)).build();
        assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> evaluator.evaluate(otherComputedAt, allEligible(), POLICY, H5S))
                .getMessage().contains("timing.sourceComputedAt"));

        // upstream future-event flag and failed feature groups must agree as well
        MarketFeaturesSnapshot futureEvent = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder().futureEventDetected(true).build())
                .build();
        assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> evaluator.evaluate(futureEvent, allEligible(), POLICY))
                .getMessage().contains("futureEventDetected"));
        MarketFeaturesSnapshot failedBbo = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90")))
                .diagnostics(FeatureDiagnostics.builder().failedFeatureGroups(List.of("bbo")).totalFeatureGroups(4).build())
                .build();
        assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> evaluator.evaluate(failedBbo, allEligible(), POLICY))
                .getMessage().contains("failedFeatureGroups"));

        // the matching pair from the real Stage 3 resolver passes
        assertEquals(InterpretationDirection.BULLISH,
                evaluator.evaluate(snapshot(uniform(active("0.90"))), quality(snapshot(uniform(active("0.90")))), POLICY, H5S)
                        .direction());
    }

    @Test
    void degradedSnapshotsWithDifferentEligibilityCausesAreNotInterchangeable() {
        // A: DEGRADED because the book is incomplete — trade-flow horizons stay ELIGIBLE.
        MarketFeaturesSnapshot bookDegraded = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).incompleteBook(true)
                        .qualityReasons(List.of("INCOMPLETE_BOOK")).build())
                .build();
        // B: DEGRADED because trades are stale — every trade-flow horizon is UNTRUSTED.
        MarketFeaturesSnapshot staleFlow = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.90")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES")).build())
                .build();
        QualityAssessment assessmentA = QUALITY_RESOLVER.resolve(bookDegraded, ASSESSED_AT, QUALITY_POLICY);
        QualityAssessment assessmentB = QUALITY_RESOLVER.resolve(staleFlow, ASSESSED_AT, QUALITY_POLICY);

        // Precondition: every fact the pre-eligibility guard compares is identical between A and B...
        assertEquals(assessmentA.sourceQualityStatus(), assessmentB.sourceQualityStatus());
        assertEquals(assessmentA.futureEventDetected(), assessmentB.futureEventDetected());
        assertEquals(assessmentA.timing().sourceEvaluationAt(), assessmentB.timing().sourceEvaluationAt());
        assertEquals(assessmentA.timing().sourceComputedAt(), assessmentB.timing().sourceComputedAt());
        assertEquals(assessmentA.failedFeatureGroups(), assessmentB.failedFeatureGroups());
        // ...only the per-horizon eligibility separates them.
        assertTrue(assessmentA.horizonEligibilities().allEligible());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(HorizonEligibilityStatus.UNTRUSTED, assessmentB.horizonEligibilities().statusOf(horizon));
        }

        // Snapshot B (stale flow values) + assessment A (ELIGIBLE horizons) must fail fast, not go bullish.
        IllegalArgumentException aggregate = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(staleFlow, assessmentA, POLICY));
        assertTrue(aggregate.getMessage().contains("horizonEligibilities"), aggregate.getMessage());
        assertTrue(aggregate.getMessage().contains("not produced from this snapshot"), aggregate.getMessage());
        IllegalArgumentException perHorizon = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(staleFlow, assessmentA, POLICY, H5S));
        assertTrue(perHorizon.getMessage().contains("horizonEligibilities"), perHorizon.getMessage());

        // The honest pairs still evaluate: B + its own assessment projects UNTRUSTED everywhere...
        FlowAssessments staleAssessments = evaluator.evaluate(staleFlow, assessmentB, POLICY);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = staleAssessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(QualityReasonCodes.STALE_TRADES), evidence.reasonCodes());
        }
        // ...and A + its own assessment reads the same windows as bullish.
        assertEquals(InterpretationDirection.BULLISH,
                evaluator.evaluate(bookDegraded, assessmentA, POLICY, H5S).direction());
    }

    @Test
    void resultCollectionsAreImmutable() {
        MarketFeaturesSnapshot snapshot = snapshot(uniform(active("0.50")));
        FlowAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> assessments.of(H5S).reasonCodes().add(FLOW_NEUTRAL_IMBALANCE));
    }

    // ------------------------------------------------------------------ eligibility precedence

    static Stream<Arguments> nonEligibleProjections() {
        // corrupt, strongly directional values: a non-ELIGIBLE horizon must never interpret them
        TradeFlowFeature corrupt = uniform(window("5.00", -10, 500, 900));
        return Stream.of(
                Arguments.of("warm-up, windows not yet computed",
                        SignalRuleTestSupport.tradableFeaturesBuilder()
                                .tradeFlow(tradeFlow(null, null, null, null))
                                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                                        .status(FeatureQualityStatus.DEGRADED).warmingUp(true)
                                        .qualityReasons(List.of("WARMING_UP")).build())
                                .build(),
                        EvidenceAvailabilityStatus.UNAVAILABLE, List.of(QualityReasonCodes.WINDOW_WARMING_UP)),
                Arguments.of("trade-flow group absent",
                        snapshot(null),
                        EvidenceAvailabilityStatus.UNAVAILABLE, List.of(QualityReasonCodes.TRADE_FLOW_GROUP_ABSENT)),
                Arguments.of("windows not computed",
                        snapshot(tradeFlow(null, null, null, null)),
                        EvidenceAvailabilityStatus.UNAVAILABLE, List.of(QualityReasonCodes.WINDOW_NOT_COMPUTED)),
                Arguments.of("stale trades over corrupt directional values",
                        SignalRuleTestSupport.tradableFeaturesBuilder()
                                .tradeFlow(corrupt)
                                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                                        .status(FeatureQualityStatus.DEGRADED).staleTrades(true)
                                        .qualityReasons(List.of("STALE_TRADES")).build())
                                .build(),
                        EvidenceAvailabilityStatus.UNTRUSTED, List.of(QualityReasonCodes.STALE_TRADES)),
                Arguments.of("failed trade-flow calculator over corrupt directional values",
                        SignalRuleTestSupport.tradableFeaturesBuilder()
                                .tradeFlow(corrupt)
                                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                                        .status(FeatureQualityStatus.DEGRADED)
                                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                                .diagnostics(FeatureDiagnostics.builder()
                                        .failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                                .build(),
                        EvidenceAvailabilityStatus.FAILED, List.of(QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonEligibleProjections")
    void nonEligibleHorizonIsProjectedWithoutReadingFeatureValues(String label,
                                                                  MarketFeaturesSnapshot snapshot,
                                                                  EvidenceAvailabilityStatus expected,
                                                                  List<ReasonCode> reasons) {
        QualityAssessment qa = QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);

        FlowAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(expected, evidence.availabilityStatus(), label + ": " + horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction(), horizon.wireValue());
            assertNull(evidence.evidenceStrength(), horizon.wireValue());
            assertEquals(reasons, evidence.reasonCodes(), "eligibility reasons are kept verbatim: " + horizon);
            assertFalse(evidence.isAvailable());
        }
    }

    @Test
    void unknownEligibilityProjectsToUnknownAndEligibleIsNeverProjected() {
        // Stage 3 never emits UNKNOWN (fail-closed vocabulary), so the mapping is pinned at unit level
        assertEquals(EvidenceAvailabilityStatus.UNKNOWN,
                FlowAssessmentEvaluator.projectEligibility(
                        HorizonEligibility.unknown(List.of(ReasonCode.of("SOME_UNKNOWN_STATE")))));
        assertThrows(IllegalArgumentException.class,
                () -> FlowAssessmentEvaluator.projectEligibility(HorizonEligibility.eligible()));
    }

    @Test
    void eligibilityIsDecidedPerHorizonIndependently() {
        // history gap: short windows computed and ELIGIBLE, uncovered long windows UNTRUSTED — one snapshot
        MarketFeaturesSnapshot gap = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(tradeFlow(active("0.50"), active("0.50"), null, null))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(gap, ASSESSED_AT, QUALITY_POLICY);

        FlowAssessments assessments = evaluator.evaluate(gap, qa, POLICY);

        assertEquals(InterpretationDirection.NEUTRAL, assessments.of(H1S).direction(), "0.50 < 1S bullish 0.60");
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H5S).direction(), "0.50 >= 5S bullish 0.30");
        for (MarketHorizon horizon : List.of(H15S, H60S)) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.TRADE_HISTORY_GAP), evidence.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ missing input

    @Test
    void absentWindowIsProjectedPerHorizonWhileComputedOnesEvaluate() {
        MarketFeaturesSnapshot noWindow15s = snapshot(tradeFlow(active("0.50"), active("0.50"), null, active("0.50")));
        QualityAssessment qa = quality(noWindow15s);

        FlowAssessments assessments = evaluator.evaluate(noWindow15s, qa, POLICY);

        EvidenceAssessment missing15s = assessments.of(H15S);
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, missing15s.availabilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, missing15s.direction(), "an absent window is not zero");
        assertNull(missing15s.evidenceStrength());
        assertEquals(List.of(QualityReasonCodes.WINDOW_NOT_COMPUTED), missing15s.reasonCodes());
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H5S).direction());
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H60S).direction(), "0.50 >= 60S bullish 0.15");
    }

    @Test
    void nullImbalanceIsUnavailableNotNeutral() {
        // the window is computed (tradeIntensity + counts present) so Stage 3 says ELIGIBLE, but the
        // directional feature itself is absent
        TradeFlowWindow noImbalance = window(null, 200, 150, 0);
        MarketFeaturesSnapshot snapshot = snapshot(uniform(noImbalance));
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().allEligible(), "precondition: Stage 3 eligible");

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(FLOW_IMBALANCE_MISSING), evidence.reasonCodes());
        }
    }

    @Test
    void nullActivityCountIsUnavailableAndBothMissingReasonsAreReportedTogether() {
        TradeFlowWindow noCounts = TradeFlowWindow.builder().signedFlowImbalance(bd("0.90")).tradeIntensity(bd("1.0")).build();
        TradeFlowWindow noUnknownSide = TradeFlowWindow.builder().signedFlowImbalance(bd("0.90"))
                .tradeCount(200).aggressiveTradeCount(150).build();
        TradeFlowWindow nothingUsable = TradeFlowWindow.builder().tradeIntensity(bd("1.0")).build();
        MarketFeaturesSnapshot snapshot = snapshot(tradeFlow(active("0.50"), noUnknownSide, noCounts, nothingUsable));
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().allEligible(), "precondition: Stage 3 eligible");

        assertEquals(List.of(FLOW_ACTIVITY_COUNTS_MISSING), evaluator.evaluate(snapshot, qa, POLICY, H5S).reasonCodes());
        assertEquals(List.of(FLOW_ACTIVITY_COUNTS_MISSING), evaluator.evaluate(snapshot, qa, POLICY, H15S).reasonCodes());
        EvidenceAssessment h60 = evaluator.evaluate(snapshot, qa, POLICY, H60S);
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, h60.availabilityStatus());
        assertEquals(List.of(FLOW_IMBALANCE_MISSING, FLOW_ACTIVITY_COUNTS_MISSING), h60.reasonCodes());
        assertEquals(InterpretationDirection.UNKNOWN, h60.direction(), "a 0.90 imbalance without counts is not bullish");
    }

    // ------------------------------------------------------------------ invalid input

    @ParameterizedTest
    @CsvSource({"1.000001", "-1.000001", "5.00", "-2"})
    void imbalanceOutsideMinusOneToOneIsUntrusted(String imbalance) {
        MarketFeaturesSnapshot snapshot = snapshot(uniform(active(imbalance)));
        QualityAssessment qa = quality(snapshot);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(FLOW_IMBALANCE_OUT_OF_RANGE), evidence.reasonCodes());
        }
    }

    @Test
    void exactlyMinusOneAndPlusOneAreValidImbalances() {
        MarketFeaturesSnapshot snapshot = snapshot(tradeFlow(active("1"), active("-1"), active("1.000000"), active("-1.0")));
        QualityAssessment qa = quality(snapshot);

        assertEquals(InterpretationDirection.BULLISH, evaluator.evaluate(snapshot, qa, POLICY, H1S).direction());
        assertEquals(EvidenceStrength.MAX, evaluator.evaluate(snapshot, qa, POLICY, H1S).evidenceStrength());
        assertEquals(InterpretationDirection.BEARISH, evaluator.evaluate(snapshot, qa, POLICY, H5S).direction());
        assertEquals(EvidenceStrength.MAX, evaluator.evaluate(snapshot, qa, POLICY, H5S).evidenceStrength());
        assertEquals(InterpretationDirection.BULLISH, evaluator.evaluate(snapshot, qa, POLICY, H15S).direction());
        assertEquals(InterpretationDirection.BEARISH, evaluator.evaluate(snapshot, qa, POLICY, H60S).direction());
    }

    static Stream<Arguments> invalidCounts() {
        return Stream.of(
                // tradeCount, aggressive, unknownSide, validQty (null = absent)
                Arguments.of("negative tradeCount", -1, 0, 0, null),
                Arguments.of("negative aggressiveTradeCount", 200, -1, 0, null),
                Arguments.of("negative unknownSideCount", 200, 150, -1, null),
                Arguments.of("aggressive > tradeCount", 200, 201, 0, null),
                Arguments.of("unknownSide > tradeCount", 200, 0, 201, null),
                Arguments.of("aggressive + unknownSide > tradeCount", 200, 150, 51, null),
                Arguments.of("negative validQtyTradeCount", 200, 150, 0, -1),
                Arguments.of("validQty > tradeCount", 200, 150, 0, 201),
                Arguments.of("aggressive > validQty", 200, 150, 0, 149));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCounts")
    void negativeOrContradictoryCountsAreUntrusted(String label, int tradeCount, int aggressive, int unknownSide,
                                                   Integer validQty) {
        TradeFlowWindow invalid = TradeFlowWindow.builder()
                .signedFlowImbalance(bd("0.90"))
                .tradeCount(tradeCount)
                .aggressiveTradeCount(aggressive)
                .unknownSideCount(unknownSide)
                .validQtyTradeCount(validQty)
                .tradeIntensity(bd("1.0"))
                .build();
        MarketFeaturesSnapshot snapshot = snapshot(withWindow(H5S, invalid));

        EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, H5S);

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus(), label);
        assertEquals(InterpretationDirection.UNKNOWN, evidence.direction(), "no bullish evidence from corrupt counts");
        assertNull(evidence.evidenceStrength());
        assertEquals(List.of(FLOW_ACTIVITY_COUNTS_INVALID), evidence.reasonCodes());
    }

    @Test
    void validCountEdgeCasesAreAccepted() {
        assertFalse(FlowAssessmentEvaluator.countsInvalid(200, 150, 50, null), "aggressive + unknown == tradeCount");
        assertFalse(FlowAssessmentEvaluator.countsInvalid(200, 150, 0, 150), "aggressive == validQty");
        assertFalse(FlowAssessmentEvaluator.countsInvalid(200, 200, 0, 200), "all trades aggressive");
        assertFalse(FlowAssessmentEvaluator.countsInvalid(0, 0, 0, 0), "empty window is consistent");
        assertTrue(FlowAssessmentEvaluator.countsInvalid(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, null), "no int overflow");
    }

    @Test
    void outOfRangeImbalanceAndInvalidCountsAreReportedTogether() {
        TradeFlowWindow doubleInvalid = window("3.00", 200, 250, 0);
        MarketFeaturesSnapshot snapshot = snapshot(withWindow(H15S, doubleInvalid));

        EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, H15S);

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus());
        assertEquals(List.of(FLOW_IMBALANCE_OUT_OF_RANGE, FLOW_ACTIVITY_COUNTS_INVALID), evidence.reasonCodes());
    }

    // ------------------------------------------------------------------ activity gate

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void lowActivityIsAvailableUnknownNotNeutral(MarketHorizon horizon) {
        FlowHorizonPolicy p = POLICY.of(horizon);
        // strongly bullish imbalance, one trade short of the minimum
        TradeFlowWindow tooFewTrades = window("0.90", p.minTradeCount() - 1, Math.min(p.minTradeCount() - 1, p.minAggressiveTradeCount()), 0);
        // enough trades, one aggressive trade short of the minimum
        TradeFlowWindow tooFewAggressive = window("0.90", p.minTradeCount(), Math.max(0, p.minAggressiveTradeCount() - 1), 0);

        for (TradeFlowWindow w : List.of(tooFewTrades, tooFewAggressive)) {
            MarketFeaturesSnapshot snapshot = snapshot(withWindow(horizon, w));
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, horizon);

            assertEquals(EvidenceAvailabilityStatus.AVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction(), "low activity is UNKNOWN, not NEUTRAL");
            assertNull(evidence.evidenceStrength(), "no strength could be assessed");
            assertEquals(List.of(FLOW_INSUFFICIENT_ACTIVITY), evidence.reasonCodes());
            assertTrue(evidence.isAvailable());
        }
    }

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void exactlyMinimumActivityPassesTheGate(MarketHorizon horizon) {
        FlowHorizonPolicy p = POLICY.of(horizon);
        TradeFlowWindow atMinimum = window("0.90", p.minTradeCount(), p.minAggressiveTradeCount(), 0);
        MarketFeaturesSnapshot snapshot = snapshot(withWindow(horizon, atMinimum));

        EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, horizon);

        assertEquals(InterpretationDirection.BULLISH, evidence.direction(), horizon.wireValue());
        assertEquals(List.of(FLOW_BULLISH_IMBALANCE), evidence.reasonCodes());
    }

    @Test
    void activityGateIsCheckedAfterValidityAndBeforeUnknownSide() {
        // 1S: minTradeCount 3 — a single trade with a corrupt count is UNTRUSTED, not INSUFFICIENT_ACTIVITY
        MarketFeaturesSnapshot corrupt = snapshot(withWindow(H1S, window("0.90", 1, 2, 0)));
        assertEquals(List.of(FLOW_ACTIVITY_COUNTS_INVALID), evaluator.evaluate(corrupt, allEligible(), POLICY, H1S).reasonCodes());

        // 1S: two trades, both unknown-side (ratio 1.0 > 0.5) — activity gate wins, the ratio is not even judged
        MarketFeaturesSnapshot quiet = snapshot(withWindow(H1S, window("0.90", 2, 0, 2)));
        assertEquals(List.of(FLOW_INSUFFICIENT_ACTIVITY), evaluator.evaluate(quiet, allEligible(), POLICY, H1S).reasonCodes());
    }

    // ------------------------------------------------------------------ unknown-side gate

    static Stream<Arguments> unknownSideBoundaries() {
        return Stream.of(
                // horizon, tradeCount, aggressive at max, unknown at max (ratio == maxUnknownSideRatio)
                Arguments.of(H1S, 10, 5, 5),      // 0.50
                Arguments.of(H5S, 20, 15, 5),     // 0.25
                Arguments.of(H15S, 50, 40, 10),   // 0.20
                Arguments.of(H60S, 200, 180, 20)); // 0.10
    }

    @ParameterizedTest
    @MethodSource("unknownSideBoundaries")
    void unknownSideRatioExactlyAtMaximumIsTrustedAndAboveIsUntrusted(MarketHorizon horizon, int tradeCount,
                                                                        int aggressive, int unknownAtMax) {
        MarketFeaturesSnapshot atMax = snapshot(withWindow(horizon, window("0.90", tradeCount, aggressive, unknownAtMax)));
        EvidenceAssessment trusted = evaluator.evaluate(atMax, allEligible(), POLICY, horizon);
        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, trusted.availabilityStatus(), horizon.wireValue());
        assertEquals(InterpretationDirection.BULLISH, trusted.direction());
        assertEquals(List.of(FLOW_BULLISH_IMBALANCE), trusted.reasonCodes());

        MarketFeaturesSnapshot aboveMax = snapshot(withWindow(horizon, window("0.90", tradeCount, aggressive - 1, unknownAtMax + 1)));
        EvidenceAssessment untrusted = evaluator.evaluate(aboveMax, allEligible(), POLICY, horizon);
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, untrusted.availabilityStatus(), horizon.wireValue());
        assertEquals(InterpretationDirection.UNKNOWN, untrusted.direction());
        assertNull(untrusted.evidenceStrength());
        assertEquals(List.of(FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED), untrusted.reasonCodes());
    }

    @Test
    void unknownSideRatioIsDeterministicBigDecimalWithExactBoundarySemantics() {
        // non-terminating 1/3 vs a policy just below / at / above it: rounding never moves a value across the boundary
        assertTrue(FlowAssessmentEvaluator.unknownSideRatio(1, 3, bd("0.33")).compareTo(bd("0.33")) > 0);
        assertTrue(FlowAssessmentEvaluator.unknownSideRatio(1, 3, bd("0.333333")).compareTo(bd("0.333333")) > 0);
        assertTrue(FlowAssessmentEvaluator.unknownSideRatio(1, 3, bd("0.34")).compareTo(bd("0.34")) < 0);
        // exact boundary with a differently scaled policy value
        assertEquals(0, FlowAssessmentEvaluator.unknownSideRatio(1, 4, bd("0.250000000")).compareTo(bd("0.25")));
        assertEquals(0, FlowAssessmentEvaluator.unknownSideRatio(0, 7, BigDecimal.ZERO).signum());
        assertEquals(Math.max(FlowAssessmentEvaluator.MIN_RATIO_SCALE, 9),
                FlowAssessmentEvaluator.unknownSideRatio(1, 4, bd("0.250000000")).scale(), "scale follows the policy");
        assertThrows(IllegalArgumentException.class, () -> FlowAssessmentEvaluator.unknownSideRatio(0, 0, bd("0.25")));
    }

    @Test
    void zeroMaxUnknownSideRatioTrustsOnlyFullyKnownSides() {
        FlowHorizonPolicy strict = FlowHorizonPolicy.of(H5S, bd("0.30"), bd("-0.30"), 10, 5, BigDecimal.ZERO);
        FlowAssessmentPolicy policy = FlowAssessmentPolicy.of("flow-strict-v1", FlowFixtures.P1S, strict, FlowFixtures.P15S, FlowFixtures.P60S);

        assertEquals(InterpretationDirection.BULLISH,
                evaluator.evaluate(snapshot(withWindow(H5S, window("0.90", 50, 50, 0))), allEligible(), policy, H5S).direction());
        assertEquals(List.of(FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED),
                evaluator.evaluate(snapshot(withWindow(H5S, window("0.90", 50, 49, 1))), allEligible(), policy, H5S).reasonCodes());
    }

    // ------------------------------------------------------------------ direction and strength

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void bullishThresholdIsInclusive(MarketHorizon horizon) {
        BigDecimal threshold = POLICY.of(horizon).bullishImbalanceThreshold();

        assertDirection(horizon, threshold.subtract(EPS), InterpretationDirection.NEUTRAL, FLOW_NEUTRAL_IMBALANCE);
        assertDirection(horizon, threshold, InterpretationDirection.BULLISH, FLOW_BULLISH_IMBALANCE);
        assertDirection(horizon, threshold.add(EPS), InterpretationDirection.BULLISH, FLOW_BULLISH_IMBALANCE);
    }

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void bearishThresholdIsInclusive(MarketHorizon horizon) {
        BigDecimal threshold = POLICY.of(horizon).bearishImbalanceThreshold();

        assertDirection(horizon, threshold.add(EPS), InterpretationDirection.NEUTRAL, FLOW_NEUTRAL_IMBALANCE);
        assertDirection(horizon, threshold, InterpretationDirection.BEARISH, FLOW_BEARISH_IMBALANCE);
        assertDirection(horizon, threshold.subtract(EPS), InterpretationDirection.BEARISH, FLOW_BEARISH_IMBALANCE);
    }

    private void assertDirection(MarketHorizon horizon, BigDecimal imbalance, InterpretationDirection expected,
                                 ReasonCode expectedReason) {
        MarketFeaturesSnapshot snapshot = snapshot(withWindow(horizon, active(imbalance.toPlainString())));
        EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, horizon);

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
        assertEquals(expected, evidence.direction(), horizon.wireValue() + " @ " + imbalance.toPlainString());
        assertEquals(List.of(expectedReason), evidence.reasonCodes());
        assertNotNull(evidence.evidenceStrength(), "an interpreted reading always carries a strength");
        if (expected == InterpretationDirection.NEUTRAL) {
            assertEquals(EvidenceStrength.MIN, evidence.evidenceStrength(), "neutral strength is a real 0");
        } else {
            assertEquals(EvidenceStrength.of(imbalance.abs()), evidence.evidenceStrength(), "strength = |imbalance|");
        }
    }

    @Test
    void realZeroImbalanceWithEnoughActivityIsNeutralWithZeroStrength() {
        MarketFeaturesSnapshot snapshot = snapshot(uniform(active("0.00")));
        QualityAssessment qa = quality(snapshot);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(InterpretationDirection.NEUTRAL, evidence.direction(), horizon.wireValue());
            assertEquals(EvidenceStrength.MIN, evidence.evidenceStrength());
            assertEquals("0", evidence.evidenceStrength().toPlainString());
            assertEquals(List.of(FLOW_NEUTRAL_IMBALANCE), evidence.reasonCodes());
        }
    }

    @ParameterizedTest
    @CsvSource({"0.35, BULLISH, 0.35", "-0.4200, BEARISH, 0.42", "1, BULLISH, 1", "-0.999999, BEARISH, 0.999999"})
    void strengthIsAbsoluteImbalanceForDirectionalEvidence(String imbalance, InterpretationDirection expected, String strength) {
        MarketFeaturesSnapshot snapshot = snapshot(withWindow(H5S, active(imbalance)));

        EvidenceAssessment evidence = evaluator.evaluate(snapshot, allEligible(), POLICY, H5S);

        assertEquals(expected, evidence.direction());
        assertEquals(EvidenceStrength.of(strength), evidence.evidenceStrength());
        assertEquals(strength, evidence.evidenceStrength().toPlainString(), "normalised plain decimal");
    }

    @Test
    void neverProducesMixed() {
        for (String imbalance : List.of("-1", "-0.5", "-0.15", "0", "0.15", "0.5", "1")) {
            MarketFeaturesSnapshot snapshot = snapshot(uniform(active(imbalance)));
            for (EvidenceAssessment evidence : evaluator.evaluate(snapshot, quality(snapshot), POLICY).asList()) {
                assertTrue(evidence.direction() != InterpretationDirection.MIXED, imbalance);
            }
        }
    }

    // ------------------------------------------------------------------ determinism / horizon specificity

    @Test
    void sameInputAndPolicyGiveValueEqualResults() {
        MarketFeaturesSnapshot snapshot = snapshot(tradeFlow(active("0.61"), active("-0.31"), active("0.21"), window("0.10", 50, 40, 5)));
        QualityAssessment qa = quality(snapshot);

        FlowAssessments first = evaluator.evaluate(snapshot, qa, POLICY);
        FlowAssessments second = new FlowAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
        assertEquals(List.of(FLOW_BULLISH_IMBALANCE), first.of(H1S).reasonCodes());
        assertEquals(List.of(FLOW_BEARISH_IMBALANCE), first.of(H5S).reasonCodes());
        assertEquals(List.of(FLOW_BULLISH_IMBALANCE), first.of(H15S).reasonCodes());
        assertEquals(List.of(FLOW_INSUFFICIENT_ACTIVITY), first.of(H60S).reasonCodes());
    }

    @Test
    void theSameFlowReadsDifferentlyPerHorizonUnderHorizonSpecificPolicy() {
        // one identical window everywhere: 0.25 imbalance, 50 trades, 40 aggressive, 5 unknown-side (ratio 0.10)
        MarketFeaturesSnapshot snapshot = snapshot(uniform(window("0.25", 50, 40, 5)));
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().allEligible());

        FlowAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(InterpretationDirection.NEUTRAL, assessments.of(H1S).direction(), "0.25 < 0.60 bullish");
        assertEquals(InterpretationDirection.NEUTRAL, assessments.of(H5S).direction(), "0.25 < 0.30 bullish");
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H15S).direction(), "0.25 >= 0.20 bullish");
        assertEquals(EvidenceStrength.of("0.25"), assessments.of(H15S).evidenceStrength());
        assertEquals(InterpretationDirection.UNKNOWN, assessments.of(H60S).direction(), "50 < 100 minTradeCount");
        assertEquals(List.of(FLOW_INSUFFICIENT_ACTIVITY), assessments.of(H60S).reasonCodes());

        // the same window with a -0.22 imbalance: 15S is asymmetric (bearish at -0.25), so it stays NEUTRAL
        MarketFeaturesSnapshot bearishLean = snapshot(uniform(window("-0.22", 200, 150, 0)));
        FlowAssessments lean = evaluator.evaluate(bearishLean, quality(bearishLean), POLICY);
        assertEquals(InterpretationDirection.NEUTRAL, lean.of(H15S).direction());
        assertEquals(InterpretationDirection.BEARISH, lean.of(H60S).direction(), "-0.22 <= -0.15");
    }

    // ------------------------------------------------------------------ with the real Stage 3 pipeline

    @Test
    void partialWarmUpEvaluatesShortHorizonsAndProjectsLongOnes() {
        MarketFeaturesSnapshot snapshot = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(tradeFlow(active("0.70"), active("0.70"), null, null))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).warmingUp(true).qualityReasons(List.of("WARMING_UP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);

        FlowAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(InterpretationDirection.BULLISH, assessments.of(H1S).direction());
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H5S).direction());
        for (MarketHorizon horizon : List.of(H15S, H60S)) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertEquals(List.of(QualityReasonCodes.WINDOW_WARMING_UP), evidence.reasonCodes());
        }
    }

    @Test
    void staleTradesFailedGroupAndNoDataAreProjectedFromStage3() {
        MarketFeaturesSnapshot stale = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.70")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).staleTrades(true).qualityReasons(List.of("STALE_TRADES")).build())
                .build();
        MarketFeaturesSnapshot failed = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniform(active("0.70")))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder().failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                .build();
        MarketFeaturesSnapshot noData = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(null)
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.NO_DATA).staleTrades(true).qualityReasons(List.of("NO_MARKET_DATA")).build())
                .build();

        for (EvidenceAssessment e : evaluator.evaluate(stale, QUALITY_RESOLVER.resolve(stale, ASSESSED_AT, QUALITY_POLICY), POLICY).asList()) {
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, e.availabilityStatus());
            assertEquals(List.of(QualityReasonCodes.STALE_TRADES), e.reasonCodes());
        }
        for (EvidenceAssessment e : evaluator.evaluate(failed, QUALITY_RESOLVER.resolve(failed, ASSESSED_AT, QUALITY_POLICY), POLICY).asList()) {
            assertEquals(EvidenceAvailabilityStatus.FAILED, e.availabilityStatus());
            assertEquals(List.of(QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED), e.reasonCodes());
        }
        for (EvidenceAssessment e : evaluator.evaluate(noData, QUALITY_RESOLVER.resolve(noData, ASSESSED_AT, QUALITY_POLICY), POLICY).asList()) {
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, e.availabilityStatus());
            assertEquals(List.of(QualityReasonCodes.SOURCE_NO_DATA), e.reasonCodes());
            assertEquals(InterpretationDirection.UNKNOWN, e.direction());
        }
    }
}

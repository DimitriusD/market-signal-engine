package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.activeTradeFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.bd;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.regime;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.uniformRegime;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_BEARISH_MOVE;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_BULLISH_MOVE;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_NEUTRAL_MOVE;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_NOT_SCOPED_TO_HORIZON;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_PRICE_CHANGE_MISSING;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_REGIME_CALCULATOR_FAILED;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumReasonCodes.MOMENTUM_REGIME_MISSING;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Momentum V1 evaluation: exact horizon → priceChangeBps mapping, explicit 1S unavailability (never
 * the 5S value), eligibility precedence, failed / missing / implausible as distinct states, inclusive
 * directional boundaries, saturating strength, determinism and horizon-specific verdicts under one
 * explicit fixture policy.
 */
class MomentumAssessmentEvaluatorTest {

    private static final BigDecimal EPS = bd("0.000001");

    private final MomentumAssessmentEvaluator evaluator = new MomentumAssessmentEvaluator();

    private static final List<MarketHorizon> SCOPED = List.of(H5S, H15S, H60S);

    // ------------------------------------------------------------------ shape

    @Test
    void returnsExactlyFourMomentumAssessmentsInCanonicalOrder() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("0"));

        MomentumAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(assessments.asMap().keySet()));
        assertEquals(4, assessments.asList().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceDimension.MOMENTUM, evidence.dimension());
            assertEquals(evidence, evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon),
                    "per-horizon entry point agrees with the aggregate");
        }
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("0"));
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, POLICY, null));
    }

    @Test
    void consistencyGuardRunsExactlyOncePerPublicEvaluation() {
        AtomicInteger verifications = new AtomicInteger();
        MomentumAssessmentEvaluator counted = new MomentumAssessmentEvaluator(new SnapshotQualityConsistencyGuard() {
            @Override
            public void verify(MarketFeaturesSnapshot snapshot, QualityAssessment qualityAssessment) {
                verifications.incrementAndGet();
                super.verify(snapshot, qualityAssessment);
            }
        });
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("0"));
        QualityAssessment qa = quality(snapshot);

        counted.evaluate(snapshot, qa, POLICY);
        assertEquals(1, verifications.get(), "aggregate evaluation verifies once, not once per horizon");
        counted.evaluate(snapshot, qa, POLICY, H15S);
        assertEquals(2, verifications.get(), "per-horizon evaluation verifies once");
    }

    @Test
    void qualityAssessmentOfAnotherSnapshotIsRejected() {
        // a strongly bullish move paired with the assessment of a different (as-of) snapshot
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("9"));
        QualityAssessment qa = quality(snapshot);
        MarketFeaturesSnapshot otherAsOf = snapshot.toBuilder()
                .evaluationTs(MomentumFixtures.EVENT_TIME.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(otherAsOf, qa, POLICY));
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());
    }

    // ------------------------------------------------------------------ 1S semantics

    @Test
    void eligibleH1sIsExplicitlyUnavailableAndNeverSubstitutedWithThe5sValue() {
        // 5S carries a huge bullish move — 1S must not borrow it
        MarketFeaturesSnapshot snapshot = snapshot(regime("9", "0", "0"));
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().isEligible(H1S), "precondition: 1S is ELIGIBLE at Stage 3");

        EvidenceAssessment h1s = evaluator.evaluate(snapshot, qa, POLICY, H1S);

        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, h1s.availabilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, h1s.direction());
        assertNull(h1s.evidenceStrength());
        assertEquals(List.of(MOMENTUM_NOT_SCOPED_TO_HORIZON), h1s.reasonCodes());
        assertEquals(InterpretationDirection.BULLISH, evaluator.evaluate(snapshot, qa, POLICY, H5S).direction(),
                "the 5S value stays a 5S reading");
    }

    @Test
    void nonEligibleH1sIsProjectedBeforeTheNotScopedRule() {
        // stale trades: Stage 3 marks every horizon UNTRUSTED — 1S keeps the eligibility projection
        MarketFeaturesSnapshot stale = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .regime(uniformRegime("9"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(stale, ASSESSED_AT, QUALITY_POLICY);

        EvidenceAssessment h1s = evaluator.evaluate(stale, qa, POLICY, H1S);

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, h1s.availabilityStatus());
        assertEquals(List.of(QualityReasonCodes.STALE_TRADES), h1s.reasonCodes(),
                "eligibility precedence: the projection keeps the eligibility reasons, not NOT_SCOPED");
        assertFalse(h1s.reasonCodes().contains(MOMENTUM_NOT_SCOPED_TO_HORIZON));
    }

    @Test
    void h1sSelectorFailsFastInsteadOfGuessing() {
        assertThrows(IllegalArgumentException.class,
                () -> MomentumAssessmentEvaluator.priceChangeBpsOf(uniformRegime("1"), H1S));
    }

    // ------------------------------------------------------------------ exact horizon → feature mapping

    @Test
    void eachScopedHorizonReadsExactlyItsOwnPriceChangeField() {
        // three different readings in one snapshot: 5S bullish, 15S bearish, 60S neutral
        MarketFeaturesSnapshot snapshot = snapshot(regime("6", "-8", "0"));
        QualityAssessment qa = quality(snapshot);

        MomentumAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(InterpretationDirection.BULLISH, assessments.of(H5S).direction(), "6 >= 2");
        assertEquals(EvidenceStrength.of("0.6"), assessments.of(H5S).evidenceStrength(), "6 / 10");
        assertEquals(InterpretationDirection.BEARISH, assessments.of(H15S).direction(), "-8 <= -4");
        assertEquals(EvidenceStrength.of("0.666666"), assessments.of(H15S).evidenceStrength(), "8 / 12 rounded down");
        assertEquals(InterpretationDirection.NEUTRAL, assessments.of(H60S).direction(), "0 within ±5");

        // changing only the 15S value changes only the 15S verdict
        MarketFeaturesSnapshot changed15s = snapshot(regime("6", "5", "0"));
        MomentumAssessments after = evaluator.evaluate(changed15s, quality(changed15s), POLICY);
        assertEquals(InterpretationDirection.BULLISH, after.of(H15S).direction(), "5 >= 3");
        assertEquals(assessments.of(H5S), after.of(H5S));
        assertEquals(assessments.of(H60S), after.of(H60S));
    }

    // ------------------------------------------------------------------ direction and strength

    @ParameterizedTest
    @EnumSource(value = MarketHorizon.class, names = {"H5S", "H15S", "H60S"})
    void bullishThresholdIsInclusive(MarketHorizon horizon) {
        BigDecimal threshold = POLICY.of(horizon).bullishPriceChangeBpsThreshold();

        assertDirection(horizon, threshold.subtract(EPS), InterpretationDirection.NEUTRAL, MOMENTUM_NEUTRAL_MOVE);
        assertDirection(horizon, threshold, InterpretationDirection.BULLISH, MOMENTUM_BULLISH_MOVE);
        assertDirection(horizon, threshold.add(EPS), InterpretationDirection.BULLISH, MOMENTUM_BULLISH_MOVE);
    }

    @ParameterizedTest
    @EnumSource(value = MarketHorizon.class, names = {"H5S", "H15S", "H60S"})
    void bearishThresholdIsInclusive(MarketHorizon horizon) {
        BigDecimal threshold = POLICY.of(horizon).bearishPriceChangeBpsThreshold();

        assertDirection(horizon, threshold.add(EPS), InterpretationDirection.NEUTRAL, MOMENTUM_NEUTRAL_MOVE);
        assertDirection(horizon, threshold, InterpretationDirection.BEARISH, MOMENTUM_BEARISH_MOVE);
        assertDirection(horizon, threshold.subtract(EPS), InterpretationDirection.BEARISH, MOMENTUM_BEARISH_MOVE);
    }

    private void assertDirection(MarketHorizon horizon, BigDecimal priceChangeBps,
                                 InterpretationDirection expected, ReasonCode expectedReason) {
        MarketFeaturesSnapshot snapshot = snapshot(withValue(horizon, priceChangeBps.toPlainString()));
        EvidenceAssessment evidence = evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon);

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
        assertEquals(expected, evidence.direction(), horizon.wireValue() + " @ " + priceChangeBps.toPlainString());
        assertEquals(List.of(expectedReason), evidence.reasonCodes());
        if (expected == InterpretationDirection.NEUTRAL) {
            assertEquals(EvidenceStrength.MIN, evidence.evidenceStrength(), "neutral strength is a real 0");
        } else {
            assertEquals(MomentumAssessmentEvaluator.saturatingStrength(
                            priceChangeBps.abs(), POLICY.of(horizon).fullStrengthAbsMoveBps()),
                    evidence.evidenceStrength());
        }
    }

    /** Only {@code horizon} carries {@code value}; the other scoped horizons read a real zero. */
    private static RegimeFeature withValue(MarketHorizon horizon, String value) {
        return regime(
                horizon == H5S ? value : "0",
                horizon == H15S ? value : "0",
                horizon == H60S ? value : "0");
    }

    @Test
    void zeroMoveIsNeutralWithZeroStrength() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("0"));
        QualityAssessment qa = quality(snapshot);

        for (MarketHorizon horizon : SCOPED) {
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(InterpretationDirection.NEUTRAL, evidence.direction(), horizon.wireValue());
            assertEquals(EvidenceStrength.MIN, evidence.evidenceStrength());
            assertEquals("0", evidence.evidenceStrength().toPlainString());
            assertEquals(List.of(MOMENTUM_NEUTRAL_MOVE), evidence.reasonCodes());
        }
    }

    @Test
    void strengthReachesMaxExactlyAtFullStrengthAndSaturatesAbove() {
        // 5S: full strength at 10, safe up to 50
        assertEquals(EvidenceStrength.of("0.999999"),
                strengthAt(H5S, "9.99999"), "just below full strength stays below 1");
        assertEquals(EvidenceStrength.MAX, strengthAt(H5S, "10"), "exact full-strength boundary");
        assertEquals(EvidenceStrength.MAX, strengthAt(H5S, "35"), "saturation: capped at 1, never above");
        assertEquals(EvidenceStrength.MAX, strengthAt(H5S, "-50"), "bearish saturation at the safe boundary");
    }

    private EvidenceStrength strengthAt(MarketHorizon horizon, String value) {
        MarketFeaturesSnapshot snapshot = snapshot(withValue(horizon, value));
        return evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon).evidenceStrength();
    }

    @Test
    void strengthRatioIsDeterministicBigDecimal() {
        assertEquals(EvidenceStrength.of("0.35"),
                MomentumAssessmentEvaluator.saturatingStrength(bd("3.5"), bd("10")));
        assertEquals(EvidenceStrength.of("0.333333"),
                MomentumAssessmentEvaluator.saturatingStrength(bd("4"), bd("12")), "non-terminating, rounded down");
        assertEquals(EvidenceStrength.MAX, MomentumAssessmentEvaluator.saturatingStrength(bd("12"), bd("12")));
    }

    // ------------------------------------------------------------------ missing / failed / implausible

    @Test
    void missingRegimeGroupIsUnavailableNotNeutral() {
        MarketFeaturesSnapshot snapshot = snapshot(null);
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().allEligible(), "precondition: eligibility is trade-flow-backed");

        for (MarketHorizon horizon : SCOPED) {
            EvidenceAssessment evidence = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(MOMENTUM_REGIME_MISSING), evidence.reasonCodes());
        }
    }

    @Test
    void missingPriceChangeValueIsUnavailablePerHorizon() {
        // only the 15S value is absent — the other horizons still evaluate
        MarketFeaturesSnapshot snapshot = snapshot(regime("6", null, "0"));
        QualityAssessment qa = quality(snapshot);

        EvidenceAssessment missing = evaluator.evaluate(snapshot, qa, POLICY, H15S);
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, missing.availabilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, missing.direction(), "an absent move is not zero");
        assertEquals(List.of(MOMENTUM_PRICE_CHANGE_MISSING), missing.reasonCodes());
        assertEquals(InterpretationDirection.BULLISH, evaluator.evaluate(snapshot, qa, POLICY, H5S).direction());
        assertEquals(InterpretationDirection.NEUTRAL, evaluator.evaluate(snapshot, qa, POLICY, H60S).direction());
    }

    @Test
    void failedShortTermRegimeGroupIsFailedForScopedHorizonsAndNotScopedFor1s() {
        MarketFeaturesSnapshot failed = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .regime(uniformRegime("9"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("short-term-regime")).totalFeatureGroups(4).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(failed, ASSESSED_AT, QUALITY_POLICY);
        assertTrue(qa.horizonEligibilities().allEligible(),
                "precondition: a regime failure does not touch trade-flow eligibility");

        for (MarketHorizon horizon : SCOPED) {
            EvidenceAssessment evidence = evaluator.evaluate(failed, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.FAILED, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction(), "no bullish evidence from a failed group");
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(MOMENTUM_REGIME_CALCULATOR_FAILED), evidence.reasonCodes());
        }
        assertEquals(List.of(MOMENTUM_NOT_SCOPED_TO_HORIZON),
                evaluator.evaluate(failed, qa, POLICY, H1S).reasonCodes(),
                "1S answers 'not scoped' before the regime dependency is even considered");
    }

    @Test
    void maxSafeBoundaryIsAcceptedAndAboveIsUntrusted() {
        // 5S: maxSafe 50 — the boundary itself is trusted (and saturated)
        MarketFeaturesSnapshot atMax = snapshot(withValue(H5S, "50"));
        EvidenceAssessment trusted = evaluator.evaluate(atMax, quality(atMax), POLICY, H5S);
        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, trusted.availabilityStatus());
        assertEquals(InterpretationDirection.BULLISH, trusted.direction());
        assertEquals(EvidenceStrength.MAX, trusted.evidenceStrength());

        for (String implausible : List.of("50.000001", "-50.000001", "1000")) {
            MarketFeaturesSnapshot aboveMax = snapshot(withValue(H5S, implausible));
            EvidenceAssessment untrusted = evaluator.evaluate(aboveMax, quality(aboveMax), POLICY, H5S);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, untrusted.availabilityStatus(), implausible);
            assertEquals(InterpretationDirection.UNKNOWN, untrusted.direction());
            assertNull(untrusted.evidenceStrength());
            assertEquals(List.of(MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE), untrusted.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ eligibility precedence

    @Test
    void nonEligibleHorizonsAreProjectedWithoutReadingTheRegime() {
        // warm-up: 1S/5S computed, 15S/60S not yet — the regime carries corrupt directional values
        MarketFeaturesSnapshot warmingUp = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder()
                        .window1s(MomentumFixtures.activeWindow())
                        .window5s(MomentumFixtures.activeWindow())
                        .build())
                .regime(uniformRegime("999999"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).warmingUp(true)
                        .qualityReasons(List.of("WARMING_UP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(warmingUp, ASSESSED_AT, QUALITY_POLICY);

        MomentumAssessments assessments = evaluator.evaluate(warmingUp, qa, POLICY);

        for (MarketHorizon horizon : List.of(H15S, H60S)) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction());
            assertEquals(List.of(QualityReasonCodes.WINDOW_WARMING_UP), evidence.reasonCodes(),
                    "eligibility reasons are kept verbatim; the corrupt regime value is never read");
        }
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, assessments.of(H5S).availabilityStatus(),
                "an eligible horizon does read the value and rejects the implausible move");
    }

    // ------------------------------------------------------------------ determinism / immutability

    @Test
    void sameInputAndPolicyGiveValueEqualResults() {
        MarketFeaturesSnapshot snapshot = snapshot(regime("2", "-4.5", "3"));
        QualityAssessment qa = quality(snapshot);

        MomentumAssessments first = evaluator.evaluate(snapshot, qa, POLICY);
        MomentumAssessments second = new MomentumAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
        assertEquals(List.of(MOMENTUM_NOT_SCOPED_TO_HORIZON), first.of(H1S).reasonCodes());
        assertEquals(List.of(MOMENTUM_BULLISH_MOVE), first.of(H5S).reasonCodes());
        assertEquals(List.of(MOMENTUM_BEARISH_MOVE), first.of(H15S).reasonCodes());
        assertEquals(List.of(MOMENTUM_NEUTRAL_MOVE), first.of(H60S).reasonCodes());
    }

    @Test
    void resultCollectionsAreImmutable() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("6"));
        MomentumAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> assessments.of(H5S).reasonCodes().add(MOMENTUM_NEUTRAL_MOVE));
    }
}

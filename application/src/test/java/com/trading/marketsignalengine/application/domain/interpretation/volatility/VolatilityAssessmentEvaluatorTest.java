package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.activeTradeFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.bd;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.regime;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.uniformRegime;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_EXTREME;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_HIGH;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_LOW;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_NEGATIVE;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_NORMAL;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_REGIME_CALCULATOR_FAILED;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_REGIME_MISSING;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityReasonCodes.VOLATILITY_VALUE_MISSING;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
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
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Volatility V1 evaluation: exact horizon → realizedVolatilityBps mapping, eligibility precedence,
 * failed / missing / negative as distinct states, inclusive band boundaries, the never-directional
 * invariant, typed levels, determinism and horizon-specific classifications under one explicit
 * fixture policy.
 */
class VolatilityAssessmentEvaluatorTest {

    private static final BigDecimal EPS = bd("0.000001");

    private final VolatilityAssessmentEvaluator evaluator = new VolatilityAssessmentEvaluator();

    // ------------------------------------------------------------------ shape

    @Test
    void returnsExactlyFourVolatilityAssessmentsInCanonicalOrder() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("1"));

        VolatilityAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(assessments.asMap().keySet()));
        assertEquals(4, assessments.asList().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = assessments.of(horizon);
            assertEquals(EvidenceDimension.VOLATILITY, assessment.evidence().dimension());
            assertEquals(assessment, evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon),
                    "per-horizon entry point agrees with the aggregate");
        }
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("1"));
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, POLICY, null));
    }

    @Test
    void consistencyGuardRunsExactlyOncePerPublicEvaluation() {
        AtomicInteger verifications = new AtomicInteger();
        VolatilityAssessmentEvaluator counted = new VolatilityAssessmentEvaluator(new SnapshotQualityConsistencyGuard() {
            @Override
            public void verify(MarketFeaturesSnapshot snapshot, QualityAssessment qualityAssessment) {
                verifications.incrementAndGet();
                super.verify(snapshot, qualityAssessment);
            }
        });
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("1"));
        QualityAssessment qa = quality(snapshot);

        counted.evaluate(snapshot, qa, POLICY);
        assertEquals(1, verifications.get(), "aggregate evaluation verifies once, not once per horizon");
        counted.evaluate(snapshot, qa, POLICY, H1S);
        assertEquals(2, verifications.get(), "per-horizon evaluation verifies once");
    }

    @Test
    void qualityAssessmentOfAnotherSnapshotIsRejected() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("1"));
        QualityAssessment qa = quality(snapshot);
        MarketFeaturesSnapshot otherAsOf = snapshot.toBuilder()
                .evaluationTs(VolatilityFixtures.EVENT_TIME.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(otherAsOf, qa, POLICY));
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());
    }

    // ------------------------------------------------------------------ exact horizon → feature mapping

    @Test
    void eachHorizonReadsExactlyItsOwnRealizedVolatilityField() {
        // four different classifications in one snapshot: LOW / NORMAL / HIGH / EXTREME
        MarketFeaturesSnapshot snapshot = snapshot(regime("1", "5", "15", "40"));
        QualityAssessment qa = quality(snapshot);

        VolatilityAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(VolatilityLevel.LOW, assessments.of(H1S).level(), "1 <= 2");
        assertEquals(VolatilityLevel.NORMAL, assessments.of(H5S).level(), "3 < 5 <= 8");
        assertEquals(VolatilityLevel.HIGH, assessments.of(H15S).level(), "10 < 15 <= 20");
        assertEquals(VolatilityLevel.EXTREME, assessments.of(H60S).level(), "40 > 25");

        // changing only the 15S value changes only the 15S classification
        MarketFeaturesSnapshot changed15s = snapshot(regime("1", "5", "3", "40"));
        VolatilityAssessments after = evaluator.evaluate(changed15s, quality(changed15s), POLICY);
        assertEquals(VolatilityLevel.LOW, after.of(H15S).level(), "3 <= 4");
        assertEquals(assessments.of(H1S), after.of(H1S));
        assertEquals(assessments.of(H5S), after.of(H5S));
        assertEquals(assessments.of(H60S), after.of(H60S));
    }

    // ------------------------------------------------------------------ classification

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void classificationBoundariesAreInclusiveOnTheLowerBand(MarketHorizon horizon) {
        VolatilityHorizonPolicy p = POLICY.of(horizon);

        assertLevel(horizon, BigDecimal.ZERO, VolatilityLevel.LOW, "zero volatility is a real LOW reading");
        assertLevel(horizon, p.lowUpperBoundBps(), VolatilityLevel.LOW, "value == lowUpperBound stays LOW");
        assertLevel(horizon, p.lowUpperBoundBps().add(EPS), VolatilityLevel.NORMAL, "just above lowUpperBound");
        assertLevel(horizon, p.normalUpperBoundBps(), VolatilityLevel.NORMAL, "value == normalUpperBound stays NORMAL");
        assertLevel(horizon, p.normalUpperBoundBps().add(EPS), VolatilityLevel.HIGH, "just above normalUpperBound");
        assertLevel(horizon, p.highUpperBoundBps(), VolatilityLevel.HIGH, "value == highUpperBound stays HIGH");
        assertLevel(horizon, p.highUpperBoundBps().add(EPS), VolatilityLevel.EXTREME, "just above highUpperBound");
    }

    private void assertLevel(MarketHorizon horizon, BigDecimal value, VolatilityLevel expected, String label) {
        MarketFeaturesSnapshot snapshot = snapshot(withValue(horizon, value.toPlainString()));
        VolatilityAssessment assessment = evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon);

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, assessment.availabilityStatus(), label);
        assertEquals(expected, assessment.level(), horizon.wireValue() + " @ " + value.toPlainString() + ": " + label);
        assertEquals(List.of(levelCode(expected)), assessment.reasonCodes());
    }

    private static ReasonCode levelCode(VolatilityLevel level) {
        return switch (level) {
            case LOW -> VOLATILITY_LOW;
            case NORMAL -> VOLATILITY_NORMAL;
            case HIGH -> VOLATILITY_HIGH;
            case EXTREME -> VOLATILITY_EXTREME;
            case UNKNOWN -> throw new IllegalArgumentException("UNKNOWN has no level code");
        };
    }

    /** Only {@code horizon} carries {@code value}; the other horizons read a real zero. */
    private static RegimeFeature withValue(MarketHorizon horizon, String value) {
        return regime(
                horizon == H1S ? value : "0",
                horizon == H5S ? value : "0",
                horizon == H15S ? value : "0",
                horizon == H60S ? value : "0");
    }

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void availableVolatilityIsNeverDirectionalAndCarriesNoStrength(MarketHorizon horizon) {
        for (String value : List.of("0", "5", "15", "1000")) {
            MarketFeaturesSnapshot snapshot = snapshot(uniformRegime(value));
            VolatilityAssessment assessment = evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon);

            assertTrue(assessment.isAvailable(), value);
            assertEquals(InterpretationDirection.UNKNOWN, assessment.evidence().direction(),
                    "volatility never votes a direction");
            assertNull(assessment.evidence().evidenceStrength(), "volatility never carries a strength");
            assertTrue(assessment.level().isClassified());
        }
    }

    @Test
    void highAndExtremeDoNotBlockTheHorizon() {
        MarketFeaturesSnapshot extreme = snapshot(uniformRegime("1000"));
        QualityAssessment qa = quality(extreme);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = evaluator.evaluate(extreme, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.AVAILABLE, assessment.availabilityStatus(),
                    "EXTREME is context, not a block or NO_TRADE");
            assertEquals(VolatilityLevel.EXTREME, assessment.level());
        }
    }

    // ------------------------------------------------------------------ missing / failed / invalid

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void negativeVolatilityIsUntrustedWithLevelUnknown(MarketHorizon horizon) {
        MarketFeaturesSnapshot snapshot = snapshot(withValue(horizon, "-0.000001"));
        VolatilityAssessment assessment = evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon);

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, assessment.availabilityStatus());
        assertEquals(VolatilityLevel.UNKNOWN, assessment.level());
        assertEquals(InterpretationDirection.UNKNOWN, assessment.evidence().direction());
        assertNull(assessment.evidence().evidenceStrength());
        assertEquals(List.of(VOLATILITY_NEGATIVE), assessment.reasonCodes());
    }

    @Test
    void missingRegimeGroupIsUnavailableOnEveryHorizon() {
        MarketFeaturesSnapshot snapshot = snapshot(null);
        QualityAssessment qa = quality(snapshot);
        assertTrue(qa.horizonEligibilities().allEligible(), "precondition: eligibility is trade-flow-backed");

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = evaluator.evaluate(snapshot, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, assessment.availabilityStatus(), horizon.wireValue());
            assertEquals(VolatilityLevel.UNKNOWN, assessment.level());
            assertEquals(List.of(VOLATILITY_REGIME_MISSING), assessment.reasonCodes());
        }
    }

    @Test
    void missingValueIsUnavailablePerHorizonWhileOthersClassify() {
        MarketFeaturesSnapshot snapshot = snapshot(regime("1", null, "15", "40"));
        QualityAssessment qa = quality(snapshot);

        VolatilityAssessment missing = evaluator.evaluate(snapshot, qa, POLICY, H5S);
        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, missing.availabilityStatus());
        assertEquals(VolatilityLevel.UNKNOWN, missing.level(), "an absent value is not LOW");
        assertEquals(List.of(VOLATILITY_VALUE_MISSING), missing.reasonCodes());
        assertEquals(VolatilityLevel.LOW, evaluator.evaluate(snapshot, qa, POLICY, H1S).level());
        assertEquals(VolatilityLevel.HIGH, evaluator.evaluate(snapshot, qa, POLICY, H15S).level());
        assertEquals(VolatilityLevel.EXTREME, evaluator.evaluate(snapshot, qa, POLICY, H60S).level());
    }

    @Test
    void failedShortTermRegimeGroupIsFailedOnEveryHorizon() {
        MarketFeaturesSnapshot failed = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .regime(uniformRegime("5"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("short-term-regime")).totalFeatureGroups(4).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(failed, ASSESSED_AT, QUALITY_POLICY);
        assertTrue(qa.horizonEligibilities().allEligible(),
                "precondition: a regime failure does not touch trade-flow eligibility");

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = evaluator.evaluate(failed, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.FAILED, assessment.availabilityStatus(), horizon.wireValue());
            assertEquals(VolatilityLevel.UNKNOWN, assessment.level(), "no level from a failed group");
            assertEquals(List.of(VOLATILITY_REGIME_CALCULATOR_FAILED), assessment.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ eligibility precedence

    @Test
    void nonEligibleHorizonIsProjectedWithoutReadingTheRegime() {
        // stale trades: Stage 3 marks every horizon UNTRUSTED — the corrupt negative value is never read
        MarketFeaturesSnapshot stale = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .regime(uniformRegime("-999"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED).staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(stale, ASSESSED_AT, QUALITY_POLICY);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = evaluator.evaluate(stale, qa, POLICY, horizon);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, assessment.availabilityStatus(), horizon.wireValue());
            assertEquals(VolatilityLevel.UNKNOWN, assessment.level());
            assertEquals(List.of(QualityReasonCodes.STALE_TRADES), assessment.reasonCodes(),
                    "eligibility reasons are kept verbatim; VOLATILITY_NEGATIVE never appears");
        }
    }

    // ------------------------------------------------------------------ determinism / immutability

    @Test
    void sameInputAndPolicyGiveValueEqualResults() {
        MarketFeaturesSnapshot snapshot = snapshot(regime("2", "8", "20", "26"));
        QualityAssessment qa = quality(snapshot);

        VolatilityAssessments first = evaluator.evaluate(snapshot, qa, POLICY);
        VolatilityAssessments second = new VolatilityAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
        assertEquals(VolatilityLevel.LOW, first.of(H1S).level(), "2 <= 2 (boundary)");
        assertEquals(VolatilityLevel.NORMAL, first.of(H5S).level(), "8 <= 8 (boundary)");
        assertEquals(VolatilityLevel.HIGH, first.of(H15S).level(), "20 <= 20 (boundary)");
        assertEquals(VolatilityLevel.EXTREME, first.of(H60S).level(), "26 > 25");
    }

    @Test
    void resultCollectionsAreImmutable() {
        MarketFeaturesSnapshot snapshot = snapshot(uniformRegime("5"));
        VolatilityAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> assessments.of(H5S).reasonCodes().add(VOLATILITY_LOW));
    }
}

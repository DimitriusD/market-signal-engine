package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.uniformTradeFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.window;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_BOOK_CONTRADICTS_DIRECTION;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_BOOK_SUPPORTS_DIRECTION;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_FROM_FLOW;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_DIVERGENCE;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_QUIET;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_TRENDING;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_UNKNOWN;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_VOLATILE;
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
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessments;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stage 6 orchestration end to end in the domain: one safe public entry point (snapshot + quality +
 * aggregate policy), nested evidence value-equal to a direct run of every evidence evaluator on the
 * same snapshot, eligibility precedence, direction / book-context / regime composition, evidence
 * completeness and determinism — with the real Stage 3 resolver producing the quality assessment.
 */
class HorizonAssessmentEvaluatorTest {

    private final HorizonAssessmentEvaluator evaluator = new HorizonAssessmentEvaluator();

    // ------------------------------------------------------------------ shape and completeness

    @Test
    void returnsExactlyFourHorizonAssessmentsWithAllFourEvidenceDimensions() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");

        HorizonAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(assessments.asMap().keySet()));
        assertEquals(4, assessments.asList().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonAssessment assessment = assessments.of(horizon);
            assertEquals(horizon, assessment.horizon());
            assertEquals(List.of(EvidenceDimension.FLOW, EvidenceDimension.MOMENTUM,
                            EvidenceDimension.VOLATILITY, EvidenceDimension.BOOK),
                    assessment.evidenceAssessments().stream().map(EvidenceAssessment::dimension).toList(),
                    "exactly the four dimensions, canonical order: " + horizon.wireValue());
            // the flattened volatility evidence is kept (direction UNKNOWN, no strength, by model invariant)
            EvidenceAssessment volatility = assessment.evidence(EvidenceDimension.VOLATILITY).orElseThrow();
            assertEquals(InterpretationDirection.UNKNOWN, volatility.direction());
            assertNull(volatility.evidenceStrength());
        }
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
    }

    // ------------------------------------------------------------------ orchestration boundary

    @Test
    void nestedEvidenceIsExactlyWhatEachEvidenceEvaluatorProducesForThisSnapshot() {
        // the orchestrator derives every evidence set itself, from this one snapshot/assessment pair:
        // its nested evidence must be value-equal to a direct run of each evidence evaluator (the
        // per-evaluator guard-once behaviour is pinned in each evidence package's own tests)
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "-6", "9", "0.10", "-1");
        QualityAssessment qa = quality(snapshot);

        HorizonAssessments assessments = evaluator.evaluate(snapshot, qa, POLICY);

        FlowAssessments flow = new FlowAssessmentEvaluator().evaluate(snapshot, qa, POLICY.flowPolicy());
        MomentumAssessments momentum =
                new MomentumAssessmentEvaluator().evaluate(snapshot, qa, POLICY.momentumPolicy());
        VolatilityAssessments volatility =
                new VolatilityAssessmentEvaluator().evaluate(snapshot, qa, POLICY.volatilityPolicy());
        BookAssessments book = new BookAssessmentEvaluator().evaluate(snapshot, qa, POLICY.bookPolicy());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonAssessment assessment = assessments.of(horizon);
            assertEquals(flow.of(horizon),
                    assessment.evidence(EvidenceDimension.FLOW).orElseThrow(), horizon.wireValue());
            assertEquals(momentum.of(horizon),
                    assessment.evidence(EvidenceDimension.MOMENTUM).orElseThrow(), horizon.wireValue());
            assertEquals(volatility.of(horizon).evidence(),
                    assessment.evidence(EvidenceDimension.VOLATILITY).orElseThrow(), horizon.wireValue());
            assertEquals(book.of(horizon),
                    assessment.evidence(EvidenceDimension.BOOK).orElseThrow(), horizon.wireValue());
        }
    }

    @Test
    void mismatchedSnapshotAndQualityAssessmentFailFast() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);
        MarketFeaturesSnapshot otherAsOf = snapshot.toBuilder()
                .evaluationTs(HorizonFixtures.EVENT_TIME.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(otherAsOf, qa, POLICY));
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());
    }

    @Test
    void noPublicApiAcceptsIndependentEvidenceContainers() {
        for (Method method : HorizonAssessmentEvaluator.class.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == FlowAssessments.class || parameter == MomentumAssessments.class
                                || parameter == VolatilityAssessments.class || parameter == BookAssessments.class,
                        "public API must not accept independently produced evidence: " + method);
            }
        }
        long publicEvaluates = java.util.Arrays.stream(HorizonAssessmentEvaluator.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .count();
        assertEquals(1, publicEvaluates, "exactly one safe public entry point");
    }

    @Test
    void repeatedEvaluationIsDeterministicAndValueEqual() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "-6", "9", "0.10", "-1");
        QualityAssessment qa = quality(snapshot);

        HorizonAssessments first = evaluator.evaluate(snapshot, qa, POLICY);
        HorizonAssessments second = new HorizonAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
    }

    // ------------------------------------------------------------------ composed scenarios

    @Test
    void confirmedBullishScenario() {
        // flow 0.60 (bullish), momentum +6 bps (bullish, strength 0.6), volatility 5 (NORMAL),
        // book: top5 0.60 + microprice 6 (confirmed bullish on 1S)
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        HorizonAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        for (MarketHorizon horizon : List.of(H5S, H15S, H60S)) {
            HorizonAssessment assessment = assessments.of(horizon);
            assertEquals(InterpretationDirection.BULLISH, assessment.direction(), horizon.wireValue());
            assertEquals(EvidenceStrength.of("0.6"), assessment.evidenceStrength(), "min(flow 0.6, momentum 0.6)");
            assertEquals(MarketRegime.TRENDING, assessment.regime(), "NORMAL volatility + directional momentum");
            assertEquals(List.of(HORIZON_FLOW_MOMENTUM_CONFIRMED, HORIZON_REGIME_TRENDING),
                    assessment.reasonCodes(), "book is not scoped there — no book codes");
        }

        HorizonAssessment h1s = assessments.of(H1S);
        assertEquals(InterpretationDirection.BULLISH, h1s.direction(), "flow-only 1S direction");
        assertEquals(EvidenceStrength.of("0.6"), h1s.evidenceStrength(), "book support adds no strength");
        assertEquals(MarketRegime.UNKNOWN, h1s.regime(), "NORMAL volatility + not-scoped momentum");
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_SUPPORTS_DIRECTION, HORIZON_REGIME_UNKNOWN),
                h1s.reasonCodes());
        assertTrue(h1s.isEligible());
    }

    @Test
    void divergenceScenario() {
        // flow bullish 0.60 vs momentum -6 bps (bearish); no book indicators at all
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "-6", "5", null, null);
        HorizonAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        for (MarketHorizon horizon : List.of(H5S, H15S, H60S)) {
            HorizonAssessment assessment = assessments.of(horizon);
            assertEquals(InterpretationDirection.MIXED, assessment.direction(), horizon.wireValue());
            assertNull(assessment.evidenceStrength());
            assertEquals(MarketRegime.TRENDING, assessment.regime(),
                    "regime reads momentum, not the horizon direction — a divergence can still be trending");
            assertEquals(List.of(HORIZON_FLOW_MOMENTUM_DIVERGENCE, HORIZON_REGIME_TRENDING), assessment.reasonCodes());
        }

        HorizonAssessment h1s = assessments.of(H1S);
        assertEquals(InterpretationDirection.BULLISH, h1s.direction(), "1S sees only flow — no divergence there");
        assertEquals(EvidenceStrength.of("0.6"), h1s.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_REGIME_UNKNOWN), h1s.reasonCodes(),
                "an unavailable book adds no horizon-level code");
    }

    @Test
    void quietNeutralScenario() {
        // everything neutral, volatility 1 (LOW): a real interpreted flat market
        MarketFeaturesSnapshot snapshot = snapshot("0.00", "0", "1", "0.10", "0.5");
        HorizonAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonAssessment assessment = assessments.of(horizon);
            assertEquals(InterpretationDirection.NEUTRAL, assessment.direction(), horizon.wireValue());
            assertEquals(EvidenceStrength.MIN, assessment.evidenceStrength(), "neutral is a real 0");
            assertEquals(MarketRegime.QUIET, assessment.regime(), "LOW volatility without a directional move");
            assertEquals(List.of(HORIZON_DIRECTION_NEUTRAL, HORIZON_REGIME_QUIET), assessment.reasonCodes(),
                    "a neutral book next to a non-directional conclusion adds no code: " + horizon.wireValue());
        }
    }

    @Test
    void bookContradictionScenario() {
        // 1S: flow bullish 0.60 vs a confirmed bearish book (top5 -0.60, microprice -6); volatility 20 (EXTREME)
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "0", "20", "-0.60", "-6");
        HorizonAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        HorizonAssessment h1s = assessments.of(H1S);
        assertEquals(InterpretationDirection.BULLISH, h1s.direction(), "book never reverses the primary direction");
        assertNull(h1s.evidenceStrength(), "the contradicted conclusion carries no strength");
        assertEquals(MarketRegime.VOLATILE, h1s.regime());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_CONTRADICTS_DIRECTION, HORIZON_REGIME_VOLATILE),
                h1s.reasonCodes());

        HorizonAssessment h5s = assessments.of(H5S);
        assertEquals(InterpretationDirection.BULLISH, h5s.direction(), "flow directional, momentum neutral (0)");
        assertEquals(EvidenceStrength.of("0.6"), h5s.evidenceStrength(), "not-scoped book cannot contradict 5S");
        assertEquals(MarketRegime.VOLATILE, h5s.regime(), "EXTREME dominates directional momentum absence");
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_REGIME_VOLATILE), h5s.reasonCodes());
    }

    // ------------------------------------------------------------------ eligibility precedence

    @Test
    void nonEligibleHorizonsKeepTheOriginalEligibilityAndNeverBecomeNeutral() {
        record Case(String label, MarketFeaturesSnapshot snapshot, HorizonEligibilityStatus expectedStatus,
                    List<ReasonCode> expectedReasons) {
        }
        List<Case> cases = List.of(
                new Case("stale trades → UNTRUSTED",
                        withQualityFlags(q -> q.status(FeatureQualityStatus.DEGRADED).staleTrades(true)
                                .qualityReasons(List.of("STALE_TRADES"))),
                        HorizonEligibilityStatus.UNTRUSTED, List.of(QualityReasonCodes.STALE_TRADES)),
                new Case("failed trade-flow → FAILED",
                        withQualityFlags(q -> q.status(FeatureQualityStatus.DEGRADED)
                                .qualityReasons(List.of("CALCULATOR_FAILURE"))).toBuilder()
                                .diagnostics(FeatureDiagnostics.builder()
                                        .failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                                .build(),
                        HorizonEligibilityStatus.FAILED, List.of(QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED)),
                new Case("no data → UNAVAILABLE",
                        withQualityFlags(q -> q.status(FeatureQualityStatus.NO_DATA).staleTrades(true)
                                .qualityReasons(List.of("NO_MARKET_DATA"))).toBuilder().tradeFlow(null).build(),
                        HorizonEligibilityStatus.UNAVAILABLE, List.of(QualityReasonCodes.SOURCE_NO_DATA)),
                new Case("warm-up → WARMING_UP",
                        withQualityFlags(q -> q.status(FeatureQualityStatus.DEGRADED).warmingUp(true)
                                .qualityReasons(List.of("WARMING_UP"))).toBuilder()
                                .tradeFlow(TradeFlowFeature.builder().build()).build(),
                        HorizonEligibilityStatus.WARMING_UP, List.of(QualityReasonCodes.WINDOW_WARMING_UP)));
        // UNKNOWN eligibility is not emitted by Stage 3 and cannot pass the consistency guard — its
        // projection is pinned at unit level (EvidenceEligibilityProjection + HorizonAssessment invariants).

        for (Case c : cases) {
            QualityAssessment qa = QUALITY_RESOLVER.resolve(c.snapshot(), ASSESSED_AT, QUALITY_POLICY);
            HorizonAssessments assessments = evaluator.evaluate(c.snapshot(), qa, POLICY);
            for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
                HorizonAssessment assessment = assessments.of(horizon);
                assertEquals(c.expectedStatus(), assessment.eligibilityStatus(), c.label() + " " + horizon.wireValue());
                assertEquals(InterpretationDirection.UNKNOWN, assessment.direction(), "never NEUTRAL: " + c.label());
                assertNull(assessment.evidenceStrength(), c.label());
                assertNull(assessment.regime(), "non-eligible horizon has no regime: " + c.label());
                assertEquals(c.expectedReasons(), assessment.eligibility().reasonCodes(),
                        "original eligibility preserved: " + c.label());
                assertEquals(c.expectedReasons(), assessment.reasonCodes(),
                        "horizon reasons = eligibility reasons verbatim: " + c.label());
                assertEquals(4, assessment.evidenceAssessments().size(), "all four nested evidence attached");
                assertTrue(assessment.evidenceAssessments().stream().noneMatch(EvidenceAssessment::isAvailable),
                        "no nested evidence may be AVAILABLE: " + c.label());
            }
        }
    }

    private static MarketFeaturesSnapshot withQualityFlags(QualityCustomizer customizer) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .regime(HorizonFixtures.regime("6", "5"))
                .bbo(HorizonFixtures.bbo("6"))
                .book(HorizonFixtures.book("0.60"))
                .quality(customizer.apply(SignalRuleTestSupport.tradableQuality().toBuilder()).build())
                .build();
    }

    private interface QualityCustomizer {
        FeatureQuality.FeatureQualityBuilder apply(FeatureQuality.FeatureQualityBuilder builder);
    }

    @Test
    void partiallyEligibleSnapshotMixesResolvedAndProjectedHorizons() {
        // history gap: 1S/5S computed and ELIGIBLE, uncovered 15S/60S UNTRUSTED — one snapshot
        MarketFeaturesSnapshot gap = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder().window1s(window("0.60")).window5s(window("0.60")).build())
                .regime(HorizonFixtures.regime("6", "5"))
                .bbo(HorizonFixtures.bbo("6"))
                .book(HorizonFixtures.book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(gap, ASSESSED_AT, QUALITY_POLICY);

        HorizonAssessments assessments = evaluator.evaluate(gap, qa, POLICY);

        assertEquals(InterpretationDirection.BULLISH, assessments.of(H1S).direction());
        assertEquals(InterpretationDirection.BULLISH, assessments.of(H5S).direction());
        assertEquals(MarketRegime.TRENDING, assessments.of(H5S).regime());
        for (MarketHorizon horizon : List.of(H15S, H60S)) {
            HorizonAssessment projected = assessments.of(horizon);
            assertEquals(HorizonEligibilityStatus.UNTRUSTED, projected.eligibilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, projected.direction());
            assertNull(projected.regime());
            assertEquals(List.of(QualityReasonCodes.TRADE_HISTORY_GAP), projected.reasonCodes());
        }
    }
}

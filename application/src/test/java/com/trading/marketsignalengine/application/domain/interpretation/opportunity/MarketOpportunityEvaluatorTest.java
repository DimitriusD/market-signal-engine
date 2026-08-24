package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.ALLOW_VOLATILE_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.CROSS_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.bbo;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.book;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.historyGapSnapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.regime;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.unsafeSnapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.window;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_BLOCKED_BY_QUALITY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_BOOK_CONTRADICTS;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_CONFLICT;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_PARTIAL;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_VOLATILE_REGIME_ALLOWED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAlignment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 8 safe public boundary end to end in the domain: one entry point from a snapshot + real
 * Stage 3 quality assessment + aggregate policy to a {@link MarketOpportunityEvaluation} that
 * carries the cross-horizon evaluation computed during the evaluation and the opportunity resolved
 * from exactly that — plus the reflection guarantees that no public API accepts independently
 * produced interpretation objects and that the resolver and the result constructor stay non-public.
 * The end-to-end scenarios run the real Stage 3–7 evaluators.
 */
class MarketOpportunityEvaluatorTest {

    private final MarketOpportunityEvaluator evaluator = new MarketOpportunityEvaluator();

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
    }

    // ------------------------------------------------------------------ e2e scenario 1 + 2

    @Test
    void fullyBullishContinuationIsALongCandidate() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);
        MarketOpportunity opportunity = evaluation.marketOpportunity();

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment());
        assertEquals(OpportunityStatus.CANDIDATE, opportunity.status());
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, opportunity.type());
        assertEquals(OpportunitySide.LONG, opportunity.side());
        assertEquals(H5S, opportunity.setupHorizon());
        assertEquals(EvidenceStrength.of("0.6"), opportunity.evidenceStrength());
        assertEquals(OpportunityInvalidationCodes.ALL, opportunity.invalidationCodes());
    }

    @Test
    void fullyBearishContinuationIsAShortCandidate() {
        MarketFeaturesSnapshot snapshot = snapshot("-0.60", "-6", "5", "-0.60", "-6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunity opportunity = evaluator.evaluate(snapshot, qa, POLICY).marketOpportunity();

        assertEquals(OpportunityStatus.CANDIDATE, opportunity.status());
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, opportunity.type());
        assertEquals(OpportunitySide.SHORT, opportunity.side());
        assertEquals(H5S, opportunity.setupHorizon());
    }

    // ------------------------------------------------------------------ e2e scenario 3 + 4

    @Test
    void flowOnlyAlignmentWithoutMomentumPersistenceIsNoOpportunity() {
        // strong flow, flat price: every horizon bullish from flow, H15S momentum reads NEUTRAL
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "0", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, evaluation.marketOpportunity().status());
        assertTrue(evaluation.marketOpportunity().reasonCodes().contains(OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED));
    }

    @Test
    void momentumOnlyAlignmentWithoutFlowTriggerIsNoOpportunity() {
        // flat flow, strong price move: structural horizons bullish from momentum, H5S flow NEUTRAL
        MarketFeaturesSnapshot snapshot = snapshot("0", "6", "5", null, null);
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, evaluation.marketOpportunity().status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED),
                evaluation.marketOpportunity().reasonCodes());
    }

    // ------------------------------------------------------------------ e2e scenario 5 + 6 + 7

    @Test
    void seniorConflictIsNoOpportunity() {
        // 60S price falls while 5S/15S rise: H60S flow/momentum divergence conflicts with the structure
        MarketFeaturesSnapshot snapshot = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(OpportunityFixtures.uniformTradeFlow("0.60"))
                .regime(regime("6", "6", "-6", "5"))
                .bbo(bbo("6"))
                .book(book("0.60"))
                .build();
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(CrossHorizonAlignment.CONFLICTING,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_CROSS_HORIZON_CONFLICT),
                evaluation.marketOpportunity().reasonCodes());
    }

    @Test
    void historyGapWithoutSeniorContextIsNoOpportunity() {
        MarketFeaturesSnapshot gap = historyGapSnapshot();
        QualityAssessment qa = quality(gap);
        assertTrue(qa.eligibleForTrading(), "fixture self-check: degraded but eligible");

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(gap, qa, POLICY);

        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT),
                evaluation.marketOpportunity().reasonCodes());
    }

    @Test
    void adverseMicroContextDowngradesToPartialAndNoOpportunity() {
        // bearish 1S flow under a fully bullish structure: ALIGNED downgraded to PARTIALLY_ALIGNED
        MarketFeaturesSnapshot snapshot = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder()
                        .window1s(window("-0.60")).window5s(window("0.60"))
                        .window15s(window("0.60")).window60s(window("0.60"))
                        .build())
                .regime(regime("6", "6", "6", "5"))
                .build();
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_CROSS_HORIZON_PARTIAL),
                evaluation.marketOpportunity().reasonCodes());
    }

    // ------------------------------------------------------------------ e2e scenario 8 + 9

    @Test
    void bookContradictionIsNoOpportunity() {
        // bullish flow and momentum everywhere, but the 1S order book reads bearish
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "-0.60", "-6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH,
                evaluation.crossHorizonEvaluation().crossHorizonAssessment().alignment(), "fixture self-check");
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, evaluation.marketOpportunity().status());
        assertTrue(evaluation.marketOpportunity().reasonCodes().contains(OPPORTUNITY_BOOK_CONTRADICTS));
    }

    @Test
    void blockedQualityIsABlockedOpportunityEvenOnAPerfectMarket() {
        MarketFeaturesSnapshot unsafe = unsafeSnapshot();
        QualityAssessment qa = quality(unsafe);
        assertFalse(qa.eligibleForTrading(), "fixture self-check");

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(unsafe, qa, POLICY);
        MarketOpportunity opportunity = evaluation.marketOpportunity();

        assertEquals(OpportunityStatus.BLOCKED, opportunity.status());
        assertEquals(OPPORTUNITY_BLOCKED_BY_QUALITY, opportunity.reasonCodes().get(0));
        assertEquals(qa.reasonCodes(), opportunity.reasonCodes().subList(1, opportunity.reasonCodes().size()));
    }

    // ------------------------------------------------------------------ e2e scenario 10

    @Test
    void volatileRegimeIsControlledByTheExplicitPolicyEndToEnd() {
        // realized volatility 12 bps (HIGH): every horizon regime VOLATILE, alignment fully bullish
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "12", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunity blocked = evaluator.evaluate(snapshot, qa, POLICY).marketOpportunity();
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, blocked.status());
        assertTrue(blocked.reasonCodes().contains(OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY));

        MarketOpportunity allowed = evaluator.evaluate(snapshot, qa, ALLOW_VOLATILE_POLICY).marketOpportunity();
        assertEquals(OpportunityStatus.CANDIDATE, allowed.status());
        assertEquals(OpportunitySide.LONG, allowed.side());
        assertTrue(allowed.reasonCodes().contains(OPPORTUNITY_VOLATILE_REGIME_ALLOWED));
    }

    // ------------------------------------------------------------------ boundary behaviour

    @Test
    void evaluatorReturnsExactlyTheCrossEvaluationTheOpportunityWasResolvedFrom() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        MarketOpportunityEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        // the cross layer runs once and is returned verbatim, not re-derived differently
        CrossHorizonEvaluation direct = new CrossHorizonAssessmentEvaluator().evaluate(snapshot, qa, CROSS_POLICY);
        assertEquals(direct, evaluation.crossHorizonEvaluation());
        assertSame(qa, evaluation.qualityAssessment(), "the gating quality assessment is returned as-is");
    }

    @Test
    void mismatchedSnapshotAndQualityFailFastThroughTheConsistencyGuard() {
        MarketFeaturesSnapshot full = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment gapQuality = quality(historyGapSnapshot());

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(full, gapQuality, POLICY));
    }

    @Test
    void repeatedEvaluationIsDeterministicAndValueEqual() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");

        MarketOpportunityEvaluation first = evaluator.evaluate(snapshot, quality(snapshot), POLICY);
        MarketOpportunityEvaluation second =
                new MarketOpportunityEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
    }

    // ------------------------------------------------------------------ safe boundary (reflection)

    @Test
    void noPublicApiAcceptsIndependentlyProducedInterpretationObjects() {
        for (Method method : MarketOpportunityEvaluator.class.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == HorizonAssessments.class
                                || parameter == CrossHorizonAssessment.class
                                || parameter == CrossHorizonEvaluation.class,
                        "public API must not accept independently produced interpretation: " + method);
            }
        }
        long publicMethods = Arrays.stream(MarketOpportunityEvaluator.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        assertEquals(1, publicMethods, "exactly one safe public entry point");
    }

    @Test
    void resolverAndResultConstructorAreNotPublic() {
        assertFalse(Modifier.isPublic(OpportunityResolver.class.getModifiers()),
                "the resolver must stay package-private");
        for (Method method : OpportunityResolver.class.getDeclaredMethods()) {
            assertFalse(Modifier.isPublic(method.getModifiers()),
                    "no resolver method may be public: " + method);
        }
        for (Constructor<?> constructor : MarketOpportunityEvaluation.class.getDeclaredConstructors()) {
            assertFalse(Modifier.isPublic(constructor.getModifiers()),
                    "only the safe evaluator may create a MarketOpportunityEvaluation: " + constructor);
        }
    }
}

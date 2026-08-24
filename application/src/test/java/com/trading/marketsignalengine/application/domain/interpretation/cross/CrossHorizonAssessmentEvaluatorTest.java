package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.HORIZON_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.window;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H1_SUPPORTS_CONTEXT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H5_TRIGGER_CONFIRMS;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H60_CONTEXT_DOMINANT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_ALIGNED_BULLISH;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_INSUFFICIENT_DATA;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_FROM_DOMINANT;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAlignment;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 7 safe public boundary end to end in the domain: one entry point from a snapshot + real
 * Stage 3 quality assessment + aggregate policy to a {@link CrossHorizonEvaluation} that carries both
 * the horizon assessments computed during the evaluation and the cross assessment built from exactly
 * those — plus the reflection guarantees that no public API accepts manually assembled
 * {@link HorizonAssessments} and that the interpreter and the result constructor stay non-public.
 */
class CrossHorizonAssessmentEvaluatorTest {

    private final CrossHorizonAssessmentEvaluator evaluator = new CrossHorizonAssessmentEvaluator();

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
    }

    @Test
    void fullyBullishSnapshotAlignsAcrossTheHierarchy() {
        // flow 0.60, momentum +6 bps, volatility 5 (NORMAL), confirmed bullish 1S book: every horizon
        // BULLISH strength 0.6; H60S TRENDING regime, 1S regime UNKNOWN (momentum not scoped there)
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");

        CrossHorizonEvaluation evaluation = evaluator.evaluate(snapshot, quality(snapshot), POLICY);
        CrossHorizonAssessment cross = evaluation.crossHorizonAssessment();

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH, cross.alignment());
        assertEquals(InterpretationDirection.BULLISH, cross.direction());
        assertEquals(H60S, cross.dominantHorizon());
        assertEquals(List.of(H1S, H5S, H15S, H60S), cross.participatingHorizons());
        assertEquals(List.of(), cross.conflictingHorizons());
        assertEquals(EvidenceStrength.of("0.6"), cross.evidenceStrength());
        assertEquals(MarketRegime.TRENDING, cross.regime());
        assertEquals(List.of(CROSS_HORIZON_ALIGNED_BULLISH, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_H1_SUPPORTS_CONTEXT, CROSS_HORIZON_REGIME_FROM_DOMINANT),
                cross.reasonCodes());
    }

    @Test
    void evaluationPairsTheCrossAssessmentWithExactlyTheAssessmentsItWasBuiltFrom() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "-6", "9", "0.10", "-1");
        QualityAssessment qa = quality(snapshot);

        CrossHorizonEvaluation evaluation = evaluator.evaluate(snapshot, qa, POLICY);

        // the horizon layer runs once, not re-derived differently for the cross step
        HorizonAssessments direct = new HorizonAssessmentEvaluator().evaluate(snapshot, qa, HORIZON_POLICY);
        assertEquals(direct, evaluation.horizonAssessments());
        assertEquals(new CrossHorizonInterpreter().interpret(direct), evaluation.crossHorizonAssessment());
        // every referenced horizon resolves against the returned assessments
        for (MarketHorizon participant : evaluation.crossHorizonAssessment().participatingHorizons()) {
            assertTrue(evaluation.horizonAssessments().of(participant).isEligible());
        }
    }

    @Test
    void historyGapWithoutSeniorHorizonsIsInsufficientEndToEnd() {
        // 1S/5S computed and ELIGIBLE (bullish), uncovered 15S/60S UNTRUSTED: trigger + micro alone
        MarketFeaturesSnapshot gap = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder().window1s(window("0.60")).window5s(window("0.60")).build())
                .regime(CrossFixtures.regime("6", "5"))
                .bbo(CrossFixtures.bbo("6"))
                .book(CrossFixtures.book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(gap, ASSESSED_AT, QUALITY_POLICY);

        CrossHorizonAssessment cross = evaluator.evaluate(gap, qa, POLICY).crossHorizonAssessment();

        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, cross.alignment());
        assertEquals(InterpretationDirection.UNKNOWN, cross.direction());
        assertEquals(List.of(H1S, H5S), cross.participatingHorizons());
        assertEquals(List.of(CROSS_HORIZON_INSUFFICIENT_DATA, CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR),
                cross.reasonCodes());
    }

    @Test
    void repeatedEvaluationIsDeterministicAndValueEqual() {
        MarketFeaturesSnapshot snapshot = snapshot("0.60", "6", "5", "0.60", "6");

        CrossHorizonEvaluation first = evaluator.evaluate(snapshot, quality(snapshot), POLICY);
        CrossHorizonEvaluation second =
                new CrossHorizonAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
    }

    // ------------------------------------------------------------------ safe boundary (reflection)

    @Test
    void noPublicApiAcceptsManuallyAssembledHorizonAssessments() {
        for (Method method : CrossHorizonAssessmentEvaluator.class.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == HorizonAssessments.class,
                        "public API must not accept independently produced horizon assessments: " + method);
            }
        }
        long publicMethods = java.util.Arrays.stream(CrossHorizonAssessmentEvaluator.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        assertEquals(1, publicMethods, "exactly one safe public entry point");
    }

    @Test
    void interpreterAndResultConstructorAreNotPublic() {
        assertFalse(Modifier.isPublic(CrossHorizonInterpreter.class.getModifiers()),
                "the interpreter must stay package-private");
        for (Constructor<?> constructor : CrossHorizonEvaluation.class.getDeclaredConstructors()) {
            assertFalse(Modifier.isPublic(constructor.getModifiers()),
                    "only the safe evaluator may create a CrossHorizonEvaluation: " + constructor);
        }
    }
}

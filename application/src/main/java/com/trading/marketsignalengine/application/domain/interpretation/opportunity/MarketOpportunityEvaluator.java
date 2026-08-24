package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

/**
 * Pure, deterministic Stage 8 orchestrator and the <b>single safe public entry point</b> from one
 * validated {@link MarketFeaturesSnapshot} + its Stage 3 {@link QualityAssessment} to a
 * {@link MarketOpportunityEvaluation}: the {@link CrossHorizonEvaluation} computed once for this
 * snapshot and the {@link MarketOpportunity} resolved from exactly that evaluation by the
 * package-private {@link OpportunityResolver}. No Spring, Kafka, Avro, infrastructure,
 * {@code Clock}, {@code Instant.now()} or metrics; same input + policy ⇒ value-equal result.
 *
 * <p><b>Snapshot-mixing safety.</b> There is deliberately no public API that accepts independently
 * produced {@code HorizonAssessments}, {@code CrossHorizonAssessment} or
 * {@code CrossHorizonEvaluation} — such an API would let a caller resolve an opportunity from
 * interpretation that was not derived from this snapshot and quality assessment. This evaluator
 * invokes the Stage 7 {@link CrossHorizonAssessmentEvaluator} itself, exactly once per
 * {@code evaluate(...)} call (which in turn runs the canonical snapshot/quality consistency guard),
 * hands the result to the resolver and returns all three in a {@link MarketOpportunityEvaluation}
 * whose constructor is package-private. The cross-horizon interpretation is never re-computed for
 * the opportunity step.
 */
public final class MarketOpportunityEvaluator {

    private final CrossHorizonAssessmentEvaluator crossHorizonEvaluator = new CrossHorizonAssessmentEvaluator();
    private final OpportunityResolver resolver = new OpportunityResolver();

    /**
     * The cross-horizon evaluation of one snapshot plus the market opportunity resolved from exactly
     * that evaluation. The cross-horizon evaluation runs exactly once; the resolver only reads its
     * typed result.
     */
    public MarketOpportunityEvaluation evaluate(MarketFeaturesSnapshot snapshot,
                                                QualityAssessment qualityAssessment,
                                                OpportunityInterpretationPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "opportunity policy");
        CrossHorizonEvaluation crossHorizonEvaluation =
                crossHorizonEvaluator.evaluate(snapshot, qualityAssessment, policy.crossHorizonPolicy());
        MarketOpportunity opportunity = resolver.resolve(qualityAssessment, crossHorizonEvaluation, policy);
        return new MarketOpportunityEvaluation(qualityAssessment, crossHorizonEvaluation, opportunity);
    }
}

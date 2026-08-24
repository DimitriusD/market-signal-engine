package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

/**
 * Pure, deterministic Stage 7 orchestrator and the <b>single safe public entry point</b> from one
 * validated {@link MarketFeaturesSnapshot} + its Stage 3 {@link QualityAssessment} to a
 * {@link CrossHorizonEvaluation}: the {@link HorizonAssessments} computed once for this snapshot and
 * the cross-horizon assessment built from exactly those assessments by the package-private
 * {@link CrossHorizonInterpreter}. No Spring, Kafka, Avro, infrastructure, {@code Clock},
 * {@code Instant.now()} or metrics; same input + policy ⇒ value-equal result.
 *
 * <p><b>Snapshot-mixing safety.</b> There is deliberately no public API that accepts independently
 * produced {@link HorizonAssessments} — such an API would let a caller silently interpret manually
 * assembled assessments from different snapshots together. This evaluator invokes the Stage 6
 * {@link HorizonAssessmentEvaluator} itself, exactly once per {@code evaluate(...)} call (which in
 * turn runs the canonical snapshot/quality consistency guard), hands the result to the interpreter
 * and returns both in a {@link CrossHorizonEvaluation} whose constructor is package-private. The
 * horizon evidence is never re-computed for the cross step.
 */
public final class CrossHorizonAssessmentEvaluator {

    private final HorizonAssessmentEvaluator horizonEvaluator = new HorizonAssessmentEvaluator();
    private final CrossHorizonInterpreter interpreter = new CrossHorizonInterpreter();

    /**
     * The four horizon assessments of one snapshot plus their hierarchical cross-horizon reduction.
     * The horizon evaluation runs exactly once; the interpreter only reads its typed result.
     */
    public CrossHorizonEvaluation evaluate(MarketFeaturesSnapshot snapshot,
                                           QualityAssessment qualityAssessment,
                                           CrossHorizonInterpretationPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "cross-horizon policy");
        HorizonAssessments horizonAssessments =
                horizonEvaluator.evaluate(snapshot, qualityAssessment, policy.horizonPolicy());
        return new CrossHorizonEvaluation(horizonAssessments, interpreter.interpret(horizonAssessments));
    }
}

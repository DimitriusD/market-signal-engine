package com.trading.marketsignalengine.application.domain.interpretation.cross;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;

/**
 * Test-only bridge to the package-private {@link CrossHorizonEvaluation} constructor and
 * {@link CrossHorizonInterpreter}, for tests outside this package (the Stage 8 opportunity tests)
 * that need domain-valid cross evaluations built from hand-crafted horizon assessments. Production
 * code has no such bridge on purpose — the only production path stays
 * {@link CrossHorizonAssessmentEvaluator}.
 */
public final class CrossEvaluationTestFactory {

    private CrossEvaluationTestFactory() {
    }

    /** Pairs the assessments with the real interpreter's reduction of exactly those assessments. */
    public static CrossHorizonEvaluation interpret(HorizonAssessments assessments) {
        return new CrossHorizonEvaluation(assessments, new CrossHorizonInterpreter().interpret(assessments));
    }

    /** Pairs a hand-built cross assessment with assessments; constructor consistency checks still apply. */
    public static CrossHorizonEvaluation pair(HorizonAssessments assessments, CrossHorizonAssessment cross) {
        return new CrossHorizonEvaluation(assessments, cross);
    }
}

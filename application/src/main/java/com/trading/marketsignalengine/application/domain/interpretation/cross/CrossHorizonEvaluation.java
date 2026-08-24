package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Objects;

/**
 * Immutable result of one {@link CrossHorizonAssessmentEvaluator#evaluate}: the
 * {@link HorizonAssessments} computed during the evaluation and the {@link CrossHorizonAssessment}
 * built from exactly those assessments. The constructor is deliberately package-private — only the
 * safe evaluator can pair the two, so a cross assessment can never be published next to horizon
 * assessments it was not derived from. The constructor re-checks reference consistency (every
 * participating horizon is ELIGIBLE with a known direction in the paired assessments; conflicting ⊆
 * participating; a present dominant horizon is participating, eligible and not conflicting) without
 * duplicating the interpreter's business logic. Value semantics.
 */
public final class CrossHorizonEvaluation {

    private final HorizonAssessments horizonAssessments;
    private final CrossHorizonAssessment crossHorizonAssessment;

    CrossHorizonEvaluation(HorizonAssessments horizonAssessments, CrossHorizonAssessment crossHorizonAssessment) {
        this.horizonAssessments = requireNonNull(horizonAssessments, "horizonAssessments");
        this.crossHorizonAssessment = requireNonNull(crossHorizonAssessment, "crossHorizonAssessment");
        for (MarketHorizon participant : crossHorizonAssessment.participatingHorizons()) {
            HorizonAssessment assessment = horizonAssessments.of(participant);
            require(assessment.isEligible(),
                    "participating horizon " + participant + " is " + assessment.eligibilityStatus()
                            + " in the paired horizon assessments");
            require(assessment.direction() != InterpretationDirection.UNKNOWN,
                    "participating horizon " + participant + " has direction UNKNOWN in the paired horizon assessments");
        }
        for (MarketHorizon conflicting : crossHorizonAssessment.conflictingHorizons()) {
            require(crossHorizonAssessment.participatingHorizons().contains(conflicting),
                    "conflicting horizon " + conflicting + " is not a participating horizon");
        }
        MarketHorizon dominant = crossHorizonAssessment.dominantHorizon();
        if (dominant != null) {
            require(crossHorizonAssessment.participatingHorizons().contains(dominant),
                    "dominant horizon " + dominant + " is not a participating horizon");
            require(horizonAssessments.of(dominant).isEligible(),
                    "dominant horizon " + dominant + " is not ELIGIBLE in the paired horizon assessments");
            require(!crossHorizonAssessment.conflictingHorizons().contains(dominant),
                    "dominant horizon " + dominant + " cannot be a conflicting horizon");
        }
    }

    public HorizonAssessments horizonAssessments() {
        return horizonAssessments;
    }

    public CrossHorizonAssessment crossHorizonAssessment() {
        return crossHorizonAssessment;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CrossHorizonEvaluation other
                && horizonAssessments.equals(other.horizonAssessments)
                && crossHorizonAssessment.equals(other.crossHorizonAssessment));
    }

    @Override
    public int hashCode() {
        return Objects.hash(horizonAssessments, crossHorizonAssessment);
    }

    @Override
    public String toString() {
        return "CrossHorizonEvaluation{horizonAssessments=" + horizonAssessments
                + ", crossHorizonAssessment=" + crossHorizonAssessment + '}';
    }
}

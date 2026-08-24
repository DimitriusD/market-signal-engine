package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Objects;

/**
 * Immutable result of one {@link MarketOpportunityEvaluator#evaluate}: the {@link QualityAssessment}
 * the evaluation was gated by, the {@link CrossHorizonEvaluation} computed during the evaluation and
 * the {@link MarketOpportunity} built from exactly those. The constructor is deliberately
 * package-private — only the safe evaluator can pair the three, so an opportunity can never be
 * published next to a quality assessment or cross evaluation it was not derived from. The
 * constructor re-checks reference consistency without duplicating the resolver's business logic:
 * {@code eligibleForTrading == false} ⇔ status BLOCKED; the safe path never yields
 * {@code OpportunityStatus.UNKNOWN}; a CANDIDATE's setup horizon is ELIGIBLE in the paired horizon
 * assessments and participates in the cross assessment (absence of setup horizon / strength /
 * invalidation codes on NO_OPPORTUNITY and BLOCKED is already enforced by {@link MarketOpportunity}).
 * Value semantics.
 */
public final class MarketOpportunityEvaluation {

    private final QualityAssessment qualityAssessment;
    private final CrossHorizonEvaluation crossHorizonEvaluation;
    private final MarketOpportunity marketOpportunity;

    MarketOpportunityEvaluation(QualityAssessment qualityAssessment,
                                CrossHorizonEvaluation crossHorizonEvaluation,
                                MarketOpportunity marketOpportunity) {
        this.qualityAssessment = requireNonNull(qualityAssessment, "qualityAssessment");
        this.crossHorizonEvaluation = requireNonNull(crossHorizonEvaluation, "crossHorizonEvaluation");
        this.marketOpportunity = requireNonNull(marketOpportunity, "marketOpportunity");

        OpportunityStatus status = marketOpportunity.status();
        require(status != OpportunityStatus.UNKNOWN,
                "the safe evaluation path never yields an UNKNOWN opportunity");
        if (qualityAssessment.eligibleForTrading()) {
            require(status != OpportunityStatus.BLOCKED,
                    "an eligibleForTrading snapshot cannot carry a BLOCKED opportunity");
        } else {
            require(status == OpportunityStatus.BLOCKED,
                    "a non-eligibleForTrading snapshot requires a BLOCKED opportunity, got " + status);
        }
        if (status == OpportunityStatus.CANDIDATE) {
            MarketHorizon setupHorizon = marketOpportunity.setupHorizon();
            require(crossHorizonEvaluation.horizonAssessments().of(setupHorizon).isEligible(),
                    "setup horizon " + setupHorizon + " is not ELIGIBLE in the paired horizon assessments");
            require(crossHorizonEvaluation.crossHorizonAssessment().participatingHorizons().contains(setupHorizon),
                    "setup horizon " + setupHorizon + " is not a participating horizon of the paired cross assessment");
        }
    }

    public QualityAssessment qualityAssessment() {
        return qualityAssessment;
    }

    public CrossHorizonEvaluation crossHorizonEvaluation() {
        return crossHorizonEvaluation;
    }

    public MarketOpportunity marketOpportunity() {
        return marketOpportunity;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MarketOpportunityEvaluation other
                && qualityAssessment.equals(other.qualityAssessment)
                && crossHorizonEvaluation.equals(other.crossHorizonEvaluation)
                && marketOpportunity.equals(other.marketOpportunity));
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualityAssessment, crossHorizonEvaluation, marketOpportunity);
    }

    @Override
    public String toString() {
        return "MarketOpportunityEvaluation{qualityAssessment=" + qualityAssessment
                + ", crossHorizonEvaluation=" + crossHorizonEvaluation
                + ", marketOpportunity=" + marketOpportunity + '}';
    }
}

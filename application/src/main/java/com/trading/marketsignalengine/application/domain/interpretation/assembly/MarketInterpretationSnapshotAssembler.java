package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineage;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineageFactory;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

/**
 * Pure, deterministic Stage 9 orchestrator and the <b>single safe public entry point</b> from one
 * validated {@link MarketFeaturesSnapshot} + its Stage 3 {@link QualityAssessment} to one complete
 * {@link MarketInterpretationSnapshot}. It runs the Stage 8 {@link MarketOpportunityEvaluator}
 * itself, exactly once per {@code assemble(...)} call (which keeps the canonical snapshot/quality
 * consistency guard active), builds the {@link FeatureLineage} losslessly from the snapshot via
 * {@link FeatureLineageFactory}, resolves the deterministic validity deadline (downgrading an
 * already-expired candidate to NO_OPPORTUNITY) and assembles the existing aggregate through
 * {@code MarketInterpretationSnapshot.builder()} — the id is derived only by the existing
 * deterministic generator inside the builder, never here.
 *
 * <p><b>Snapshot-mixing safety.</b> There is deliberately no public API that accepts an
 * independently produced {@code MarketOpportunityEvaluation}, {@code CrossHorizonEvaluation},
 * horizon assessments, opportunity or lineage — the opportunity evaluation carries no source feature
 * event id yet, so such an API would let a caller silently pair it with a different snapshot of
 * similar quality structure. Timing: {@code evaluatedAt} is always the source evaluation tick
 * ({@code snapshot.evaluationTs = featureLineage.sourceEvaluationAt}) — never a wall-clock read,
 * never {@code computedAt}, never the quality assessment instant. No Spring, Kafka, Avro, I/O or
 * metrics; same input + policy ⇒ value-equal snapshot with the same id.
 */
public final class MarketInterpretationSnapshotAssembler {

    private final MarketOpportunityEvaluator opportunityEvaluator = new MarketOpportunityEvaluator();
    private final InterpretationValidityResolver validityResolver = new InterpretationValidityResolver();

    /**
     * One complete interpretation snapshot of one feature snapshot: opportunity evaluation (exactly
     * once), lossless lineage, deterministic validity and the domain aggregate with its derived id.
     */
    public MarketInterpretationSnapshot assemble(MarketFeaturesSnapshot snapshot,
                                                 QualityAssessment qualityAssessment,
                                                 MarketInterpretationAssemblyPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "assembly policy");

        MarketOpportunityEvaluation opportunityEvaluation =
                opportunityEvaluator.evaluate(snapshot, qualityAssessment, policy.opportunityPolicy());
        FeatureLineage featureLineage = FeatureLineageFactory.from(snapshot);
        ValidityResolution validity = validityResolver.resolve(opportunityEvaluation, policy.validityPolicy());

        return MarketInterpretationSnapshot.builder()
                .exchange(snapshot.exchange())
                .marketType(snapshot.marketType())
                .base(snapshot.base())
                .quote(snapshot.quote())
                .symbol(snapshot.symbol())
                .instrumentId(snapshot.instrumentId())
                .evaluatedAt(featureLineage.sourceEvaluationAt())
                .validUntil(validity.validUntil())
                .interpretationQuality(opportunityEvaluation.qualityAssessment().interpretationQuality())
                .horizonAssessments(opportunityEvaluation.crossHorizonEvaluation().horizonAssessments().asList())
                .crossHorizonAssessment(opportunityEvaluation.crossHorizonEvaluation().crossHorizonAssessment())
                .marketOpportunity(validity.effectiveOpportunity())
                .featureLineage(featureLineage)
                .interpretationLineage(policy.interpretationLineage())
                .build();
    }
}

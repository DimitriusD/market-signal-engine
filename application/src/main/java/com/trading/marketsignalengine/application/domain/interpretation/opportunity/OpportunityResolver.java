package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_BLOCKED_BY_QUALITY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_BOOK_CONTRADICTS;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_CONFLICT;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_PARTIAL;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_CROSS_HORIZON_UNKNOWN;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H15_MOMENTUM_CONFIRMS;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_H60_CONTEXT_CONFIRMS;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_REGIME_UNKNOWN;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_STRENGTH_UNAVAILABLE;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_STRENGTH_ZERO;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_TRENDING_REGIME;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_VOLATILE_REGIME_ALLOWED;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes.OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure, deterministic Stage 8 reduction of one snapshot's {@link QualityAssessment} +
 * {@link CrossHorizonEvaluation} into one {@link MarketOpportunity}. Deliberately package-private:
 * production code reaches it only through {@link MarketOpportunityEvaluator}, which derives the
 * cross-horizon evaluation from one snapshot, so the snapshot/quality consistency guard stays
 * active. Reads only typed fields (eligibility, alignment, direction, availability, strength,
 * regime) — never reason-code strings; never re-runs horizon/cross evaluators, never mutates the
 * input, no clock, no probability/confidence/edge/cost.
 *
 * <h2>Decision matrix (first version — deliberately conservative)</h2>
 * <ol>
 *   <li><b>Quality gate (absolute priority)</b> — {@code eligibleForTrading == false} ⇒ BLOCKED,
 *       reasons = {@code OPPORTUNITY_BLOCKED_BY_QUALITY} + the quality reason codes in original
 *       order. An eligible snapshot can never be BLOCKED.</li>
 *   <li><b>Cross-horizon gate</b> — only {@code ALIGNED_BULLISH} / {@code ALIGNED_BEARISH} continue
 *       to the candidate gates; PARTIALLY_ALIGNED / CONFLICTING / NEUTRAL / INSUFFICIENT_DATA /
 *       UNKNOWN ⇒ NO_OPPORTUNITY with the matching cross cause. Partial alignment is never a
 *       candidate: which partial combinations are profitable is unknown before replay evidence.</li>
 *   <li><b>Independent evidence</b> — one feature family must not create an opportunity: H15S
 *       MOMENTUM (persistence) and H5S FLOW (trigger) must both be AVAILABLE on the candidate
 *       direction. NEUTRAL / MIXED / UNKNOWN / non-AVAILABLE / opposite readings never confirm.</li>
 *   <li><b>Book contradiction</b> — AVAILABLE Book evidence on an eligible participating horizon
 *       that is opposite to the candidate or MIXED ⇒ NO_OPPORTUNITY. Same-direction Book adds no
 *       strength; neutral / unavailable Book neither blocks nor confirms.</li>
 *   <li><b>Strength</b> — only {@code crossHorizonAssessment.evidenceStrength()}, carried unchanged:
 *       absent ⇒ STRENGTH_UNAVAILABLE, zero ⇒ STRENGTH_ZERO; no recomputation and no hidden minimum
 *       threshold above zero (that needs replay calibration).</li>
 *   <li><b>Regime</b> — TRENDING is continuation-compatible; VOLATILE only via the explicit
 *       {@code allowVolatileMomentumContinuation} policy switch; RANGING / QUIET / UNKNOWN / absent
 *       ⇒ NO_OPPORTUNITY.</li>
 * </ol>
 * For an aligned snapshot every failed gate is reported in deterministic order (evidence → book →
 * strength → regime), not only the first. A CANDIDATE maps to MOMENTUM_CONTINUATION LONG/SHORT with
 * {@code setupHorizon = H5S} (the trigger horizon — never H1S, never the dominant or strongest
 * horizon) and carries the full {@link OpportunityInvalidationCodes#ALL} list — at creation time no
 * invalidator is already true. {@code OpportunityStatus.UNKNOWN} is never produced for valid typed
 * input.
 */
final class OpportunityResolver {

    MarketOpportunity resolve(QualityAssessment qualityAssessment,
                              CrossHorizonEvaluation crossHorizonEvaluation,
                              OpportunityInterpretationPolicy policy) {
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(crossHorizonEvaluation, "crossHorizonEvaluation");
        requireNonNull(policy, "opportunity policy");

        if (!qualityAssessment.eligibleForTrading()) {
            List<ReasonCode> reasons = new ArrayList<>(1 + qualityAssessment.reasonCodes().size());
            reasons.add(OPPORTUNITY_BLOCKED_BY_QUALITY);
            reasons.addAll(qualityAssessment.reasonCodes());
            return MarketOpportunity.blocked(reasons);
        }

        CrossHorizonAssessment cross = crossHorizonEvaluation.crossHorizonAssessment();
        return switch (cross.alignment()) {
            case ALIGNED_BULLISH -> aligned(crossHorizonEvaluation, policy,
                    InterpretationDirection.BULLISH, OpportunitySide.LONG);
            case ALIGNED_BEARISH -> aligned(crossHorizonEvaluation, policy,
                    InterpretationDirection.BEARISH, OpportunitySide.SHORT);
            case PARTIALLY_ALIGNED -> noOpportunity(OPPORTUNITY_CROSS_HORIZON_PARTIAL);
            case CONFLICTING -> noOpportunity(OPPORTUNITY_CROSS_HORIZON_CONFLICT);
            case NEUTRAL -> noOpportunity(OPPORTUNITY_CROSS_HORIZON_NEUTRAL);
            case INSUFFICIENT_DATA -> noOpportunity(OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT);
            case UNKNOWN -> noOpportunity(OPPORTUNITY_CROSS_HORIZON_UNKNOWN);
        };
    }

    // ------------------------------------------------------------------ aligned candidate gates

    private static MarketOpportunity aligned(CrossHorizonEvaluation evaluation,
                                             OpportunityInterpretationPolicy policy,
                                             InterpretationDirection direction,
                                             OpportunitySide side) {
        HorizonAssessments assessments = evaluation.horizonAssessments();
        CrossHorizonAssessment cross = evaluation.crossHorizonAssessment();
        List<ReasonCode> causes = new ArrayList<>(5);

        boolean h15MomentumConfirms =
                confirms(assessments.of(MarketHorizon.H15S).evidence(EvidenceDimension.MOMENTUM), direction);
        if (!h15MomentumConfirms) {
            causes.add(OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED);
        }
        boolean h5FlowConfirms =
                confirms(assessments.of(MarketHorizon.H5S).evidence(EvidenceDimension.FLOW), direction);
        if (!h5FlowConfirms) {
            causes.add(OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED);
        }
        if (bookContradicts(assessments, cross.participatingHorizons(), direction)) {
            causes.add(OPPORTUNITY_BOOK_CONTRADICTS);
        }
        EvidenceStrength strength = cross.evidenceStrength();
        if (strength == null) {
            causes.add(OPPORTUNITY_STRENGTH_UNAVAILABLE);
        } else if (strength.compareTo(EvidenceStrength.MIN) == 0) {
            causes.add(OPPORTUNITY_STRENGTH_ZERO);
        }
        ReasonCode regimeBlockCause = regimeBlockCause(cross.regime(), policy);
        if (regimeBlockCause != null) {
            causes.add(regimeBlockCause);
        }

        if (!causes.isEmpty()) {
            List<ReasonCode> reasons = new ArrayList<>(1 + causes.size());
            reasons.add(OPPORTUNITY_NO_OPPORTUNITY);
            reasons.addAll(causes);
            return MarketOpportunity.noOpportunity(reasons);
        }
        return MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, side, MarketHorizon.H5S, strength,
                List.of(OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE,
                        side == OpportunitySide.LONG
                                ? OpportunityReasonCodes.OPPORTUNITY_LONG : OpportunityReasonCodes.OPPORTUNITY_SHORT,
                        OPPORTUNITY_H60_CONTEXT_CONFIRMS,
                        OPPORTUNITY_H15_MOMENTUM_CONFIRMS,
                        OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS,
                        cross.regime() == MarketRegime.VOLATILE
                                ? OPPORTUNITY_VOLATILE_REGIME_ALLOWED : OPPORTUNITY_TRENDING_REGIME),
                OpportunityInvalidationCodes.ALL);
    }

    // ------------------------------------------------------------------ gate predicates

    /** Confirmation iff the evidence exists, is AVAILABLE and reads exactly the expected direction. */
    private static boolean confirms(Optional<EvidenceAssessment> evidence, InterpretationDirection expected) {
        return evidence.map(e -> e.isAvailable() && e.direction() == expected).orElse(false);
    }

    /** AVAILABLE Book evidence on any eligible participating horizon opposite to the candidate or MIXED. */
    private static boolean bookContradicts(HorizonAssessments assessments,
                                           List<MarketHorizon> participatingHorizons,
                                           InterpretationDirection candidateDirection) {
        for (MarketHorizon horizon : participatingHorizons) {
            Optional<EvidenceAssessment> book = assessments.of(horizon).evidence(EvidenceDimension.BOOK);
            if (book.isPresent() && book.get().isAvailable()) {
                InterpretationDirection direction = book.get().direction();
                if (direction == opposite(candidateDirection) || direction == InterpretationDirection.MIXED) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The regime cause blocking a continuation candidate, or {@code null} when compatible. */
    private static ReasonCode regimeBlockCause(MarketRegime regime, OpportunityInterpretationPolicy policy) {
        if (regime == null || regime == MarketRegime.UNKNOWN) {
            return OPPORTUNITY_REGIME_UNKNOWN;
        }
        return switch (regime) {
            case TRENDING -> null;
            case VOLATILE -> policy.allowVolatileMomentumContinuation()
                    ? null : OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY;
            case RANGING, QUIET -> OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE;
            case UNKNOWN -> OPPORTUNITY_REGIME_UNKNOWN;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static MarketOpportunity noOpportunity(ReasonCode crossCause) {
        return MarketOpportunity.noOpportunity(List.of(OPPORTUNITY_NO_OPPORTUNITY, crossCause));
    }

    private static InterpretationDirection opposite(InterpretationDirection direction) {
        return direction == InterpretationDirection.BULLISH
                ? InterpretationDirection.BEARISH : InterpretationDirection.BULLISH;
    }
}

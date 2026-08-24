package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pure, deterministic Stage 9 validity reduction. Deliberately package-private: production code
 * reaches it only through {@link MarketInterpretationSnapshotAssembler}. Works exclusively in epoch
 * milliseconds on the typed timing of the {@link QualityAssessment} — no wall-clock reads, no
 * snapshot identity, no re-evaluation of opportunity/cross/horizon layers, no mutation of the input;
 * arithmetic overflow becomes an {@link IllegalArgumentException}.
 *
 * <h2>Formula</h2>
 * <pre>
 *   validUntil = sourceEvaluationAt + baseValidity(status/type, setup horizon) − deductions
 *   remaining  = validUntil − assessedAt          (exclusive deadline: remaining ≤ 0 ⇒ expired)
 * </pre>
 * Deductions: {@code publicationSafetyBuffer} always; {@code degradedQualityAdjustment} (quality
 * DEGRADED) and {@code volatileRegimeAdjustment} (cross regime VOLATILE) for candidates only.
 * {@code TimingAssessment.featureAgeMs} already equals {@code assessedAt − sourceEvaluationAt} and
 * contains all upstream computation + transport + engine latency, so processing latency is
 * deliberately <b>not</b> deducted a second time — anchoring the deadline to the source tick and
 * comparing against {@code assessedAt} charges the elapsed time exactly once.
 *
 * <h2>Expired candidate</h2>
 * A candidate with {@code remaining <= 0} is downgraded to a fresh
 * {@code MarketOpportunity.noOpportunity} carrying exactly {@code OPPORTUNITY_NO_OPPORTUNITY} +
 * {@code OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY} (no candidate reasons, no invalidation codes), and
 * {@code validUntil} is re-derived from {@code noOpportunityBaseValidity − publicationSafetyBuffer}.
 * The original evaluation is immutable and never touched. A candidate with at least 1 ms remaining
 * is passed through unchanged. {@code OpportunityStatus.UNKNOWN} and a candidate type without a
 * configured base validity fail fast — never a silent arbitrary TTL.
 */
final class InterpretationValidityResolver {

    ValidityResolution resolve(MarketOpportunityEvaluation opportunityEvaluation,
                               InterpretationValidityPolicy policy) {
        requireNonNull(opportunityEvaluation, "opportunityEvaluation");
        return resolve(opportunityEvaluation.qualityAssessment(),
                opportunityEvaluation.crossHorizonEvaluation().crossHorizonAssessment().regime(),
                opportunityEvaluation.marketOpportunity(),
                policy);
    }

    /** Typed-parts overload for unit tests; {@code crossRegime} may be {@code null} (not assessed). */
    ValidityResolution resolve(QualityAssessment qualityAssessment,
                               MarketRegime crossRegime,
                               MarketOpportunity opportunity,
                               InterpretationValidityPolicy policy) {
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(opportunity, "opportunity");
        requireNonNull(policy, "validity policy");
        long sourceEvaluationMs = qualityAssessment.timing().sourceEvaluationAt().toEpochMilli();
        long assessedMs = qualityAssessment.timing().assessedAt().toEpochMilli();

        return switch (opportunity.status()) {
            case BLOCKED -> nonCandidate(policy.blockedBaseValidity(), policy, opportunity,
                    sourceEvaluationMs, assessedMs);
            case NO_OPPORTUNITY -> nonCandidate(policy.noOpportunityBaseValidity(), policy, opportunity,
                    sourceEvaluationMs, assessedMs);
            case CANDIDATE -> candidate(qualityAssessment, crossRegime, opportunity, policy,
                    sourceEvaluationMs, assessedMs);
            case UNKNOWN -> throw new IllegalArgumentException(
                    "the safe pipeline never produces an UNKNOWN opportunity; no validity is defined for it");
        };
    }

    // ------------------------------------------------------------------ per-status resolution

    private static ValidityResolution nonCandidate(Duration baseValidity,
                                                   InterpretationValidityPolicy policy,
                                                   MarketOpportunity opportunity,
                                                   long sourceEvaluationMs,
                                                   long assessedMs) {
        long validUntilMs = deadlineMs(sourceEvaluationMs,
                baseValidity.toMillis() - policy.publicationSafetyBuffer().toMillis());
        return new ValidityResolution(Instant.ofEpochMilli(validUntilMs), opportunity,
                remainingMs(validUntilMs, assessedMs), false);
    }

    private static ValidityResolution candidate(QualityAssessment qualityAssessment,
                                                MarketRegime crossRegime,
                                                MarketOpportunity opportunity,
                                                InterpretationValidityPolicy policy,
                                                long sourceEvaluationMs,
                                                long assessedMs) {
        require(opportunity.type() == OpportunityType.MOMENTUM_CONTINUATION,
                "no base validity is configured for candidate opportunity type " + opportunity.type()
                        + " — refusing to guess a TTL");
        long baseMs = policy.momentumContinuationBaseValidityOf(opportunity.setupHorizon()).toMillis();
        long deductionsMs = policy.publicationSafetyBuffer().toMillis();
        if (qualityAssessment.status() == InterpretationQualityStatus.DEGRADED) {
            deductionsMs = Math.addExact(deductionsMs, policy.degradedQualityAdjustment().toMillis());
        }
        if (crossRegime == MarketRegime.VOLATILE) {
            deductionsMs = Math.addExact(deductionsMs, policy.volatileRegimeAdjustment().toMillis());
        }
        long candidateValidUntilMs = deadlineMs(sourceEvaluationMs, baseMs - deductionsMs);
        long remainingMs = remainingMs(candidateValidUntilMs, assessedMs);
        if (remainingMs > 0L) {
            return new ValidityResolution(Instant.ofEpochMilli(candidateValidUntilMs), opportunity,
                    remainingMs, false);
        }
        // exclusive deadline reached or passed at assessment time: the candidate must not stay active
        MarketOpportunity downgraded = MarketOpportunity.noOpportunity(List.of(
                OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY,
                InterpretationValidityReasonCodes.OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY));
        long noOpportunityValidUntilMs = deadlineMs(sourceEvaluationMs,
                policy.noOpportunityBaseValidity().toMillis() - policy.publicationSafetyBuffer().toMillis());
        return new ValidityResolution(Instant.ofEpochMilli(noOpportunityValidUntilMs), downgraded,
                remainingMs(noOpportunityValidUntilMs, assessedMs), true);
    }

    // ------------------------------------------------------------------ millisecond arithmetic

    private static long deadlineMs(long sourceEvaluationMs, long adjustedBaseValidityMs) {
        try {
            return Math.addExact(sourceEvaluationMs, adjustedBaseValidityMs);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("validUntil overflows epoch milliseconds: " + sourceEvaluationMs
                    + " + " + adjustedBaseValidityMs, e);
        }
    }

    private static long remainingMs(long validUntilMs, long assessedMs) {
        try {
            return Math.subtractExact(validUntilMs, assessedMs);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("remaining validity overflows long milliseconds: " + validUntilMs
                    + " - " + assessedMs, e);
        }
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.EVENT_TIME;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.OPPORTUNITY_EVALUATOR;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.OPPORTUNITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.VALIDITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.quality;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInvalidationCodes;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.HorizonEligibilities;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.TimingAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.TimingStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 9 validity formula on the package-private resolver:
 * {@code validUntil = sourceEvaluationAt + base − deductions} (exclusive) and
 * {@code remaining = validUntil − assessedAt}; base selection per status/type/setup horizon,
 * candidate-only degraded/volatile deductions, single-charged feature age, exclusive-boundary
 * expiration with the NO_OPPORTUNITY downgrade, and fail-fast on UNKNOWN / unconfigured types.
 */
class InterpretationValidityResolverTest {

    private final InterpretationValidityResolver resolver = new InterpretationValidityResolver();

    @Test
    void rejectsNullInputs() {
        MarketOpportunityEvaluation evaluation = evaluation(AssemblyFixtures.bullishSnapshot(), ASSESSED_AT);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null, VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(evaluation, null));
        QualityAssessment qa = quality(AssemblyFixtures.bullishSnapshot());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(null, MarketRegime.TRENDING, candidate(), VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(qa, MarketRegime.TRENDING, null, VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(qa, MarketRegime.TRENDING, candidate(), null));
    }

    // ------------------------------------------------------------------ base selection (17.2)

    @Test
    void candidateUsesTheSetupHorizonBaseValidity() {
        MarketOpportunityEvaluation evaluation = evaluation(AssemblyFixtures.bullishSnapshot(), ASSESSED_AT);

        ValidityResolution resolution = resolver.resolve(evaluation, VALIDITY_POLICY);

        // H5S base 500 − buffer 100, anchored to the source tick
        assertEquals(EVENT_TIME.plusMillis(400), resolution.validUntil());
        assertEquals(300L, resolution.remainingValidityMs(), "assessed 100 ms after the tick");
        assertFalse(resolution.candidateExpired());
        assertEquals(evaluation.marketOpportunity(), resolution.effectiveOpportunity(),
                "an active candidate passes through unchanged");
    }

    @Test
    void noOpportunityAndBlockedUseTheirOwnBaseValidities() {
        ValidityResolution noOpportunity =
                resolver.resolve(evaluation(AssemblyFixtures.conflictSnapshot(), ASSESSED_AT), VALIDITY_POLICY);
        assertEquals(EVENT_TIME.plusMillis(200), noOpportunity.validUntil(), "300 − 100 buffer");
        assertEquals(100L, noOpportunity.remainingValidityMs());
        assertFalse(noOpportunity.candidateExpired());

        ValidityResolution blocked =
                resolver.resolve(evaluation(AssemblyFixtures.unsafeSnapshot(), ASSESSED_AT), VALIDITY_POLICY);
        assertEquals(OpportunityStatus.BLOCKED, blocked.effectiveOpportunity().status(), "fixture self-check");
        assertEquals(EVENT_TIME.plusMillis(150), blocked.validUntil(), "250 − 100 buffer");
        assertEquals(50L, blocked.remainingValidityMs());
    }

    @Test
    void unknownOpportunityAndUnconfiguredCandidateTypeFailFast() {
        QualityAssessment qa = quality(AssemblyFixtures.bullishSnapshot());

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(qa, MarketRegime.TRENDING,
                MarketOpportunity.unknown(List.of()), VALIDITY_POLICY));
        MarketOpportunity reversal = MarketOpportunity.candidate(OpportunityType.SHORT_TERM_REVERSAL,
                OpportunitySide.LONG, MarketHorizon.H5S, EvidenceStrength.of("0.6"),
                List.of(OpportunityReasonCodes.OPPORTUNITY_LONG), OpportunityInvalidationCodes.ALL);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(qa, MarketRegime.TRENDING, reversal, VALIDITY_POLICY),
                "no base validity is configured for SHORT_TERM_REVERSAL — never guess a TTL");
    }

    // ------------------------------------------------------------------ adjustments (17.2)

    @Test
    void degradedAdjustmentAppliesOnlyToCandidates() {
        // DEGRADED but eligible quality, full bullish market → candidate: 500 − 100 − 50
        ValidityResolution candidate = resolver.resolve(
                evaluation(AssemblyFixtures.degradedEligibleSnapshot(), ASSESSED_AT), VALIDITY_POLICY);
        assertEquals(OpportunityStatus.CANDIDATE, candidate.effectiveOpportunity().status(), "fixture self-check");
        assertEquals(EVENT_TIME.plusMillis(350), candidate.validUntil());

        // the same DEGRADED quality with a NO_OPPORTUNITY verdict: buffer only
        QualityAssessment degraded = quality(AssemblyFixtures.degradedEligibleSnapshot());
        ValidityResolution noOpportunity = resolver.resolve(degraded, MarketRegime.TRENDING,
                MarketOpportunity.noOpportunity(List.of(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY)),
                VALIDITY_POLICY);
        assertEquals(EVENT_TIME.plusMillis(200), noOpportunity.validUntil());
    }

    @Test
    void volatileAdjustmentAppliesOnlyToCandidates() {
        QualityAssessment ok = quality(AssemblyFixtures.bullishSnapshot());

        ValidityResolution candidate =
                resolver.resolve(ok, MarketRegime.VOLATILE, candidate(), VALIDITY_POLICY);
        assertEquals(EVENT_TIME.plusMillis(375), candidate.validUntil(), "500 − 100 − 25");

        ValidityResolution noOpportunity = resolver.resolve(ok, MarketRegime.VOLATILE,
                MarketOpportunity.noOpportunity(List.of(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY)),
                VALIDITY_POLICY);
        assertEquals(EVENT_TIME.plusMillis(200), noOpportunity.validUntil(), "buffer only");
    }

    @Test
    void degradedAndVolatileAdjustmentsSumSafely() {
        QualityAssessment degraded = quality(AssemblyFixtures.degradedEligibleSnapshot());

        ValidityResolution resolution =
                resolver.resolve(degraded, MarketRegime.VOLATILE, candidate(), VALIDITY_POLICY);

        assertEquals(EVENT_TIME.plusMillis(325), resolution.validUntil(), "500 − 100 − 50 − 25");
    }

    // ------------------------------------------------------------------ feature age (17.3)

    @Test
    void remainingValidityFollowsTheAssessedInstant() {
        record AgeCase(long assessedOffsetMs, long expectedRemainingMs, boolean expired) {
        }
        // computedAt = source tick, so timing stays FRESH even at age 0 (no negative latency)
        var snapshot = AssemblyFixtures.bullishSnapshot().toBuilder().computedAt(EVENT_TIME).build();
        for (AgeCase c : List.of(
                new AgeCase(0L, 400L, false),
                new AgeCase(1L, 399L, false),
                new AgeCase(399L, 1L, false),   // exactly 1 ms remaining → still active
                new AgeCase(400L, 0L, true),    // assessedAt == deadline → expired (exclusive boundary)
                new AgeCase(401L, -1L, true))) {
            QualityAssessment qa = quality(snapshot, EVENT_TIME.plusMillis(c.assessedOffsetMs()));
            ValidityResolution resolution =
                    resolver.resolve(qa, MarketRegime.TRENDING, candidate(), VALIDITY_POLICY);

            assertEquals(c.expired(), resolution.candidateExpired(), "offset " + c.assessedOffsetMs());
            if (!c.expired()) {
                assertEquals(EVENT_TIME.plusMillis(400), resolution.validUntil());
                assertEquals(c.expectedRemainingMs(), resolution.remainingValidityMs());
                assertEquals(OpportunityStatus.CANDIDATE, resolution.effectiveOpportunity().status());
            } else {
                assertEquals(OpportunityStatus.NO_OPPORTUNITY, resolution.effectiveOpportunity().status());
            }
        }
    }

    @Test
    void processingLatencyIsNeverDeductedASecondTime() {
        // same source tick, same assessedAt, different computedAt → different processing latency
        Instant assessedAt = EVENT_TIME.plusMillis(300);
        QualityAssessment fastPath = quality(AssemblyFixtures.bullishSnapshot(), assessedAt);
        QualityAssessment slowPath = quality(AssemblyFixtures.bullishSnapshot().toBuilder()
                .computedAt(EVENT_TIME.plusMillis(250)).build(), assessedAt);
        assertNotEquals(fastPath.timing().processingLatencyMs(), slowPath.timing().processingLatencyMs(),
                "fixture self-check: the paths really differ in processing latency");
        assertEquals(fastPath.timing().featureAgeMs(), slowPath.timing().featureAgeMs(),
                "feature age depends only on the source tick and assessedAt");

        ValidityResolution fast = resolver.resolve(fastPath, MarketRegime.TRENDING, candidate(), VALIDITY_POLICY);
        ValidityResolution slow = resolver.resolve(slowPath, MarketRegime.TRENDING, candidate(), VALIDITY_POLICY);

        assertEquals(fast.validUntil(), slow.validUntil(), "latency is already inside the feature age");
        assertEquals(fast.remainingValidityMs(), slow.remainingValidityMs());
    }

    // ------------------------------------------------------------------ expiration downgrade (17.4)

    @Test
    void expiredCandidateIsDowngradedToNoOpportunityWithoutMutatingTheEvaluation() {
        MarketOpportunityEvaluation evaluation =
                evaluation(AssemblyFixtures.bullishSnapshot(), EVENT_TIME.plusMillis(400));
        assertEquals(OpportunityStatus.CANDIDATE, evaluation.marketOpportunity().status(), "fixture self-check");

        ValidityResolution resolution = resolver.resolve(evaluation, VALIDITY_POLICY);

        assertTrue(resolution.candidateExpired());
        MarketOpportunity effective = resolution.effectiveOpportunity();
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, effective.status());
        assertEquals(OpportunityType.NONE, effective.type());
        assertEquals(OpportunitySide.NONE, effective.side());
        assertNull(effective.setupHorizon());
        assertNull(effective.evidenceStrength());
        assertEquals(List.of(), effective.invalidationCodes());
        assertEquals(List.of(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY,
                InterpretationValidityReasonCodes.OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY), effective.reasonCodes());
        // validUntil re-derived from the NO_OPPORTUNITY base: 300 − 100
        assertEquals(EVENT_TIME.plusMillis(200), resolution.validUntil());
        assertEquals(-200L, resolution.remainingValidityMs(), "already 200 ms past the downgraded deadline");
        // the original evaluation is untouched
        assertEquals(OpportunityStatus.CANDIDATE, evaluation.marketOpportunity().status());
    }

    // ------------------------------------------------------------------ arithmetic and determinism

    @Test
    void epochOverflowBecomesIllegalArgument() {
        Instant nearMax = Instant.ofEpochMilli(Long.MAX_VALUE - 5);
        TimingAssessment timing = new TimingAssessment(nearMax, nearMax, nearMax, 0L, 0L,
                TimingStatus.FRESH, List.of());
        QualityAssessment qa = new QualityAssessment(FeatureQualityStatus.OK,
                InterpretationQuality.ok(List.of()), timing,
                HorizonEligibilities.uniform(HorizonEligibility.eligible()), List.of(), false);

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(qa, MarketRegime.TRENDING, candidate(), VALIDITY_POLICY));
    }

    @Test
    void sameInputsResolveToValueEqualResults() {
        MarketOpportunityEvaluation evaluation = evaluation(AssemblyFixtures.bullishSnapshot(), ASSESSED_AT);

        assertEquals(resolver.resolve(evaluation, VALIDITY_POLICY),
                new InterpretationValidityResolver().resolve(evaluation, VALIDITY_POLICY));
    }

    // ------------------------------------------------------------------ helpers

    private static MarketOpportunityEvaluation evaluation(
            com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot snapshot,
            Instant assessedAt) {
        return OPPORTUNITY_EVALUATOR.evaluate(snapshot, quality(snapshot, assessedAt), OPPORTUNITY_POLICY);
    }

    /** A hand-built H5S momentum-continuation candidate for the typed-parts overload. */
    private static MarketOpportunity candidate() {
        return MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, OpportunitySide.LONG,
                MarketHorizon.H5S, EvidenceStrength.of("0.6"),
                List.of(OpportunityReasonCodes.OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE),
                OpportunityInvalidationCodes.ALL);
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.ALLOW_VOLATILE_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.alignedAssessments;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.alignedEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.conflictingEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.eligibleQuality;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.evaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.evidence;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.horizon;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.insufficientEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.neutralEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.notAvailable;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.partiallyAlignedEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.unknownEvaluation;
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
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAlignment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 8 decision matrix on the package-private resolver: absolute quality gate, conservative
 * cross-alignment gate (only ALIGNED_* continues), independent H15S-MOMENTUM + H5S-FLOW evidence
 * requirement, Book contradiction gate, unchanged cross-horizon strength, explicit regime policy,
 * deterministic reason/invalidation ordering. All inputs are typed domain fixtures; the cross
 * evaluations are reduced by the real Stage 7 interpreter.
 */
class OpportunityResolverTest {

    private final OpportunityResolver resolver = new OpportunityResolver();

    // ------------------------------------------------------------------ 15.1 quality gate

    @Test
    void nonEligibleQualityIsBlockedWithQualityReasonsAppended() {
        QualityAssessment quality = OpportunityFixtures.unsafeBlockedQuality();

        MarketOpportunity opportunity = resolver.resolve(quality, alignedEvaluation(InterpretationDirection.BULLISH), POLICY);

        assertEquals(OpportunityStatus.BLOCKED, opportunity.status());
        assertEquals(OpportunityType.NONE, opportunity.type());
        assertEquals(OpportunitySide.NONE, opportunity.side());
        assertNull(opportunity.setupHorizon());
        assertNull(opportunity.evidenceStrength());
        assertEquals(List.of(), opportunity.invalidationCodes());
        assertEquals(OPPORTUNITY_BLOCKED_BY_QUALITY, opportunity.reasonCodes().get(0));
        assertFalse(quality.reasonCodes().isEmpty(), "the fixture quality must explain itself");
        assertEquals(quality.reasonCodes(),
                opportunity.reasonCodes().subList(1, opportunity.reasonCodes().size()),
                "quality reasons carried through in their original order");
    }

    @Test
    void everyNonEligibleQualityVariantIsBlocked() {
        for (QualityAssessment quality : List.of(
                OpportunityFixtures.unsafeBlockedQuality(),
                OpportunityFixtures.noDataBlockedQuality(),
                OpportunityFixtures.unknownQuality())) {
            MarketOpportunity opportunity =
                    resolver.resolve(quality, alignedEvaluation(InterpretationDirection.BULLISH), POLICY);
            assertEquals(OpportunityStatus.BLOCKED, opportunity.status(), quality.status().toString());
            assertFalse(quality.eligibleForTrading());
        }
    }

    @Test
    void alignedCrossCannotBypassQualityBlock() {
        // a perfect bullish market interpretation must not matter when quality forbids trading
        MarketOpportunity opportunity = resolver.resolve(OpportunityFixtures.noDataBlockedQuality(),
                alignedEvaluation(InterpretationDirection.BULLISH), ALLOW_VOLATILE_POLICY);
        assertEquals(OpportunityStatus.BLOCKED, opportunity.status());
    }

    @Test
    void eligibleQualityIsNeverBlocked() {
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(), conflictingEvaluation(), POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status(), "an honest negative, not a block");
    }

    @Test
    void degradedButEligibleQualityCanProduceCandidate() {
        QualityAssessment degraded = OpportunityFixtures.degradedEligibleQuality();
        assertTrue(degraded.eligibleForTrading());

        MarketOpportunity opportunity =
                resolver.resolve(degraded, alignedEvaluation(InterpretationDirection.BULLISH), POLICY);

        assertEquals(OpportunityStatus.CANDIDATE, opportunity.status());
    }

    // ------------------------------------------------------------------ 15.2 cross-alignment matrix

    @Test
    void onlyAlignedStatesReachTheCandidateGates() {
        record NonAligned(CrossHorizonEvaluation evaluation, CrossHorizonAlignment alignment, ReasonCode cause) {
        }
        for (NonAligned scenario : List.of(
                new NonAligned(partiallyAlignedEvaluation(), CrossHorizonAlignment.PARTIALLY_ALIGNED,
                        OPPORTUNITY_CROSS_HORIZON_PARTIAL),
                new NonAligned(conflictingEvaluation(), CrossHorizonAlignment.CONFLICTING,
                        OPPORTUNITY_CROSS_HORIZON_CONFLICT),
                new NonAligned(neutralEvaluation(), CrossHorizonAlignment.NEUTRAL,
                        OPPORTUNITY_CROSS_HORIZON_NEUTRAL),
                new NonAligned(insufficientEvaluation(), CrossHorizonAlignment.INSUFFICIENT_DATA,
                        OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT),
                new NonAligned(unknownEvaluation(), CrossHorizonAlignment.UNKNOWN,
                        OPPORTUNITY_CROSS_HORIZON_UNKNOWN))) {
            assertEquals(scenario.alignment(), scenario.evaluation().crossHorizonAssessment().alignment(),
                    "fixture self-check");

            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(), scenario.evaluation(), POLICY);

            assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status(), scenario.alignment().toString());
            assertEquals(OpportunityType.NONE, opportunity.type());
            assertEquals(OpportunitySide.NONE, opportunity.side());
            assertNull(opportunity.setupHorizon());
            assertNull(opportunity.evidenceStrength());
            assertEquals(List.of(), opportunity.invalidationCodes());
            assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, scenario.cause()), opportunity.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ 15.3 LONG / SHORT mapping

    @Test
    void alignedBullishWithAllGatesPassingIsLongCandidateOnH5s() {
        MarketOpportunity opportunity =
                resolver.resolve(eligibleQuality(), alignedEvaluation(InterpretationDirection.BULLISH), POLICY);

        assertEquals(OpportunityStatus.CANDIDATE, opportunity.status());
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, opportunity.type());
        assertEquals(OpportunitySide.LONG, opportunity.side());
        assertEquals(H5S, opportunity.setupHorizon());
        assertEquals(EvidenceStrength.of("0.6"), opportunity.evidenceStrength());
        assertEquals(List.of(OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE, OpportunityReasonCodes.OPPORTUNITY_LONG,
                OPPORTUNITY_H60_CONTEXT_CONFIRMS, OPPORTUNITY_H15_MOMENTUM_CONFIRMS,
                OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS, OPPORTUNITY_TRENDING_REGIME), opportunity.reasonCodes());
        assertEquals(OpportunityInvalidationCodes.ALL, opportunity.invalidationCodes());
    }

    @Test
    void alignedBearishWithAllGatesPassingIsShortCandidateOnH5s() {
        MarketOpportunity opportunity =
                resolver.resolve(eligibleQuality(), alignedEvaluation(InterpretationDirection.BEARISH), POLICY);

        assertEquals(OpportunityStatus.CANDIDATE, opportunity.status());
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, opportunity.type());
        assertEquals(OpportunitySide.SHORT, opportunity.side());
        assertEquals(H5S, opportunity.setupHorizon());
        assertEquals(EvidenceStrength.of("0.6"), opportunity.evidenceStrength());
        assertEquals(List.of(OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE, OpportunityReasonCodes.OPPORTUNITY_SHORT,
                OPPORTUNITY_H60_CONTEXT_CONFIRMS, OPPORTUNITY_H15_MOMENTUM_CONFIRMS,
                OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS, OPPORTUNITY_TRENDING_REGIME), opportunity.reasonCodes());
        assertEquals(OpportunityInvalidationCodes.ALL, opportunity.invalidationCodes());
    }

    // ------------------------------------------------------------------ 15.4 independent evidence

    @Test
    void h15MomentumMustConfirmTheCandidateDirection() {
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        for (EvidenceAssessment momentum : nonConfirmingReadings(EvidenceDimension.MOMENTUM, bullish)) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                            momentum,
                            evidence(EvidenceDimension.FLOW, bullish, "0.6"),
                            evidence(EvidenceDimension.BOOK, bullish, "0.6"))),
                    POLICY);

            assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status(), String.valueOf(momentum));
            assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED),
                    opportunity.reasonCodes());
        }
    }

    @Test
    void h5FlowTriggerMustConfirmTheCandidateDirection() {
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        for (EvidenceAssessment flow : nonConfirmingReadings(EvidenceDimension.FLOW, bullish)) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                            evidence(EvidenceDimension.MOMENTUM, bullish, "0.6"),
                            flow,
                            evidence(EvidenceDimension.BOOK, bullish, "0.6"))),
                    POLICY);

            assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status(), String.valueOf(flow));
            assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED),
                    opportunity.reasonCodes());
        }
    }

    @Test
    void oneEvidenceFamilyAloneNeverCreatesACandidate() {
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        // flow-only alignment: no H15S momentum persistence
        MarketOpportunity flowOnly = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                        null,
                        evidence(EvidenceDimension.FLOW, bullish, "0.6"),
                        null)),
                POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, flowOnly.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED),
                flowOnly.reasonCodes());

        // momentum-only alignment: no H5S flow trigger
        MarketOpportunity momentumOnly = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, bullish, "0.6"),
                        null,
                        null)),
                POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, momentumOnly.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED),
                momentumOnly.reasonCodes());

        // both missing: both gate causes, deterministic order
        MarketOpportunity neither = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING, null, null, null)),
                POLICY);
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED,
                OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED), neither.reasonCodes());
    }

    // ------------------------------------------------------------------ 15.5 book gate

    @Test
    void oppositeOrMixedBookIsNoOpportunity() {
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        for (EvidenceAssessment book : List.of(
                evidence(EvidenceDimension.BOOK, InterpretationDirection.BEARISH, "0.6"),
                evidence(EvidenceDimension.BOOK, InterpretationDirection.MIXED, null))) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                            evidence(EvidenceDimension.MOMENTUM, bullish, "0.6"),
                            evidence(EvidenceDimension.FLOW, bullish, "0.6"),
                            book)),
                    POLICY);

            assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status(), String.valueOf(book));
            assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_BOOK_CONTRADICTS),
                    opportunity.reasonCodes());
        }
    }

    @Test
    void adverseBookOnAnyParticipatingHorizonBlocksTheCandidate() {
        // adverse book evidence attached to the H5S trigger horizon instead of H1S
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        HorizonAssessments assessments = HorizonAssessments.of(
                horizon(H1S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, bullish, "0.6")),
                horizon(H5S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, bullish, "0.6"),
                        evidence(EvidenceDimension.BOOK, InterpretationDirection.BEARISH, "0.6")),
                horizon(H15S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, bullish, "0.6")),
                horizon(H60S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, bullish, "0.6")));

        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(), evaluation(assessments), POLICY);

        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_BOOK_CONTRADICTS), opportunity.reasonCodes());
    }

    @Test
    void sameDirectionNeutralOrUnavailableBookNeitherBlocksNorBoosts() {
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        for (EvidenceAssessment book : new EvidenceAssessment[]{
                evidence(EvidenceDimension.BOOK, bullish, "1"),
                evidence(EvidenceDimension.BOOK, InterpretationDirection.NEUTRAL, "0"),
                evidence(EvidenceDimension.BOOK, InterpretationDirection.UNKNOWN, null),
                notAvailable(EvidenceDimension.BOOK, EvidenceAvailabilityStatus.UNAVAILABLE),
                null}) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedAssessments(bullish, "0.6", MarketRegime.TRENDING,
                            evidence(EvidenceDimension.MOMENTUM, bullish, "0.6"),
                            evidence(EvidenceDimension.FLOW, bullish, "0.6"),
                            book)),
                    POLICY);

            assertEquals(OpportunityStatus.CANDIDATE, opportunity.status(), String.valueOf(book));
            assertEquals(EvidenceStrength.of("0.6"), opportunity.evidenceStrength(),
                    "book never changes the carried strength: " + book);
        }
    }

    @Test
    void bookAloneNeverCreatesACandidate() {
        // a bullish book inside an otherwise neutral market stays NO_OPPORTUNITY (fixture has one)
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(), neutralEvaluation(), POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_CROSS_HORIZON_NEUTRAL),
                opportunity.reasonCodes());
    }

    // ------------------------------------------------------------------ 15.6 strength gate

    @Test
    void absentCrossStrengthIsNoOpportunity() {
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(InterpretationDirection.BULLISH, null, MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6"),
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6"),
                        null)),
                POLICY);

        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_STRENGTH_UNAVAILABLE),
                opportunity.reasonCodes());
    }

    @Test
    void zeroCrossStrengthIsNoOpportunity() {
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(InterpretationDirection.BULLISH, "0", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6"),
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6"),
                        null)),
                POLICY);

        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_STRENGTH_ZERO), opportunity.reasonCodes());
    }

    @Test
    void candidateCarriesTheExactCrossStrengthWithNoHiddenMinimum() {
        for (String strength : List.of("0.35", "0.01", "1")) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedAssessments(InterpretationDirection.BULLISH, strength, MarketRegime.TRENDING,
                            evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6"),
                            evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6"),
                            null)),
                    POLICY);

            assertEquals(OpportunityStatus.CANDIDATE, opportunity.status(),
                    "any strength above zero passes — no hidden minimum: " + strength);
            assertEquals(EvidenceStrength.of(strength), opportunity.evidenceStrength(),
                    "carried unchanged, never averaged or recomputed");
        }
    }

    // ------------------------------------------------------------------ 15.7 regime gate

    @Test
    void trendingAllowsAndRangingQuietUnknownBlockTheCandidate() {
        record RegimeCase(MarketRegime regime, OpportunityStatus status, ReasonCode cause) {
        }
        for (RegimeCase c : List.of(
                new RegimeCase(MarketRegime.TRENDING, OpportunityStatus.CANDIDATE, null),
                new RegimeCase(MarketRegime.RANGING, OpportunityStatus.NO_OPPORTUNITY,
                        OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE),
                new RegimeCase(MarketRegime.QUIET, OpportunityStatus.NO_OPPORTUNITY,
                        OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE),
                new RegimeCase(MarketRegime.UNKNOWN, OpportunityStatus.NO_OPPORTUNITY,
                        OPPORTUNITY_REGIME_UNKNOWN))) {
            MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                    evaluation(alignedConfirmedAssessments(InterpretationDirection.BULLISH, c.regime())), POLICY);

            assertEquals(c.status(), opportunity.status(), c.regime().toString());
            if (c.cause() != null) {
                assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, c.cause()), opportunity.reasonCodes());
            }
        }
    }

    @Test
    void volatileRegimeIsControlledByTheExplicitPolicySwitch() {
        HorizonAssessments volatileMarket =
                alignedConfirmedAssessments(InterpretationDirection.BULLISH, MarketRegime.VOLATILE);

        MarketOpportunity blocked = resolver.resolve(eligibleQuality(), evaluation(volatileMarket), POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, blocked.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY),
                blocked.reasonCodes());

        MarketOpportunity allowed =
                resolver.resolve(eligibleQuality(), evaluation(volatileMarket), ALLOW_VOLATILE_POLICY);
        assertEquals(OpportunityStatus.CANDIDATE, allowed.status());
        assertTrue(allowed.reasonCodes().contains(OPPORTUNITY_VOLATILE_REGIME_ALLOWED));
    }

    @Test
    void absentRegimeBlocksTheCandidateLikeUnknown() {
        // an aligned cross assessment with a null regime is only constructible as a hand-built fixture
        HorizonAssessments assessments =
                alignedConfirmedAssessments(InterpretationDirection.BULLISH, MarketRegime.TRENDING);
        CrossHorizonEvaluation withNullRegime = OpportunityFixtures.pair(assessments,
                com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment.alignedBullish(
                        EvidenceStrength.of("0.6"), H60S, List.of(H1S, H5S, H15S, H60S), null, List.of()));

        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(), withNullRegime, POLICY);

        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY, OPPORTUNITY_REGIME_UNKNOWN), opportunity.reasonCodes());
    }

    @Test
    void regimeNeverFlipsTheSide() {
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                evaluation(alignedConfirmedAssessments(InterpretationDirection.BEARISH, MarketRegime.VOLATILE)),
                ALLOW_VOLATILE_POLICY);

        assertEquals(OpportunitySide.SHORT, opportunity.side());
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, opportunity.type());
    }

    // ------------------------------------------------------------------ reason ordering across gates

    @Test
    void everyFailedGateIsReportedInDeterministicOrder() {
        // aligned, but: no H15 momentum, no H5 flow, adverse book, no strength, ranging regime
        MarketOpportunity opportunity = resolver.resolve(eligibleQuality(),
                evaluation(alignedAssessments(InterpretationDirection.BULLISH, null, MarketRegime.RANGING,
                        null,
                        null,
                        evidence(EvidenceDimension.BOOK, InterpretationDirection.BEARISH, "0.6"))),
                POLICY);

        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OPPORTUNITY_NO_OPPORTUNITY,
                OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED,
                OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED,
                OPPORTUNITY_BOOK_CONTRADICTS,
                OPPORTUNITY_STRENGTH_UNAVAILABLE,
                OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE), opportunity.reasonCodes());
    }

    // ------------------------------------------------------------------ 15.8 invalidation codes

    @Test
    void candidateCarriesTheFullImmutableInvalidatorList() {
        MarketOpportunity opportunity =
                resolver.resolve(eligibleQuality(), alignedEvaluation(InterpretationDirection.BULLISH), POLICY);

        assertEquals(OpportunityInvalidationCodes.ALL, opportunity.invalidationCodes());
        assertEquals(opportunity.invalidationCodes().size(),
                opportunity.invalidationCodes().stream().distinct().count(), "unique");
        assertThrows(UnsupportedOperationException.class,
                () -> opportunity.invalidationCodes().add(OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_QUALITY));
    }

    @Test
    void blockedAndNoOpportunityCarryNoInvalidationCodes() {
        assertEquals(List.of(), resolver.resolve(OpportunityFixtures.unsafeBlockedQuality(),
                alignedEvaluation(InterpretationDirection.BULLISH), POLICY).invalidationCodes());
        assertEquals(List.of(), resolver.resolve(eligibleQuality(), conflictingEvaluation(), POLICY)
                .invalidationCodes());
    }

    // ------------------------------------------------------------------ 15.10 determinism

    @Test
    void sameInputsGiveValueEqualResultsAndInputsAreNotMutated() {
        QualityAssessment quality = eligibleQuality();
        CrossHorizonEvaluation evaluation = alignedEvaluation(InterpretationDirection.BULLISH);
        List<ReasonCode> qualityReasonsBefore = List.copyOf(quality.reasonCodes());

        MarketOpportunity first = resolver.resolve(quality, evaluation, POLICY);
        MarketOpportunity second = new OpportunityResolver().resolve(quality, evaluation, POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(qualityReasonsBefore, quality.reasonCodes(), "input not mutated");
        assertEquals(alignedEvaluation(InterpretationDirection.BULLISH), evaluation, "input not mutated");
        assertThrows(UnsupportedOperationException.class,
                () -> first.reasonCodes().add(OPPORTUNITY_NO_OPPORTUNITY));
    }

    @Test
    void unknownStatusIsNeverReturnedForValidInputs() {
        for (CrossHorizonEvaluation evaluation : List.of(
                alignedEvaluation(InterpretationDirection.BULLISH),
                alignedEvaluation(InterpretationDirection.BEARISH),
                partiallyAlignedEvaluation(), conflictingEvaluation(), neutralEvaluation(),
                insufficientEvaluation(), unknownEvaluation())) {
            assertFalse(resolver.resolve(eligibleQuality(), evaluation, POLICY).status() == OpportunityStatus.UNKNOWN);
        }
    }

    @Test
    void rejectsNullInputs() {
        CrossHorizonEvaluation evaluation = alignedEvaluation(InterpretationDirection.BULLISH);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null, evaluation, POLICY));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(eligibleQuality(), null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(eligibleQuality(), evaluation, null));
    }

    // ------------------------------------------------------------------ helpers

    /** Readings that must never confirm {@code expected}: neutral, opposite, MIXED, UNKNOWN, non-AVAILABLE. */
    private static List<EvidenceAssessment> nonConfirmingReadings(EvidenceDimension dimension,
                                                                  InterpretationDirection expected) {
        InterpretationDirection opposite = expected == InterpretationDirection.BULLISH
                ? InterpretationDirection.BEARISH : InterpretationDirection.BULLISH;
        return List.of(
                evidence(dimension, InterpretationDirection.NEUTRAL, "0"),
                evidence(dimension, opposite, "0.6"),
                evidence(dimension, InterpretationDirection.MIXED, null),
                evidence(dimension, InterpretationDirection.UNKNOWN, null),
                notAvailable(dimension, EvidenceAvailabilityStatus.UNAVAILABLE),
                notAvailable(dimension, EvidenceAvailabilityStatus.UNTRUSTED),
                notAvailable(dimension, EvidenceAvailabilityStatus.FAILED));
    }

    /** A fully aligned, fully evidence-confirmed set with the given (dominant) regime on every horizon. */
    private static HorizonAssessments alignedConfirmedAssessments(InterpretationDirection direction,
                                                                  MarketRegime regime) {
        return alignedAssessments(direction, "0.6", regime,
                evidence(EvidenceDimension.MOMENTUM, direction, "0.6"),
                evidence(EvidenceDimension.FLOW, direction, "0.6"),
                evidence(EvidenceDimension.BOOK, direction, "0.6"));
    }
}

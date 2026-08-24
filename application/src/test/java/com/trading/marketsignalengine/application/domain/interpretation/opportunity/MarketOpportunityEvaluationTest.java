package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.alignedEvaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.eligibleQuality;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.evaluation;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.evidence;
import static com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityFixtures.horizon;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 8 result object: pairs quality, cross evaluation and opportunity with reference
 * consistency (BLOCKED ⇔ not eligibleForTrading; never UNKNOWN; a CANDIDATE's setup horizon is
 * eligible and participating) without re-running any resolver logic. Value semantics.
 */
class MarketOpportunityEvaluationTest {

    private static final List<ReasonCode> NO_REASONS = List.of();

    @Test
    void pairsTheThreeResultsWithValueSemantics() {
        QualityAssessment quality = eligibleQuality();
        CrossHorizonEvaluation cross = alignedEvaluation(InterpretationDirection.BULLISH);
        MarketOpportunity opportunity = candidate();

        MarketOpportunityEvaluation evaluation = new MarketOpportunityEvaluation(quality, cross, opportunity);

        assertEquals(quality, evaluation.qualityAssessment());
        assertEquals(cross, evaluation.crossHorizonEvaluation());
        assertEquals(opportunity, evaluation.marketOpportunity());
        MarketOpportunityEvaluation same = new MarketOpportunityEvaluation(quality, cross, opportunity);
        assertEquals(evaluation, same);
        assertEquals(evaluation.hashCode(), same.hashCode());
        assertEquals(evaluation.toString(), same.toString());
        assertNotEquals(evaluation, new MarketOpportunityEvaluation(quality, cross,
                MarketOpportunity.noOpportunity(List.of(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY))));
    }

    @Test
    void rejectsNullComponents() {
        QualityAssessment quality = eligibleQuality();
        CrossHorizonEvaluation cross = alignedEvaluation(InterpretationDirection.BULLISH);
        MarketOpportunity opportunity = candidate();

        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(null, cross, opportunity));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(quality, null, opportunity));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(quality, cross, null));
    }

    @Test
    void blockedStatusMustMatchTradingEligibilityBothWays() {
        CrossHorizonEvaluation cross = alignedEvaluation(InterpretationDirection.BULLISH);
        QualityAssessment blockedQuality = OpportunityFixtures.unsafeBlockedQuality();

        // eligible quality can never carry BLOCKED
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(
                eligibleQuality(), cross, MarketOpportunity.blocked(NO_REASONS)));
        // non-eligible quality must carry BLOCKED
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(
                blockedQuality, cross, MarketOpportunity.noOpportunity(NO_REASONS)));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(
                blockedQuality, cross, candidate()));
        // the consistent pairing stands
        MarketOpportunityEvaluation blocked =
                new MarketOpportunityEvaluation(blockedQuality, cross, MarketOpportunity.blocked(NO_REASONS));
        assertEquals(MarketOpportunity.blocked(NO_REASONS), blocked.marketOpportunity());
    }

    @Test
    void unknownOpportunityIsNeverAcceptedOnTheSafePath() {
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunityEvaluation(
                eligibleQuality(), alignedEvaluation(InterpretationDirection.BULLISH),
                MarketOpportunity.unknown(NO_REASONS)));
    }

    @Test
    void candidateSetupHorizonMustBeEligibleAndParticipating() {
        // H5S eligible but direction UNKNOWN → not a participating horizon
        InterpretationDirection bullish = InterpretationDirection.BULLISH;
        HorizonAssessments withoutH5Participation = HorizonAssessments.of(
                horizon(H1S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, bullish, "0.6")),
                horizon(H5S, InterpretationDirection.UNKNOWN, null, null),
                horizon(H15S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, bullish, "0.6")),
                horizon(H60S, bullish, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, bullish, "0.6")));
        CrossHorizonEvaluation cross = evaluation(withoutH5Participation);
        assertTrue(cross.horizonAssessments().of(H5S).isEligible(), "fixture self-check");

        assertThrows(IllegalArgumentException.class,
                () -> new MarketOpportunityEvaluation(eligibleQuality(), cross, candidate()),
                "setup horizon H5S does not participate in the paired cross assessment");

        // H5S not even eligible
        HorizonAssessments withoutH5Eligibility = HorizonAssessments.of(
                horizon(H1S, bullish, "0.6", MarketRegime.TRENDING),
                OpportunityFixtures.unavailable(H5S),
                horizon(H15S, bullish, "0.6", MarketRegime.TRENDING),
                horizon(H60S, bullish, "0.6", MarketRegime.TRENDING));
        CrossHorizonEvaluation crossNoH5 = evaluation(withoutH5Eligibility);

        assertThrows(IllegalArgumentException.class,
                () -> new MarketOpportunityEvaluation(eligibleQuality(), crossNoH5, candidate()));
    }

    private static MarketOpportunity candidate() {
        return MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, OpportunitySide.LONG, H5S,
                EvidenceStrength.of("0.6"),
                List.of(OpportunityReasonCodes.OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE),
                OpportunityInvalidationCodes.ALL);
    }
}

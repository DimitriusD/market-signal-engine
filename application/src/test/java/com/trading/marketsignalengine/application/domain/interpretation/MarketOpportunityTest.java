package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.ALIGNED_WITH_TRIGGER;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.FLOW_FLIPPED;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.NEUTRAL_MARKET;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.QUALITY_BLOCKED;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.strength;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarketOpportunityTest {

    @Test
    void candidateRequiresSideTypeAndSetupHorizon() {
        MarketOpportunity candidate = MarketOpportunity.candidate(OpportunityType.SHORT_TERM_REVERSAL, OpportunitySide.SHORT,
                H5S, null, List.of(ALIGNED_WITH_TRIGGER), List.of(FLOW_FLIPPED));
        assertTrue(candidate.isCandidate());
        assertNull(candidate.evidenceStrength(), "strength is optional for a candidate");
        assertEquals(List.of(FLOW_FLIPPED), candidate.invalidationCodes());
        assertEquals(strength("0.65"), InterpretationFixtures.candidateLong5s().evidenceStrength());

        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION,
                OpportunitySide.NONE, H5S, null, List.of(), List.of()), "CANDIDATE + side NONE");
        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.candidate(OpportunityType.NONE,
                OpportunitySide.LONG, H5S, null, List.of(), List.of()), "CANDIDATE + type NONE");
        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.candidate(OpportunityType.UNKNOWN,
                OpportunitySide.LONG, H5S, null, List.of(), List.of()), "CANDIDATE + type UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION,
                OpportunitySide.LONG, null, null, List.of(), List.of()), "CANDIDATE without setupHorizon");
        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION,
                OpportunitySide.LONG, H5S, null, List.of(), java.util.Arrays.asList(FLOW_FLIPPED, null)), "null invalidation code");
    }

    @Test
    void noOpportunityAndBlockedAreEmptyOfSetupSemantics() {
        for (MarketOpportunity opportunity : List.of(
                MarketOpportunity.noOpportunity(List.of(NEUTRAL_MARKET)),
                MarketOpportunity.blocked(List.of(QUALITY_BLOCKED)))) {
            assertFalse(opportunity.isCandidate());
            assertEquals(OpportunitySide.NONE, opportunity.side());
            assertEquals(OpportunityType.NONE, opportunity.type());
            assertNull(opportunity.setupHorizon());
            assertNull(opportunity.evidenceStrength());
            assertEquals(List.of(), opportunity.invalidationCodes());
            assertEquals(1, opportunity.reasonCodes().size());
        }
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, MarketOpportunity.noOpportunity(List.of()).status());
        assertEquals(OpportunityStatus.BLOCKED, MarketOpportunity.blocked(List.of()).status());

        for (OpportunityStatus status : List.of(OpportunityStatus.NO_OPPORTUNITY, OpportunityStatus.BLOCKED)) {
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.NONE,
                    OpportunitySide.LONG, null, null, List.of(), List.of()), status + " + side LONG");
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.MOMENTUM_CONTINUATION,
                    OpportunitySide.NONE, null, null, List.of(), List.of()), status + " + type");
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.UNKNOWN,
                    OpportunitySide.NONE, null, null, List.of(), List.of()), status + " + type UNKNOWN");
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.NONE,
                    OpportunitySide.NONE, H5S, null, List.of(), List.of()), status + " + setupHorizon");
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.NONE,
                    OpportunitySide.NONE, null, strength("0.2"), List.of(), List.of()), status + " + strength");
            assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(status, OpportunityType.NONE,
                    OpportunitySide.NONE, null, null, List.of(), List.of(FLOW_FLIPPED)), status + " + invalidationCodes");
        }
    }

    @Test
    void unknownIsAFallbackWithoutSetupSemantics() {
        MarketOpportunity unknown = MarketOpportunity.unknown(List.of());
        assertEquals(OpportunityStatus.UNKNOWN, unknown.status());
        assertEquals(OpportunityType.UNKNOWN, unknown.type());
        assertEquals(OpportunitySide.NONE, unknown.side());
        new MarketOpportunity(OpportunityStatus.UNKNOWN, OpportunityType.NONE, OpportunitySide.NONE, null, null, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(OpportunityStatus.UNKNOWN,
                OpportunityType.MOMENTUM_CONTINUATION, OpportunitySide.NONE, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(OpportunityStatus.UNKNOWN,
                OpportunityType.UNKNOWN, OpportunitySide.SHORT, null, null, List.of(), List.of()));
    }

    @Test
    void nullsAndDuplicatesAreRejectedAndListsAreImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(null, OpportunityType.NONE,
                OpportunitySide.NONE, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(OpportunityStatus.BLOCKED, null,
                OpportunitySide.NONE, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketOpportunity(OpportunityStatus.BLOCKED, OpportunityType.NONE,
                null, null, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> MarketOpportunity.blocked(List.of(QUALITY_BLOCKED, QUALITY_BLOCKED)));
        MarketOpportunity candidate = InterpretationFixtures.candidateLong5s();
        assertThrows(UnsupportedOperationException.class, () -> candidate.reasonCodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> candidate.invalidationCodes().clear());
    }
}

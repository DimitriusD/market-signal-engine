package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Stage 8 aggregate policy: a real version, the full Stage 7 cross-horizon policy and the single
 * explicit VOLATILE-continuation switch, nothing else.
 */
class OpportunityInterpretationPolicyTest {

    @Test
    void carriesVersionCrossPolicyAndVolatileSwitch() {
        OpportunityInterpretationPolicy policy =
                new OpportunityInterpretationPolicy("opportunity-v1", OpportunityFixtures.CROSS_POLICY, true);
        assertEquals("opportunity-v1", policy.policyVersion());
        assertEquals(OpportunityFixtures.CROSS_POLICY, policy.crossHorizonPolicy());
        assertTrue(policy.allowVolatileMomentumContinuation());
        assertFalse(new OpportunityInterpretationPolicy("opportunity-v1", OpportunityFixtures.CROSS_POLICY, false)
                .allowVolatileMomentumContinuation());
    }

    @Test
    void rejectsMissingVersionOrCrossPolicy() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpportunityInterpretationPolicy(null, OpportunityFixtures.CROSS_POLICY, false));
        assertThrows(IllegalArgumentException.class,
                () -> new OpportunityInterpretationPolicy("  ", OpportunityFixtures.CROSS_POLICY, false));
        assertThrows(IllegalArgumentException.class,
                () -> new OpportunityInterpretationPolicy("placeholder", OpportunityFixtures.CROSS_POLICY, false),
                "a placeholder is not a version");
        assertThrows(IllegalArgumentException.class,
                () -> new OpportunityInterpretationPolicy("opportunity-v1", null, false));
    }
}

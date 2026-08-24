package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The Stage 7 aggregate policy: a real version plus the full Stage 6 horizon policy, nothing else. */
class CrossHorizonInterpretationPolicyTest {

    @Test
    void carriesVersionAndHorizonPolicy() {
        CrossHorizonInterpretationPolicy policy =
                new CrossHorizonInterpretationPolicy("cross-v1", CrossFixtures.HORIZON_POLICY);
        assertEquals("cross-v1", policy.policyVersion());
        assertEquals(CrossFixtures.HORIZON_POLICY, policy.horizonPolicy());
    }

    @Test
    void rejectsMissingVersionOrHorizonPolicy() {
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonInterpretationPolicy(null, CrossFixtures.HORIZON_POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonInterpretationPolicy("  ", CrossFixtures.HORIZON_POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonInterpretationPolicy("unknown", CrossFixtures.HORIZON_POLICY),
                "a placeholder is not a version");
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonInterpretationPolicy("cross-v1", null));
    }
}

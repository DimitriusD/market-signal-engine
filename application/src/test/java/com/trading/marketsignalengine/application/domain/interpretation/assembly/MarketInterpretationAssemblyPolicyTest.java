package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.interpretation.InterpretationLineage;
import org.junit.jupiter.api.Test;

/**
 * The Stage 9 aggregate policy: real lineage identity plus the Stage 8 opportunity policy and the
 * validity policy; the interpretation lineage is exactly the declared version + config hash — never
 * generated.
 */
class MarketInterpretationAssemblyPolicyTest {

    @Test
    void carriesPoliciesAndExposesTheInterpretationLineage() {
        MarketInterpretationAssemblyPolicy policy = AssemblyFixtures.POLICY;

        assertEquals("mse-interpretation-fixture-v1", policy.interpretationVersion());
        assertEquals("cfg-interpretation-fixture-1", policy.interpretationConfigHash());
        assertEquals(AssemblyFixtures.OPPORTUNITY_POLICY, policy.opportunityPolicy());
        assertEquals(AssemblyFixtures.VALIDITY_POLICY, policy.validityPolicy());
        assertEquals(new InterpretationLineage("mse-interpretation-fixture-v1", "cfg-interpretation-fixture-1"),
                policy.interpretationLineage());
    }

    @Test
    void rejectsMissingOrPlaceholderLineageAndMissingPolicies() {
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                null, "cfg-1", AssemblyFixtures.OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                "unknown", "cfg-1", AssemblyFixtures.OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY),
                "a placeholder is not a version");
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                "mse-v1", "  ", AssemblyFixtures.OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                "mse-v1", "n/a", AssemblyFixtures.OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY),
                "a placeholder is not a config hash");
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                "mse-v1", "cfg-1", null, AssemblyFixtures.VALIDITY_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationAssemblyPolicy(
                "mse-v1", "cfg-1", AssemblyFixtures.OPPORTUNITY_POLICY, null));
    }
}

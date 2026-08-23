package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.BOOK_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.FLOW_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.MOMENTUM_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonFixtures.VOLATILITY_POLICY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Aggregate policy invariants: versioned lineage, all four nested policies mandatory, pure composition. */
class HorizonInterpretationPolicyTest {

    @Test
    void validPolicyExposesTheNestedPoliciesUnchanged() {
        HorizonInterpretationPolicy policy = new HorizonInterpretationPolicy("horizon-v1",
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY);

        assertEquals("horizon-v1", policy.policyVersion());
        assertSame(FLOW_POLICY, policy.flowPolicy(), "pure composition — nothing copied or rebuilt");
        assertSame(MOMENTUM_POLICY, policy.momentumPolicy());
        assertSame(VOLATILITY_POLICY, policy.volatilityPolicy());
        assertSame(BOOK_POLICY, policy.bookPolicy());
        assertEquals(policy, new HorizonInterpretationPolicy("horizon-v1",
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY));
        assertNotEquals(policy, new HorizonInterpretationPolicy("horizon-v2",
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown", "TODO", "n/a", "placeholder"})
    void blankOrPlaceholderVersionIsRejected(String version) {
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy(version,
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY));
    }

    @Test
    void nullVersionOrNestedPolicyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy(null,
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy("v1",
                null, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy("v1",
                FLOW_POLICY, null, VOLATILITY_POLICY, BOOK_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy("v1",
                FLOW_POLICY, MOMENTUM_POLICY, null, BOOK_POLICY));
        assertThrows(IllegalArgumentException.class, () -> new HorizonInterpretationPolicy("v1",
                FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, null));
    }
}

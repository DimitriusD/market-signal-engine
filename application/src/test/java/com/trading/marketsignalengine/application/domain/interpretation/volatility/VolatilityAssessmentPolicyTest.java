package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.P15S;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.P1S;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.P5S;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.P60S;
import static com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityFixtures.bd;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Volatility policy invariants: versioned lineage, exactly the four canonical horizons, strictly
 * ordered non-negative bounds, immutability and value equality.
 */
class VolatilityAssessmentPolicyTest {

    @Test
    void validPolicyExposesHorizonsInCanonicalOrder() {
        VolatilityAssessmentPolicy policy = VolatilityAssessmentPolicy.of("volatility-v1", P1S, P5S, P15S, P60S);

        assertEquals("volatility-v1", policy.policyVersion());
        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(policy.asMap().keySet()));
        assertEquals(List.of(P1S, P5S, P15S, P60S), policy.asList());
        assertEquals(P15S, policy.of(H15S));
        assertEquals(policy, new VolatilityAssessmentPolicy("volatility-v1", List.of(P60S, P1S, P15S, P5S)),
                "input order does not matter");
        assertEquals(policy.hashCode(),
                VolatilityAssessmentPolicy.of("volatility-v1", P1S, P5S, P15S, P60S).hashCode());
        assertNotEquals(policy, VolatilityAssessmentPolicy.of("volatility-v2", P1S, P5S, P15S, P60S));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown", "TODO", "n/a", "placeholder"})
    void blankOrPlaceholderVersionIsRejected(String version) {
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityAssessmentPolicy.of(version, P1S, P5S, P15S, P60S));
    }

    @Test
    void nullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VolatilityAssessmentPolicy.of(null, P1S, P5S, P15S, P60S));
        assertThrows(IllegalArgumentException.class, () -> VolatilityAssessmentPolicy.of("v1", null, P5S, P15S, P60S));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityAssessmentPolicy("v1", null));
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessmentPolicy("v1", Arrays.asList(P1S, null, P15S, P60S)));
    }

    @Test
    void duplicateAndMissingHorizonsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessmentPolicy("v1", List.of(P1S, P1S, P15S, P60S)), "duplicate 1S");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessmentPolicy("v1", List.of(P1S, P15S, P60S)));
        assertTrue(missing.getMessage().contains("H5S"), missing.getMessage());
    }

    @Test
    void invalidBoundOrderingIsRejected() {
        // negative low bound
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, bd("-1"), bd("8"), bd("15")));
        // low == normal
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, bd("8"), bd("8"), bd("15")));
        // low > normal
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, bd("9"), bd("8"), bd("15")));
        // normal == high
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, bd("3"), bd("15"), bd("15")));
        // normal > high
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, bd("3"), bd("16"), bd("15")));
        // nulls
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(null, bd("3"), bd("8"), bd("15")));
        assertThrows(IllegalArgumentException.class,
                () -> VolatilityHorizonPolicy.of(H5S, null, bd("8"), bd("15")));
        // a zero low bound is valid: LOW then only contains exactly zero volatility
        VolatilityHorizonPolicy zeroLow = VolatilityHorizonPolicy.of(H5S, bd("0"), bd("8"), bd("15"));
        assertEquals(bd("0"), zeroLow.lowUpperBoundBps());
    }

    @Test
    void policyViewsAreImmutable() {
        VolatilityAssessmentPolicy policy = VolatilityAssessmentPolicy.of("v1", P1S, P5S, P15S, P60S);

        assertThrows(UnsupportedOperationException.class, () -> policy.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> policy.asList().add(P1S));
        assertThrows(IllegalArgumentException.class, () -> policy.of(null));
    }
}

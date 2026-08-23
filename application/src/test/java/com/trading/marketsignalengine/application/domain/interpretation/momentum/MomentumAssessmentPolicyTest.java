package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.P15S;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.P5S;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.P60S;
import static com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumFixtures.bd;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Momentum policy invariants: versioned lineage, exactly the three scoped horizons (no 1S fiction),
 * per-horizon threshold geometry, immutability and value equality.
 */
class MomentumAssessmentPolicyTest {

    @Test
    void validPolicyExposesScopedHorizonsInCanonicalOrder() {
        MomentumAssessmentPolicy policy = MomentumAssessmentPolicy.of("momentum-v1", P5S, P15S, P60S);

        assertEquals("momentum-v1", policy.policyVersion());
        assertEquals(List.of(H5S, H15S, H60S), List.copyOf(policy.asMap().keySet()));
        assertEquals(List.of(P5S, P15S, P60S), policy.asList());
        assertEquals(P15S, policy.of(H15S));
        assertEquals(policy, new MomentumAssessmentPolicy("momentum-v1", List.of(P60S, P5S, P15S)),
                "input order does not matter");
        assertEquals(policy.hashCode(), MomentumAssessmentPolicy.of("momentum-v1", P5S, P15S, P60S).hashCode());
        assertNotEquals(policy, MomentumAssessmentPolicy.of("momentum-v2", P5S, P15S, P60S));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown", "TODO", "n/a", "placeholder"})
    void blankOrPlaceholderVersionIsRejected(String version) {
        assertThrows(IllegalArgumentException.class, () -> MomentumAssessmentPolicy.of(version, P5S, P15S, P60S));
    }

    @Test
    void nullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MomentumAssessmentPolicy.of(null, P5S, P15S, P60S));
        assertThrows(IllegalArgumentException.class, () -> MomentumAssessmentPolicy.of("v1", null, P15S, P60S));
        assertThrows(IllegalArgumentException.class, () -> new MomentumAssessmentPolicy("v1", null));
        assertThrows(IllegalArgumentException.class, () -> new MomentumAssessmentPolicy("v1",
                java.util.Arrays.asList(P5S, null, P60S)));
    }

    @Test
    void duplicateAndMissingHorizonsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MomentumAssessmentPolicy("v1", List.of(P5S, P5S, P60S)), "duplicate 5S");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new MomentumAssessmentPolicy("v1", List.of(P5S, P60S)));
        assertTrue(missing.getMessage().contains("H15S"), missing.getMessage());
    }

    @Test
    void h1sPolicyCannotExist() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H1S, bd("2"), bd("-2"), bd("10"), bd("50")));
        assertTrue(ex.getMessage().contains("H1S") || ex.getMessage().contains("1S"), ex.getMessage());
    }

    @Test
    void lookupOfUnscopedHorizonFailsFast() {
        MomentumAssessmentPolicy policy = MomentumAssessmentPolicy.of("v1", P5S, P15S, P60S);

        assertThrows(IllegalArgumentException.class, () -> policy.of(H1S), "momentum has no 1S policy");
        assertThrows(IllegalArgumentException.class, () -> policy.of(null));
    }

    @Test
    void invalidThresholdGeometryIsRejected() {
        // bullish must be positive
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("0"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("-1"), bd("-2"), bd("10"), bd("50")));
        // bearish must be negative
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("0"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("1"), bd("10"), bd("50")));
        // full strength must be positive
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("-2"), bd("0"), bd("50")));
        // maxSafe must be strictly above full strength
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("-2"), bd("10"), bd("10")));
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("-2"), bd("10"), bd("9")));
        // full strength must cover both directional thresholds
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("15"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, bd("2"), bd("-15"), bd("10"), bd("50")));
        // nulls
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(null, bd("2"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class,
                () -> MomentumHorizonPolicy.of(H5S, null, bd("-2"), bd("10"), bd("50")));
        // boundary geometry that is still valid: full == bullish == abs(bearish)
        MomentumHorizonPolicy boundary = MomentumHorizonPolicy.of(H5S, bd("10"), bd("-10"), bd("10"), bd("10.01"));
        assertEquals(bd("10"), boundary.fullStrengthAbsMoveBps());
    }

    @Test
    void policyViewsAreImmutable() {
        MomentumAssessmentPolicy policy = MomentumAssessmentPolicy.of("v1", P5S, P15S, P60S);

        assertThrows(UnsupportedOperationException.class, () -> policy.asMap().remove(H5S));
        assertThrows(UnsupportedOperationException.class, () -> policy.asList().add(P5S));
    }
}

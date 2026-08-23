package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.P15S;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.P1S;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.P5S;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.P60S;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.POLICY_VERSION;
import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.bd;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exactly four horizon policies, canonical order, fail-fast on missing / duplicate, versioned, immutable. */
class FlowAssessmentPolicyTest {

    @Test
    void storesExactlyOnePolicyPerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        FlowAssessmentPolicy shuffled = new FlowAssessmentPolicy(POLICY_VERSION, List.of(P60S, P1S, P15S, P5S));

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(shuffled.asMap().keySet()));
        assertEquals(List.of(P1S, P5S, P15S, P60S), shuffled.asList());
        assertSame(P15S, shuffled.of(H15S));
        assertEquals(POLICY_VERSION, shuffled.policyVersion());
        assertEquals(POLICY, shuffled);
        assertEquals(POLICY.hashCode(), shuffled.hashCode());
    }

    @Test
    void missingHorizonFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new FlowAssessmentPolicy(POLICY_VERSION, List.of(P1S, P5S, P15S)));
        assertTrue(ex.getMessage().contains("missing horizon H60S"), ex.getMessage());

        assertThrows(IllegalArgumentException.class, () -> new FlowAssessmentPolicy(POLICY_VERSION, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FlowAssessmentPolicy(POLICY_VERSION, null));
        assertThrows(IllegalArgumentException.class, () -> FlowAssessmentPolicy.of(POLICY_VERSION, P1S, null, P15S, P60S));
    }

    @Test
    void duplicateHorizonFailsFast() {
        FlowHorizonPolicy another5s = FlowHorizonPolicy.of(H5S, bd("0.5"), bd("-0.5"), 20, 10, bd("0.1"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new FlowAssessmentPolicy(POLICY_VERSION, List.of(P1S, P5S, another5s, P15S, P60S)));
        assertTrue(ex.getMessage().contains("duplicate horizon H5S"), ex.getMessage());

        // a duplicate that "replaces" a missing one is still a duplicate, not a silent fill-in
        assertThrows(IllegalArgumentException.class,
                () -> new FlowAssessmentPolicy(POLICY_VERSION, List.of(P1S, P5S, another5s, P15S)));
        assertThrows(IllegalArgumentException.class,
                () -> new FlowAssessmentPolicy(POLICY_VERSION, Arrays.asList(P1S, null, P15S, P60S)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "unknown", "TODO", "tbd", "n/a", "null", "none", "placeholder", " Placeholder "})
    void policyVersionMustBeRealLineage(String version) {
        assertThrows(IllegalArgumentException.class, () -> FlowAssessmentPolicy.of(version, P1S, P5S, P15S, P60S));
    }

    @Test
    void policyVersionIsMandatoryAndPartOfEquality() {
        assertThrows(IllegalArgumentException.class, () -> FlowAssessmentPolicy.of(null, P1S, P5S, P15S, P60S));

        FlowAssessmentPolicy v2 = FlowAssessmentPolicy.of("flow-fixture-v2", P1S, P5S, P15S, P60S);
        assertNotEquals(POLICY, v2, "same thresholds under a different version are a different policy");
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        assertThrows(UnsupportedOperationException.class, () -> POLICY.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> POLICY.asMap().put(H1S, P5S));
        assertThrows(UnsupportedOperationException.class, () -> POLICY.asList().add(P1S));
        assertThrows(IllegalArgumentException.class, () -> POLICY.of(null));
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(horizon, POLICY.of(horizon).horizon());
        }
    }

    @Test
    void mutatingTheInputCollectionAfterConstructionHasNoEffect() {
        List<FlowHorizonPolicy> input = new ArrayList<>(List.of(P1S, P5S, P15S, P60S));
        FlowAssessmentPolicy policy = new FlowAssessmentPolicy(POLICY_VERSION, input);
        input.clear();

        assertEquals(4, policy.asList().size());
        assertEquals(POLICY, policy);
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exactly four horizon assessments, canonical order, key ↔ {@code assessment.horizon()} agreement,
 * fail-fast lookups, immutable.
 */
class HorizonAssessmentsTest {

    private static final ReasonCode REASON = ReasonCode.of("TEST_REASON");

    private static HorizonAssessment assessment(MarketHorizon horizon) {
        return HorizonAssessment.unavailable(horizon, List.of(REASON));
    }

    private static final HorizonAssessment A1S = assessment(H1S);
    private static final HorizonAssessment A5S = assessment(H5S);
    private static final HorizonAssessment A15S = assessment(H15S);
    private static final HorizonAssessment A60S = assessment(H60S);

    @Test
    void storesExactlyOnePerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        Map<MarketHorizon, HorizonAssessment> unordered = new HashMap<>();
        unordered.put(H60S, A60S);
        unordered.put(H1S, A1S);
        unordered.put(H15S, A15S);
        unordered.put(H5S, A5S);

        HorizonAssessments assessments = new HorizonAssessments(unordered);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(assessments.asMap().keySet()));
        assertEquals(List.of(A1S, A5S, A15S, A60S), assessments.asList());
        assertSame(A15S, assessments.of(H15S));
        assertEquals(assessments, HorizonAssessments.of(A1S, A5S, A15S, A60S));
        assertEquals(assessments.hashCode(), HorizonAssessments.of(A1S, A5S, A15S, A60S).hashCode());
        assertNotEquals(assessments, HorizonAssessments.of(A1S, A5S, A15S,
                HorizonAssessment.failed(H60S, List.of(REASON))));
    }

    @Test
    void missingHorizonFailsFast() {
        Map<MarketHorizon, HorizonAssessment> partial = new EnumMap<>(MarketHorizon.class);
        partial.put(H1S, A1S);
        partial.put(H5S, A5S);
        partial.put(H15S, A15S);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new HorizonAssessments(partial));
        assertTrue(ex.getMessage().contains("H60S"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new HorizonAssessments(null));
        assertThrows(IllegalArgumentException.class, () -> HorizonAssessments.of(A1S, null, A15S, A60S));
    }

    @Test
    void keyThatDoesNotMatchTheAssessmentHorizonIsRejected() {
        Map<MarketHorizon, HorizonAssessment> mismatched = new EnumMap<>(MarketHorizon.class);
        mismatched.put(H1S, A1S);
        mismatched.put(H5S, A15S); // a 15S assessment filed under the 5S key
        mismatched.put(H15S, A15S);
        mismatched.put(H60S, A60S);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new HorizonAssessments(mismatched));
        assertTrue(ex.getMessage().contains("filed under H5S"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> HorizonAssessments.of(A1S, A5S, A5S, A60S));
    }

    @Test
    void entriesBeyondTheFourCanonicalKeysAreRejectedNotSilentlyDropped() {
        Map<MarketHorizon, HorizonAssessment> withNullKey = new HashMap<>();
        withNullKey.put(H1S, A1S);
        withNullKey.put(H5S, A5S);
        withNullKey.put(H15S, A15S);
        withNullKey.put(H60S, A60S);
        withNullKey.put(null, A5S);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new HorizonAssessments(withNullKey));
        assertTrue(ex.getMessage().contains("exactly the four canonical horizons"), ex.getMessage());
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        HorizonAssessments assessments = HorizonAssessments.of(A1S, A5S, A15S, A60S);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().put(H1S, A1S));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().add(A1S));
        assertThrows(IllegalArgumentException.class, () -> assessments.of(null));
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(horizon, assessments.of(horizon).horizon());
        }
    }

    @Test
    void mutatingTheInputMapAfterConstructionHasNoEffect() {
        Map<MarketHorizon, HorizonAssessment> input = new EnumMap<>(MarketHorizon.class);
        input.put(H1S, A1S);
        input.put(H5S, A5S);
        input.put(H15S, A15S);
        input.put(H60S, A60S);
        HorizonAssessments assessments = new HorizonAssessments(input);
        input.put(H60S, HorizonAssessment.failed(H60S, List.of(REASON)));
        input.clear();

        assertSame(A60S, assessments.of(H60S));
        assertEquals(4, assessments.asList().size());
    }
}

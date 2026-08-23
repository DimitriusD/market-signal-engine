package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exactly four typed volatility assessments, canonical order, fail-fast lookups, immutable. */
class VolatilityAssessmentsTest {

    private static final VolatilityAssessment LOW = VolatilityAssessment.available(VolatilityLevel.LOW,
            List.of(VolatilityReasonCodes.VOLATILITY_LOW));
    private static final VolatilityAssessment NORMAL = VolatilityAssessment.available(VolatilityLevel.NORMAL,
            List.of(VolatilityReasonCodes.VOLATILITY_NORMAL));
    private static final VolatilityAssessment EXTREME = VolatilityAssessment.available(VolatilityLevel.EXTREME,
            List.of(VolatilityReasonCodes.VOLATILITY_EXTREME));
    private static final VolatilityAssessment MISSING = VolatilityAssessment.notAvailable(
            EvidenceAvailabilityStatus.UNAVAILABLE, List.of(VolatilityReasonCodes.VOLATILITY_VALUE_MISSING));

    @Test
    void storesExactlyOnePerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        Map<MarketHorizon, VolatilityAssessment> unordered = new HashMap<>();
        unordered.put(H60S, MISSING);
        unordered.put(H1S, LOW);
        unordered.put(H15S, EXTREME);
        unordered.put(H5S, NORMAL);

        VolatilityAssessments assessments = new VolatilityAssessments(unordered);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(assessments.asMap().keySet()));
        assertEquals(List.of(LOW, NORMAL, EXTREME, MISSING), assessments.asList());
        assertSame(EXTREME, assessments.of(H15S));
        assertEquals(assessments, VolatilityAssessments.of(LOW, NORMAL, EXTREME, MISSING));
        assertEquals(assessments.hashCode(), VolatilityAssessments.of(LOW, NORMAL, EXTREME, MISSING).hashCode());
        assertNotEquals(assessments, VolatilityAssessments.of(LOW, NORMAL, EXTREME, NORMAL));
    }

    @Test
    void missingHorizonFailsFast() {
        Map<MarketHorizon, VolatilityAssessment> partial = new EnumMap<>(MarketHorizon.class);
        partial.put(H1S, LOW);
        partial.put(H5S, NORMAL);
        partial.put(H15S, EXTREME);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessments(partial));
        assertTrue(ex.getMessage().contains("H60S"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new VolatilityAssessments(null));
        assertThrows(IllegalArgumentException.class, () -> VolatilityAssessments.of(LOW, null, EXTREME, MISSING));
    }

    @Test
    void entriesBeyondTheFourCanonicalKeysAreRejectedNotSilentlyDropped() {
        Map<MarketHorizon, VolatilityAssessment> withNullKey = new HashMap<>();
        withNullKey.put(H1S, LOW);
        withNullKey.put(H5S, NORMAL);
        withNullKey.put(H15S, EXTREME);
        withNullKey.put(H60S, MISSING);
        withNullKey.put(null, NORMAL);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new VolatilityAssessments(withNullKey));
        assertTrue(ex.getMessage().contains("exactly the four canonical horizons"), ex.getMessage());
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        VolatilityAssessments assessments = VolatilityAssessments.of(LOW, NORMAL, EXTREME, MISSING);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().put(H1S, NORMAL));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().add(NORMAL));
        assertThrows(IllegalArgumentException.class, () -> assessments.of(null));
    }

    @Test
    void mutatingTheInputMapAfterConstructionHasNoEffect() {
        Map<MarketHorizon, VolatilityAssessment> input = new EnumMap<>(MarketHorizon.class);
        input.put(H1S, LOW);
        input.put(H5S, NORMAL);
        input.put(H15S, EXTREME);
        input.put(H60S, MISSING);
        VolatilityAssessments assessments = new VolatilityAssessments(input);
        input.put(H60S, NORMAL);
        input.clear();

        assertSame(MISSING, assessments.of(H60S));
        assertEquals(4, assessments.asList().size());
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exactly four eligibilities, canonical order, no nulls, immutable. */
class HorizonEligibilitiesTest {

    private static final HorizonEligibility ELIGIBLE = HorizonEligibility.eligible();
    private static final HorizonEligibility WARMING = HorizonEligibility.warmingUp(List.of(QualityReasonCodes.WINDOW_WARMING_UP));

    @Test
    void storesExactlyOnePerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        Map<MarketHorizon, HorizonEligibility> unordered = new HashMap<>();
        unordered.put(H60S, WARMING);
        unordered.put(H1S, ELIGIBLE);
        unordered.put(H15S, WARMING);
        unordered.put(H5S, ELIGIBLE);

        HorizonEligibilities eligibilities = new HorizonEligibilities(unordered);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(eligibilities.asMap().keySet()));
        assertEquals(List.of(ELIGIBLE, ELIGIBLE, WARMING, WARMING), eligibilities.asList());
        assertEquals(List.of(H1S, H5S), eligibilities.eligibleHorizons());
        assertEquals(HorizonEligibilityStatus.WARMING_UP, eligibilities.statusOf(H60S));
        assertTrue(eligibilities.isEligible(H1S));
        assertFalse(eligibilities.isEligible(H15S));
        assertTrue(eligibilities.anyEligible());
        assertFalse(eligibilities.allEligible());
        assertEquals(eligibilities, HorizonEligibilities.of(ELIGIBLE, ELIGIBLE, WARMING, WARMING));
        assertEquals(eligibilities.hashCode(), HorizonEligibilities.of(ELIGIBLE, ELIGIBLE, WARMING, WARMING).hashCode());
    }

    @Test
    void missingHorizonFailsFast() {
        Map<MarketHorizon, HorizonEligibility> partial = new EnumMap<>(MarketHorizon.class);
        partial.put(H1S, ELIGIBLE);
        partial.put(H5S, ELIGIBLE);
        partial.put(H15S, ELIGIBLE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new HorizonEligibilities(partial));
        assertTrue(ex.getMessage().contains("H60S"));
        assertThrows(NullPointerException.class, () -> new HorizonEligibilities(null));
        assertThrows(NullPointerException.class, () -> HorizonEligibilities.of(ELIGIBLE, null, ELIGIBLE, ELIGIBLE));
        assertThrows(NullPointerException.class, () -> HorizonEligibilities.uniform(null));
    }

    @Test
    void uniformAppliesTheSameVerdictEverywhere() {
        HorizonEligibilities all = HorizonEligibilities.uniform(
                HorizonEligibility.unavailable(List.of(QualityReasonCodes.SOURCE_NO_DATA)));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(HorizonEligibilityStatus.UNAVAILABLE, all.statusOf(horizon));
        }
        assertFalse(all.anyEligible());
        assertTrue(all.eligibleHorizons().isEmpty());
        assertTrue(HorizonEligibilities.uniform(ELIGIBLE).allEligible());
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        HorizonEligibilities eligibilities = HorizonEligibilities.of(ELIGIBLE, ELIGIBLE, ELIGIBLE, ELIGIBLE);

        assertThrows(UnsupportedOperationException.class, () -> eligibilities.asMap().put(H1S, WARMING));
        assertThrows(UnsupportedOperationException.class, () -> eligibilities.asList().add(WARMING));
        assertThrows(UnsupportedOperationException.class, () -> eligibilities.eligibleHorizons().clear());
        assertThrows(NullPointerException.class, () -> eligibilities.of(null));
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(ELIGIBLE, eligibilities.of(horizon));
        }
        assertNotEquals(eligibilities, HorizonEligibilities.of(ELIGIBLE, ELIGIBLE, ELIGIBLE, WARMING));
    }
}

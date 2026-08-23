package com.trading.marketsignalengine.application.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketHorizonTest {

    @Test
    void wireValuesAndDurationsMatchTheContract() {
        assertEquals("1S", MarketHorizon.H1S.wireValue());
        assertEquals("5S", MarketHorizon.H5S.wireValue());
        assertEquals("15S", MarketHorizon.H15S.wireValue());
        assertEquals("60S", MarketHorizon.H60S.wireValue());

        assertEquals(Duration.ofSeconds(1), MarketHorizon.H1S.duration());
        assertEquals(Duration.ofSeconds(5), MarketHorizon.H5S.duration());
        assertEquals(Duration.ofSeconds(15), MarketHorizon.H15S.duration());
        assertEquals(Duration.ofSeconds(60), MarketHorizon.H60S.duration());

        assertEquals(1_000L, MarketHorizon.H1S.durationMs());
        assertEquals(60_000L, MarketHorizon.H60S.durationMs());
    }

    @Test
    void canonicalOrderIsAlways1s5s15s60s() {
        List<MarketHorizon> expected = List.of(MarketHorizon.H1S, MarketHorizon.H5S, MarketHorizon.H15S, MarketHorizon.H60S);
        assertEquals(expected, MarketHorizon.canonicalOrder());
        assertEquals(expected, List.of(MarketHorizon.values()));
        // ascending duration == canonical order
        for (int i = 1; i < expected.size(); i++) {
            assertEquals(-1, Integer.signum(expected.get(i - 1).duration().compareTo(expected.get(i).duration())));
        }
        assertThrows(UnsupportedOperationException.class, () -> MarketHorizon.canonicalOrder().add(MarketHorizon.H1S));
    }

    @Test
    void fromWireValueResolvesKnownHorizonsAndFailsClosedOnUnknown() {
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertSame(horizon, MarketHorizon.fromWireValue(horizon.wireValue()));
        }
        assertThrows(IllegalArgumentException.class, () -> MarketHorizon.fromWireValue("30S"));
        assertThrows(IllegalArgumentException.class, () -> MarketHorizon.fromWireValue("5s"));
        assertThrows(NullPointerException.class, () -> MarketHorizon.fromWireValue(null));
    }
}

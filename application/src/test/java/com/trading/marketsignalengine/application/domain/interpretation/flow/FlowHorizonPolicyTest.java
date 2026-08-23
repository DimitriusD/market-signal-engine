package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.interpretation.flow.FlowFixtures.bd;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Per-horizon policy invariants: ranges, ordering, counts, ratio, no nulls, no doubles. */
class FlowHorizonPolicyTest {

    @ParameterizedTest
    @EnumSource(MarketHorizon.class)
    void acceptsBoundaryValuesOnEveryHorizon(MarketHorizon horizon) {
        FlowHorizonPolicy widest = FlowHorizonPolicy.of(horizon, BigDecimal.ONE, bd("-1"), 1, 0, BigDecimal.ONE);
        FlowHorizonPolicy tightest = FlowHorizonPolicy.of(horizon, bd("0.000001"), bd("-0.000001"), 1, 0, BigDecimal.ZERO);

        assertEquals(horizon, widest.horizon());
        assertEquals(horizon, tightest.horizon());
        assertEquals(0, widest.bullishImbalanceThreshold().compareTo(BigDecimal.ONE));
        assertEquals(0, tightest.maxUnknownSideRatio().compareTo(BigDecimal.ZERO));
    }

    @ParameterizedTest
    @CsvSource({
            // bullish, bearish, minTrade, minAggr, maxUnknown, offending field
            "0,      -0.3,  10, 5,  0.25, bullishImbalanceThreshold",   // bullish must be > 0
            "1.0001, -0.3,  10, 5,  0.25, bullishImbalanceThreshold",   // bullish must be <= 1
            "-0.3,   -0.3,  10, 5,  0.25, bullishImbalanceThreshold",   // bullish must be positive
            "0.3,    0,     10, 5,  0.25, bearishImbalanceThreshold",   // bearish must be < 0
            "0.3,    -1.01, 10, 5,  0.25, bearishImbalanceThreshold",   // bearish must be >= -1
            "0.3,    0.2,   10, 5,  0.25, bearishImbalanceThreshold",   // bearish must be negative
            "0.3,    -0.3,  0,  5,  0.25, minTradeCount",               // minTradeCount > 0
            "0.3,    -0.3,  -1, 5,  0.25, minTradeCount",
            "0.3,    -0.3,  10, -1, 0.25, minAggressiveTradeCount",     // minAggressive >= 0
            "0.3,    -0.3,  10, 5,  -0.01, maxUnknownSideRatio",        // ratio in [0,1]
            "0.3,    -0.3,  10, 5,  1.01, maxUnknownSideRatio",
    })
    void rejectsOutOfRangeParameters(String bullish, String bearish, int minTrade, int minAggr, String maxUnknown,
                                     String offendingField) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FlowHorizonPolicy.of(H5S, bd(bullish), bd(bearish), minTrade, minAggr, bd(maxUnknown)));
        assertTrue(ex.getMessage().contains(offendingField), ex.getMessage());
        assertTrue(ex.getMessage().contains("5S"), "message names the horizon: " + ex.getMessage());
    }

    @Test
    void rejectsNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> FlowHorizonPolicy.of(null, bd("0.3"), bd("-0.3"), 10, 5, bd("0.25")));
        assertThrows(IllegalArgumentException.class,
                () -> FlowHorizonPolicy.of(H5S, null, bd("-0.3"), 10, 5, bd("0.25")));
        assertThrows(IllegalArgumentException.class,
                () -> FlowHorizonPolicy.of(H5S, bd("0.3"), null, 10, 5, bd("0.25")));
        assertThrows(IllegalArgumentException.class,
                () -> FlowHorizonPolicy.of(H5S, bd("0.3"), bd("-0.3"), 10, 5, null));
    }

    @Test
    void isValueBasedAndKeepsExactDecimals() {
        FlowHorizonPolicy a = FlowHorizonPolicy.of(H5S, bd("0.30"), bd("-0.30"), 10, 5, bd("0.25"));
        FlowHorizonPolicy b = FlowHorizonPolicy.of(H5S, bd("0.30"), bd("-0.30"), 10, 5, bd("0.25"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("0.30", a.bullishImbalanceThreshold().toPlainString(), "no double round-trip, scale preserved");
    }
}

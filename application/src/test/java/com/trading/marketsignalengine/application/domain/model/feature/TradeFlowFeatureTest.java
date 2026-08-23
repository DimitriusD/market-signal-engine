package com.trading.marketsignalengine.application.domain.model.feature;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** {@code TradeFlowFeature.window(horizon)} is the single canonical horizon → window selection. */
class TradeFlowFeatureTest {

    @Test
    void windowSelectsTheAccessorOfTheHorizon() {
        TradeFlowWindow w1 = TradeFlowWindow.builder().tradeCount(1).build();
        TradeFlowWindow w5 = TradeFlowWindow.builder().tradeCount(5).build();
        TradeFlowWindow w15 = TradeFlowWindow.builder().tradeCount(15).build();
        TradeFlowWindow w60 = TradeFlowWindow.builder().tradeCount(60).build();
        TradeFlowFeature tradeFlow = TradeFlowFeature.builder().window1s(w1).window5s(w5).window15s(w15).window60s(w60).build();

        assertSame(w1, tradeFlow.window(H1S));
        assertSame(w5, tradeFlow.window(H5S));
        assertSame(w15, tradeFlow.window(H15S));
        assertSame(w60, tradeFlow.window(H60S));
    }

    @Test
    void absentWindowIsNullNotAnEmptyWindow() {
        TradeFlowFeature onlyShort = TradeFlowFeature.builder().window5s(TradeFlowWindow.builder().tradeCount(5).build()).build();

        assertNull(onlyShort.window(H1S));
        assertNull(onlyShort.window(H15S));
        assertNull(onlyShort.window(H60S));
        assertThrows(NullPointerException.class, () -> onlyShort.window(null));
    }
}

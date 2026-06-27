package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.rule.DefaultCompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.OrderBookSignalRule;
import com.trading.marketsignalengine.application.domain.rule.QualitySignalRule;
import com.trading.marketsignalengine.application.domain.rule.RegimeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.rule.SpreadSignalRule;
import com.trading.marketsignalengine.application.domain.rule.TradeFlowSignalRule;
import com.trading.marketsignalengine.application.domain.rule.VolatilitySignalRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMarketSignalEngineTest {

    private final DefaultMarketSignalEngine engine = new DefaultMarketSignalEngine(
            List.of(new QualitySignalRule()),
            List.of(new SpreadSignalRule(), new VolatilitySignalRule()),
            List.of(new TradeFlowSignalRule(), new OrderBookSignalRule(), new RegimeSignalRule()),
            new DefaultCompositeSignalRule(),
            new SignalAggregator(),
            SignalConfiguration.defaults());

    @Test
    void badQualitySuppressesDirectionalAndTradabilitySignals() {
        // Quality rejects the snapshot; directional data that would otherwise fire is present.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .quality(FeatureQuality.builder()
                        .syncStatus(SyncStatus.OUT_OF_SYNC)
                        .staleOrderBookState(true)
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(0, snapshot.marketBiasScore().signum());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.NO_TRADE_OUT_OF_SYNC));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        // Phase 3 never ran: no directional signals computed on data the quality gate rejected.
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.REGIME_TRENDING_UP));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        // Phase 2 never ran either, so no spread verdict on a quality-rejected (e.g. stale) BBO.
        assertFalse(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
    }

    @Test
    void wideSpreadSuppressesDirectionalSignals() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(BboFeature.builder().spreadBps(new BigDecimal("5.0")).build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.DATA_TRADABLE));
        assertTrue(has(snapshot, SignalType.SPREAD_TOO_WIDE));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void highVolatilitySuppressesDirectionalSignals() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(RegimeFeature.builder()
                        .shortTermVolatility1s(new BigDecimal("0.05"))
                        .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.VOLATILITY_HIGH));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.REGIME_TRENDING_UP));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void tradableSnapshotProducesDirectionalAndSetupSignals() {
        MarketSignalSnapshot snapshot = engine.evaluate(SignalRuleTestSupport.defaultFeatures());

        assertEquals(MarketBias.BULLISH, snapshot.marketBias());
        assertEquals(RiskLevel.NORMAL, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.DATA_TRADABLE));
        assertTrue(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
        assertTrue(has(snapshot, SignalType.VOLATILITY_NORMAL));
        assertTrue(has(snapshot, SignalType.BUY_PRESSURE));
        assertTrue(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertTrue(has(snapshot, SignalType.REGIME_TRENDING_UP));
        assertTrue(has(snapshot, SignalType.LONG_SETUP_FORMING));

        assertFalse(has(snapshot, SignalType.NO_TRADE_CONDITION));
    }

    private static boolean has(MarketSignalSnapshot snapshot, SignalType type) {
        return snapshot.signals().stream().anyMatch(signal -> signal.type() == type);
    }
}

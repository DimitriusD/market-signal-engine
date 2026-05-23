package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.TradeFlowFeatureView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeFlowSignalRuleTest {

    private final TradeFlowSignalRule rule = new TradeFlowSignalRule();

    @Test
    void positiveSignedTradeFlow5sEmitsBuyPressure() {
        MarketFeaturesSnapshot features = featuresWithTradeFlow(new BigDecimal("50"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.BUY_PRESSURE, signals.getFirst().type());
    }

    @Test
    void negativeSignedTradeFlow5sEmitsSellPressure() {
        MarketFeaturesSnapshot features = featuresWithTradeFlow(new BigDecimal("-50"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.SELL_PRESSURE, signals.getFirst().type());
    }

    @Test
    void zeroSignedTradeFlow5sEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWithTradeFlow(BigDecimal.ZERO);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signals.getFirst().type());
    }

    @Test
    void nullSignedTradeFlow5sEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWithTradeFlow(null);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signals.getFirst().type());
    }

    private static MarketFeaturesSnapshot featuresWithTradeFlow(BigDecimal signedTradeFlow5s) {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .tradeFlow(TradeFlowFeatureView.builder().signedTradeFlow5s(signedTradeFlow5s).build())
                .build();
    }
}

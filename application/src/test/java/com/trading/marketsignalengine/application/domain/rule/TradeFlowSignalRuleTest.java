package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeFlowSignalRuleTest {

    private final TradeFlowSignalRule rule = new TradeFlowSignalRule();

    @Test
    void missingTradeFlowEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWith(null);

        MarketSignal signal = evaluateFirst(features);

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signal.type());
        assertEquals("TRADE_FLOW_MISSING", signal.attributes().get("neutralReason"));
    }

    @Test
    void missingImbalanceEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWith(
                TradeFlowFeature.builder().tradeCount5s(50).build());

        MarketSignal signal = evaluateFirst(features);

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signal.type());
        assertEquals("SIGNED_FLOW_IMBALANCE_5S_MISSING", signal.attributes().get("neutralReason"));
    }

    @Test
    void lowTradeCountEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("0.80"), 9));

        MarketSignal signal = evaluateFirst(features);

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signal.type());
        assertEquals("LOW_TRADE_COUNT_5S", signal.attributes().get("neutralReason"));
    }

    @Test
    void imbalanceInsideDeadZoneEmitsTradeFlowNeutral() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("0.10"), 50));

        MarketSignal signal = evaluateFirst(features);

        assertEquals(SignalType.TRADE_FLOW_NEUTRAL, signal.type());
        assertEquals("INSIDE_DEAD_ZONE", signal.attributes().get("neutralReason"));
    }

    @Test
    void imbalanceAtBuyThresholdEmitsBuyPressure() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("0.15"), 50));

        assertEquals(SignalType.BUY_PRESSURE, evaluateFirst(features).type());
    }

    @Test
    void imbalanceAboveBuyThresholdEmitsBuyPressure() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("0.30"), 50));

        assertEquals(SignalType.BUY_PRESSURE, evaluateFirst(features).type());
    }

    @Test
    void imbalanceAtSellThresholdEmitsSellPressure() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("-0.15"), 50));

        assertEquals(SignalType.SELL_PRESSURE, evaluateFirst(features).type());
    }

    @Test
    void imbalanceBelowSellThresholdEmitsSellPressure() {
        MarketFeaturesSnapshot features = featuresWith(tradeFlow(new BigDecimal("-0.30"), 50));

        assertEquals(SignalType.SELL_PRESSURE, evaluateFirst(features).type());
    }

    @Test
    void buyPressureContains1sAnd5sAttributes() {
        TradeFlowFeature tradeFlow = TradeFlowFeature.builder()
                .signedFlowImbalance5s(new BigDecimal("0.30"))
                .tradeCount5s(50)
                .buyAggressiveVolume5s(new BigDecimal("100"))
                .signedFlowImbalance1s(new BigDecimal("0.40"))
                .tradeCount1s(12)
                .buyAggressiveVolume1s(new BigDecimal("20"))
                .vwap1s(new BigDecimal("100.4"))
                .lastTradePrice(new BigDecimal("100.5"))
                .build();

        MarketSignal signal = evaluateFirst(featuresWith(tradeFlow));

        assertEquals(SignalType.BUY_PRESSURE, signal.type());
        // 5s context
        assertEquals("50", signal.attributes().get("tradeCount5s"));
        assertEquals("0.30", signal.attributes().get("signedFlowImbalance5s"));
        assertEquals("100", signal.attributes().get("buyAggressiveVolume5s"));
        // 1s context
        assertEquals("12", signal.attributes().get("tradeCount1s"));
        assertEquals("0.40", signal.attributes().get("signedFlowImbalance1s"));
        assertEquals("20", signal.attributes().get("buyAggressiveVolume1s"));
        assertEquals("100.4", signal.attributes().get("vwap1s"));
        assertEquals("100.5", signal.attributes().get("lastTradePrice"));
    }

    @Test
    void higherPositiveImbalanceHasHigherConfidence() {
        MarketSignal atThreshold = evaluateFirst(featuresWith(tradeFlow(new BigDecimal("0.15"), 50)));
        MarketSignal strong = evaluateFirst(featuresWith(tradeFlow(new BigDecimal("0.80"), 50)));

        assertTrue(strong.confidence().compareTo(atThreshold.confidence()) > 0);
    }

    @Test
    void higherNegativeImbalanceHasHigherConfidence() {
        MarketSignal atThreshold = evaluateFirst(featuresWith(tradeFlow(new BigDecimal("-0.15"), 50)));
        MarketSignal strong = evaluateFirst(featuresWith(tradeFlow(new BigDecimal("-0.80"), 50)));

        assertTrue(strong.confidence().compareTo(atThreshold.confidence()) > 0);
    }

    @Test
    void blankSignalSetVersionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SignalConfiguration.defaults().toBuilder().signalSetVersion(" ").build());
    }

    @Test
    void nonPositiveBuyThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SignalConfiguration.defaults().toBuilder()
                        .buyFlowImbalance5sThreshold(BigDecimal.ZERO).build());
    }

    @Test
    void nonNegativeSellThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SignalConfiguration.defaults().toBuilder()
                        .sellFlowImbalance5sThreshold(BigDecimal.ZERO).build());
    }

    @Test
    void buyThresholdNotGreaterThanSellThresholdIsRejected() {
        // A buy threshold that is not strictly above the sell threshold never constructs. With a
        // positive buy and negative sell the ordering holds by construction, so a degenerate ordering
        // is expressed by collapsing both onto the same value, which the sign checks also reject.
        assertThrows(IllegalArgumentException.class,
                () -> SignalConfiguration.defaults().toBuilder()
                        .buyFlowImbalance5sThreshold(new BigDecimal("0.05"))
                        .sellFlowImbalance5sThreshold(new BigDecimal("0.05"))
                        .build());
    }

    @Test
    void negativeMinTradeCountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SignalConfiguration.defaults().toBuilder()
                        .minTradeCount5sForTradeFlowSignal(-1).build());
    }

    @Test
    void imbalance5sOutsideRangeProducesNoTradeInvalidTradeFlow() {
        // Normalized imbalance must be within [-1, 1]; 1.42 is impossible: no-trade, not BUY_PRESSURE.
        MarketSignal signal = evaluateFirst(featuresWith(tradeFlow(new BigDecimal("1.42"), 50)));

        assertEquals(SignalType.NO_TRADE_INVALID_TRADE_FLOW, signal.type());
        assertEquals("SIGNED_FLOW_IMBALANCE_5S_OUT_OF_RANGE", signal.attributes().get("neutralReason"));
    }

    @Test
    void imbalance1sOutsideRangeProducesNoTradeInvalidTradeFlow() {
        MarketSignal signal = evaluateFirst(featuresWith(TradeFlowFeature.builder()
                .signedFlowImbalance5s(new BigDecimal("0.30"))
                .tradeCount5s(50)
                .signedFlowImbalance1s(new BigDecimal("1.42"))
                .build()));

        assertEquals(SignalType.NO_TRADE_INVALID_TRADE_FLOW, signal.type());
        assertEquals("SIGNED_FLOW_IMBALANCE_1S_OUT_OF_RANGE", signal.attributes().get("neutralReason"));
    }

    @Test
    void negativeTradeCountProducesNoTradeInvalidTradeFlow() {
        MarketSignal signal = evaluateFirst(featuresWith(TradeFlowFeature.builder()
                .signedFlowImbalance5s(new BigDecimal("0.30"))
                .tradeCount5s(-1)
                .build()));

        assertEquals(SignalType.NO_TRADE_INVALID_TRADE_FLOW, signal.type());
        assertEquals("NEGATIVE_TRADE_COUNT_5S", signal.attributes().get("neutralReason"));
    }

    private MarketSignal evaluateFirst(MarketFeaturesSnapshot features) {
        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));
        return signals.getFirst();
    }

    private static TradeFlowFeature tradeFlow(BigDecimal signedFlowImbalance5s, int tradeCount5s) {
        return TradeFlowFeature.builder()
                .signedFlowImbalance5s(signedFlowImbalance5s)
                .tradeCount5s(tradeCount5s)
                .build();
    }

    private static MarketFeaturesSnapshot featuresWith(TradeFlowFeature tradeFlow) {
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
                .tradeFlow(tradeFlow)
                .build();
    }
}

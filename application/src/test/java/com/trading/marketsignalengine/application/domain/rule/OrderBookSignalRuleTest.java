package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderBookSignalRuleTest {

    private final OrderBookSignalRule rule = new OrderBookSignalRule();

    @Test
    void top5ImbalanceAboveThresholdEmitsOrderBookBullish() {
        MarketFeaturesSnapshot features = featuresWithImbalance(new BigDecimal("0.80"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.ORDER_BOOK_BULLISH, signals.getFirst().type());
    }

    @Test
    void top5ImbalanceBelowNegativeThresholdEmitsOrderBookBearish() {
        MarketFeaturesSnapshot features = featuresWithImbalance(new BigDecimal("-0.80"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.ORDER_BOOK_BEARISH, signals.getFirst().type());
    }

    @Test
    void neutralTop5ImbalanceEmitsOrderBookNeutral() {
        MarketFeaturesSnapshot features = featuresWithImbalance(new BigDecimal("0.10"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.ORDER_BOOK_NEUTRAL, signals.getFirst().type());
    }

    @Test
    void bullishSignalContainsTop5ImbalanceAndThresholds() {
        MarketFeaturesSnapshot features = featuresWithBook(BookFeature.builder()
                .levelsUsed(5)
                .top1Imbalance(new BigDecimal("0.64"))
                .top5Imbalance(new BigDecimal("0.72"))
                .bidLiquidityTop5(new BigDecimal("123.45"))
                .askLiquidityTop5(new BigDecimal("87.65"))
                .build());

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.ORDER_BOOK_BULLISH, signal.type());
        assertEquals("TOP5_IMBALANCE_ABOVE_BULLISH_THRESHOLD", signal.attributes().get("signalReason"));
        assertEquals("0.72", signal.attributes().get("top5Imbalance"));
        assertEquals("0.64", signal.attributes().get("top1Imbalance"));
        assertEquals("0.60", signal.attributes().get("buyBookImbalanceThreshold"));
        assertEquals("-0.60", signal.attributes().get("sellBookImbalanceThreshold"));
        assertEquals("5", signal.attributes().get("levelsUsed"));
    }

    @Test
    void bearishSignalContainsTop5ImbalanceAndThresholds() {
        MarketFeaturesSnapshot features = featuresWithBook(BookFeature.builder()
                .levelsUsed(5)
                .top5Imbalance(new BigDecimal("-0.72"))
                .build());

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.ORDER_BOOK_BEARISH, signal.type());
        assertEquals("TOP5_IMBALANCE_BELOW_BEARISH_THRESHOLD", signal.attributes().get("signalReason"));
        assertEquals("-0.72", signal.attributes().get("top5Imbalance"));
        assertEquals("0.60", signal.attributes().get("buyBookImbalanceThreshold"));
        assertEquals("-0.60", signal.attributes().get("sellBookImbalanceThreshold"));
        assertEquals("5", signal.attributes().get("levelsUsed"));
    }

    @Test
    void neutralSignalContainsNeutralReason() {
        MarketFeaturesSnapshot features = featuresWithImbalance(new BigDecimal("0.10"));

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.ORDER_BOOK_NEUTRAL, signal.type());
        assertEquals("TOP5_IMBALANCE_INSIDE_NEUTRAL_RANGE", signal.attributes().get("neutralReason"));
        assertEquals("0.10", signal.attributes().get("top5Imbalance"));
    }

    @Test
    void missingBookContainsNeutralReason() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder().book(null).build();

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.ORDER_BOOK_NEUTRAL, signal.type());
        assertEquals("BOOK_FEATURES_MISSING", signal.attributes().get("neutralReason"));
        assertEquals("0.60", signal.attributes().get("buyBookImbalanceThreshold"));
    }

    @Test
    void top5ImbalanceOutsideRangeProducesNoTradeInvalidOrderBook() {
        // Normalized imbalance must be within [-1, 1]; 1.5 is impossible: no-trade, not ORDER_BOOK_BULLISH.
        MarketFeaturesSnapshot features = featuresWithBook(BookFeature.builder()
                .levelsUsed(5)
                .top5Imbalance(new BigDecimal("1.5"))
                .build());

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.NO_TRADE_INVALID_ORDER_BOOK, signal.type());
        assertEquals("TOP5_IMBALANCE_OUT_OF_RANGE", signal.attributes().get("riskReason"));
    }

    @Test
    void top1ImbalanceOutsideRangeProducesNoTradeInvalidOrderBook() {
        MarketFeaturesSnapshot features = featuresWithBook(BookFeature.builder()
                .levelsUsed(5)
                .top5Imbalance(new BigDecimal("0.5"))
                .top1Imbalance(new BigDecimal("1.5"))
                .build());

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.NO_TRADE_INVALID_ORDER_BOOK, signal.type());
        assertEquals("TOP1_IMBALANCE_OUT_OF_RANGE", signal.attributes().get("riskReason"));
    }

    @Test
    void invalidLevelsUsedProducesNoTradeInvalidOrderBook() {
        MarketFeaturesSnapshot features = featuresWithBook(BookFeature.builder()
                .levelsUsed(0)
                .top5Imbalance(new BigDecimal("0.5"))
                .build());

        MarketSignal signal = rule.evaluate(SignalRuleTestSupport.context(features)).getFirst();

        assertEquals(SignalType.NO_TRADE_INVALID_ORDER_BOOK, signal.type());
        assertEquals("LEVELS_USED_INVALID", signal.attributes().get("riskReason"));
    }

    private static MarketFeaturesSnapshot featuresWithBook(BookFeature book) {
        return SignalRuleTestSupport.tradableFeaturesBuilder().book(book).build();
    }

    private static MarketFeaturesSnapshot featuresWithImbalance(BigDecimal top5Imbalance) {
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
                .book(BookFeature.builder().levelsUsed(5).top5Imbalance(top5Imbalance).build())
                .build();
    }
}

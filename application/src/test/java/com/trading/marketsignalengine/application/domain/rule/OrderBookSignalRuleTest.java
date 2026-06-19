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
                .book(BookFeature.builder().top5Imbalance(top5Imbalance).build())
                .build();
    }
}

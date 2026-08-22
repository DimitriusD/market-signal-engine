package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.math.BigDecimal;
import java.time.Instant;

public final class SignalRuleTestSupport {

    private SignalRuleTestSupport() {
    }

    public static SignalEvaluationContext context(MarketFeaturesSnapshot features) {
        return new SignalEvaluationContext(features, SignalConfiguration.defaults(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    public static MarketFeaturesSnapshot defaultFeatures() {
        return tradableFeaturesBuilder().build();
    }

    /**
     * A fully populated, tradable snapshot whose directional features would produce BUY_PRESSURE,
     * ORDER_BOOK_BULLISH, SPREAD_ACCEPTABLE and VOLATILITY_NORMAL. Callers override individual
     * features (quality, bbo, regime, ...) to build gate scenarios. The regime feature still carries
     * realizedVolatilityBps1s (consumed by VolatilitySignalRule) and lastTradeDistanceToMidBps, but
     * the latter no longer produces a signal.
     */
    public static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder tradableFeaturesBuilder() {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .base("BTC")
                .quote("USDT")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .quality(tradableQuality())
                .bbo(BboFeature.builder().spreadBps(new BigDecimal("1.0")).build())
                .book(BookFeature.builder().levelsUsed(5).top5Imbalance(new BigDecimal("0.70")).build())
                .tradeFlow(TradeFlowFeature.builder()
                        .window5s(TradeFlowWindow.builder()
                                .signedFlowImbalance(new BigDecimal("0.70"))
                                .tradeCount(50)
                                .build())
                        .build())
                .regime(RegimeFeature.builder()
                        .realizedVolatilityBps1s(new BigDecimal("5.0"))
                        .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                        .build());
    }

    public static MarketFeaturesSnapshot withQuality(FeatureQuality quality) {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .base("BTC")
                .quote("USDT")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .quality(quality)
                .build();
    }

    public static FeatureQuality tradableQuality() {
        return FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .status(com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus.OK)
                .sourceOrderBookTrusted(true)
                .build();
    }
}

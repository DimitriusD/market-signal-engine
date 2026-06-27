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
     * ORDER_BOOK_BULLISH, SPREAD_ACCEPTABLE, VOLATILITY_NORMAL and REGIME_TRENDING_UP. Callers
     * override individual features (quality, bbo, regime, ...) to build gate scenarios.
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
                .book(BookFeature.builder().top5Imbalance(new BigDecimal("0.70")).build())
                .tradeFlow(TradeFlowFeature.builder().signedTradeFlow5s(new BigDecimal("100")).build())
                .regime(RegimeFeature.builder()
                        .shortTermVolatility1s(new BigDecimal("0.005"))
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
                .build();
    }
}

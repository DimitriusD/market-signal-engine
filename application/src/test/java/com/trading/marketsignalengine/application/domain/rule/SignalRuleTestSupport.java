package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.BboFeatureView;
import com.trading.marketsignalengine.application.domain.model.BookFeatureView;
import com.trading.marketsignalengine.application.domain.model.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.RegimeFeatureView;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.TradeFlowFeatureView;
import java.math.BigDecimal;
import java.time.Instant;

public final class SignalRuleTestSupport {

    private SignalRuleTestSupport() {
    }

    public static SignalEvaluationContext context(MarketFeaturesSnapshot features) {
        return new SignalEvaluationContext(features, SignalConfiguration.defaults(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    public static MarketFeaturesSnapshot defaultFeatures() {
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
                .quality(tradableQuality())
                .bbo(BboFeatureView.builder().spreadBps(new BigDecimal("1.0")).build())
                .book(BookFeatureView.builder().top5Imbalance(new BigDecimal("0.70")).build())
                .tradeFlow(TradeFlowFeatureView.builder().signedTradeFlow5s(new BigDecimal("100")).build())
                .regime(RegimeFeatureView.builder()
                        .shortTermVolatility1s(new BigDecimal("0.005"))
                        .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                        .build())
                .build();
    }

    public static MarketFeaturesSnapshot withQuality(FeatureQuality quality) {
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
                .quality(quality)
                .build();
    }

    public static FeatureQuality tradableQuality() {
        return FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleBbo(false)
                .staleBook(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
    }
}

package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
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
import java.util.List;

public final class SignalRuleTestSupport {

    /** Feature set every test fixture declares; validators in tests must allowlist it. */
    public static final String FEATURE_SET_VERSION = "mfs-features-v2";
    public static final Instant EVENT_TIME = Instant.parse("2026-01-01T00:00:00Z");
    public static final Instant COMPUTED_AT = EVENT_TIME.plusMillis(25);

    private SignalRuleTestSupport() {
    }

    public static SignalEvaluationContext context(MarketFeaturesSnapshot features) {
        return new SignalEvaluationContext(features, SignalConfiguration.defaults(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    public static MarketFeaturesSnapshot defaultFeatures() {
        return tradableFeaturesBuilder().build();
    }

    /**
     * A fully populated, tradable, <b>contract-valid MFS v2</b> snapshot (TRADE trigger, as-of =
     * event time, schemaVersion 1, config hash, clean diagnostics) whose directional features would
     * produce BUY_PRESSURE, ORDER_BOOK_BULLISH, SPREAD_ACCEPTABLE and VOLATILITY_NORMAL. Callers
     * override individual features (quality, bbo, regime, ...) to build gate scenarios. The regime
     * feature still carries realizedVolatilityBps1s (consumed by VolatilitySignalRule) and
     * lastTradeDistanceToMidBps, but the latter no longer produces a signal.
     */
    public static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder tradableFeaturesBuilder() {
        return identityBuilder()
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
        return identityBuilder()
                .quality(quality)
                .build();
    }

    /** Identity, timing, lineage and diagnostics of a valid MFS v2 TRADE-triggered snapshot; no features. */
    public static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder identityBuilder() {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .schemaVersion(1)
                .exchange("binance")
                .marketType("spot")
                .base("BTC")
                .quote("USDT")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(EVENT_TIME)
                .receivedAt(EVENT_TIME.plusMillis(20))
                .computedAt(COMPUTED_AT)
                .evaluationTs(EVENT_TIME)
                .featureSetVersion(FEATURE_SET_VERSION)
                .triggerSource("TRADE")
                .configHash("cfg-test-mfs-v2")
                .diagnostics(noFailures());
    }

    public static FeatureDiagnostics noFailures() {
        return FeatureDiagnostics.builder().failedFeatureGroups(List.of()).totalFeatureGroups(4).build();
    }

    public static FeatureQuality tradableQuality() {
        return FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .status(com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus.OK)
                .sourceOrderBookTrusted(true)
                .sourceOrderBookReason("NONE")
                .orderBookStateAgeMs(40L)
                .tradeAgeMs(90L)
                .qualityReasons(List.of())
                .futureEventDetected(false)
                .warmingUp(false)
                .build();
    }
}

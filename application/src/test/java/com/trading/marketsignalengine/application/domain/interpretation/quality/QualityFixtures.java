package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot / quality / policy fixtures of the quality layer tests. Every snapshot is the
 * contract-valid MFS v2 TRADE-triggered snapshot of {@link SignalRuleTestSupport}
 * ({@code evaluationTs = EVENT_TIME}, {@code computedAt = EVENT_TIME + 25ms}) with the trade-flow
 * group and quality replaced per scenario.
 */
final class QualityFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;
    /** A fresh assessment instant: 100 ms after the as-of instant, 75 ms after computedAt. */
    static final Instant FRESH_ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy POLICY = QualityEligibilityPolicy.of(
            Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityEligibilityPolicy ALLOW_FUTURE_POLICY = QualityEligibilityPolicy.of(
            Duration.ofMillis(2_000), Duration.ofMillis(1_000), false);

    private QualityFixtures() {
    }

    // ------------------------------------------------------------------ snapshots

    static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow) {
        return snapshot(tradeFlow, SignalRuleTestSupport.tradableQuality());
    }

    static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow, FeatureQuality quality) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(tradeFlow)
                .quality(quality)
                .build();
    }

    static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow, FeatureQuality quality, List<String> failedGroups) {
        return snapshot(tradeFlow, quality).toBuilder()
                .diagnostics(FeatureDiagnostics.builder().failedFeatureGroups(failedGroups).totalFeatureGroups(4).build())
                .build();
    }

    /** All four trade-flow windows computed. */
    static MarketFeaturesSnapshot allComputed() {
        return snapshot(allWindows());
    }

    static TradeFlowFeature allWindows() {
        return tradeFlow(populated(9), populated(50), populated(150), populated(600));
    }

    /** 1S/5S computed, 15S/60S not covered (null). */
    static TradeFlowFeature shortWindowsOnly() {
        return tradeFlow(populated(4), populated(20), null, null);
    }

    static TradeFlowFeature tradeFlow(TradeFlowWindow w1s, TradeFlowWindow w5s, TradeFlowWindow w15s, TradeFlowWindow w60s) {
        return TradeFlowFeature.builder()
                .window1s(w1s)
                .window5s(w5s)
                .window15s(w15s)
                .window60s(w60s)
                .build();
    }

    static TradeFlowWindow populated(int tradeCount) {
        return TradeFlowWindow.builder()
                .tradeCount(tradeCount)
                .validQtyTradeCount(tradeCount)
                .aggressiveTradeCount(tradeCount)
                .unknownSideCount(0)
                .signedFlowImbalance(new BigDecimal("0.25"))
                .tradeIntensity(new BigDecimal("3.0"))
                .build();
    }

    // ------------------------------------------------------------------ quality

    static FeatureQuality okQuality() {
        return SignalRuleTestSupport.tradableQuality();
    }

    static FeatureQuality degradedQuality(List<String> reasons) {
        return okQuality().toBuilder()
                .status(FeatureQualityStatus.DEGRADED)
                .qualityReasons(reasons)
                .build();
    }

    static FeatureQuality warmingUpQuality() {
        return degradedQuality(List.of("WARMING_UP")).toBuilder().warmingUp(true).build();
    }

    static FeatureQuality historyGapQuality() {
        return degradedQuality(List.of("TRADE_HISTORY_GAP"));
    }

    static FeatureQuality staleTradesQuality() {
        return degradedQuality(List.of("STALE_TRADES")).toBuilder().staleTrades(true).build();
    }

    static FeatureQuality calculatorFailureQuality() {
        return degradedQuality(List.of("CALCULATOR_FAILURE"));
    }

    static FeatureQuality futureEventQuality() {
        return degradedQuality(List.of("FUTURE_EVENT")).toBuilder().futureEventDetected(true).build();
    }

    static FeatureQuality unsafeQuality() {
        return okQuality().toBuilder()
                .status(FeatureQualityStatus.UNSAFE)
                .sourceOrderBookTrusted(false)
                .sourceOrderBookReason("BOOK_UNTRUSTED")
                .qualityReasons(List.of("BOOK_UNTRUSTED"))
                .build();
    }

    /** NO_DATA as the producer emits it: no book, no trades — and it may still carry staleTrades=true. */
    static FeatureQuality noDataQuality() {
        return okQuality().toBuilder()
                .status(FeatureQualityStatus.NO_DATA)
                .staleTrades(true)
                .qualityReasons(List.of("NO_MARKET_DATA"))
                .build();
    }
}

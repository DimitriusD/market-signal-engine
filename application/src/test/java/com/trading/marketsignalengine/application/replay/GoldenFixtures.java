package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synthetic, fully-specified input snapshots for the golden replay suite. Each case exercises one
 * distinct engine path on the default {@code mse-signals-v7} configuration. The map is ordered and
 * the key is the golden file name.
 *
 * <p>Inputs are deliberately hand-written (not generated) so that a golden diff points at a real
 * semantic change, never at fixture noise.
 */
final class GoldenFixtures {

    static final Instant EVENT_TIME = Instant.parse("2026-03-01T10:00:00Z");
    static final Instant RECEIVED_AT = Instant.parse("2026-03-01T10:00:00.020Z");
    static final Instant COMPUTED_AT = Instant.parse("2026-03-01T10:00:00.025Z");

    private GoldenFixtures() {
    }

    static Map<String, MarketFeaturesSnapshot> all() {
        Map<String, MarketFeaturesSnapshot> cases = new LinkedHashMap<>();

        // --- tradable, directional outcomes ---
        cases.put("long-setup-strong", base("g-001")
                .tradeFlow(tradeFlow("0.70", 50))
                .book(book("0.75"))
                .build());
        cases.put("short-setup-strong", base("g-002")
                .tradeFlow(tradeFlow("-0.70", 50))
                .book(book("-0.75"))
                .build());
        cases.put("long-setup-weak-flow", base("g-003")
                .tradeFlow(tradeFlow("0.18", 12))
                .book(book("0.62"))
                .build());
        cases.put("neutral-dead-zone", base("g-004")
                .tradeFlow(tradeFlow("0.05", 50))
                .book(book("0.10"))
                .build());
        cases.put("mixed-buy-flow-bearish-book", base("g-005")
                .tradeFlow(tradeFlow("0.40", 50))
                .book(book("-0.70"))
                .build());
        cases.put("flow-only-below-bias-threshold", base("g-006")
                .tradeFlow(tradeFlow("0.55", 50))
                .book(book("0.20"))
                .build());
        cases.put("low-trade-count-neutral", base("g-007")
                .tradeFlow(tradeFlow("0.80", 3))
                .book(book("0.70"))
                .build());
        cases.put("book-missing-neutral", base("g-008")
                .tradeFlow(tradeFlow("0.05", 50))
                .book(null)
                .build());

        // --- phase 1: quality gate ---
        cases.put("quality-missing", base("g-101")
                .quality(null)
                .build());
        cases.put("quality-stale-book", base("g-102")
                .quality(FeatureQuality.builder()
                        .syncStatus(SyncStatus.IN_SYNC)
                        .staleOrderBookState(true)
                        .staleTrades(false)
                        .incompleteBook(false)
                        .orderBookStateAgeMs(7_500L)
                        .tradeAgeMs(120L)
                        .build())
                .build());
        cases.put("quality-out-of-sync-and-stale-trades", base("g-103")
                .quality(FeatureQuality.builder()
                        .syncStatus(SyncStatus.OUT_OF_SYNC)
                        .staleOrderBookState(false)
                        .staleTrades(true)
                        .incompleteBook(false)
                        .orderBookStateAgeMs(300L)
                        .tradeAgeMs(9_000L)
                        .build())
                .build());
        cases.put("quality-recovering-incomplete", base("g-104")
                .quality(FeatureQuality.builder()
                        .syncStatus(SyncStatus.RECOVERING)
                        .staleOrderBookState(false)
                        .staleTrades(false)
                        .incompleteBook(true)
                        .orderBookStateAgeMs(50L)
                        .tradeAgeMs(80L)
                        .build())
                .build());

        // --- phase 2: tradability gate ---
        cases.put("spread-too-wide", base("g-201")
                .bbo(bbo("2.5"))
                .build());
        cases.put("spread-missing-bbo", base("g-202")
                .bbo(null)
                .build());
        cases.put("invalid-bbo-crossed", base("g-203")
                .bbo(BboFeature.builder()
                        .bestBidPrice(new BigDecimal("50001.0"))
                        .bestAskPrice(new BigDecimal("50000.0"))
                        .bestBidQty(new BigDecimal("1.5"))
                        .bestAskQty(new BigDecimal("2.0"))
                        .spreadBps(new BigDecimal("1.0"))
                        .build())
                .build());
        cases.put("volatility-high", base("g-204")
                .regime(regime("120.0"))
                .build());
        cases.put("volatility-missing-regime", base("g-205")
                .regime(null)
                .build());
        cases.put("volatility-missing-value", base("g-206")
                .regime(RegimeFeature.builder()
                        .lastTradeDistanceToMidBps(new BigDecimal("0.4"))
                        .realizedVolatilityBps1s(null)
                        .build())
                .build());

        // --- phase 3: invalid-feature RISK_OFF with directional evidence that must be stripped ---
        cases.put("invalid-trade-flow-imbalance-out-of-range", base("g-301")
                .tradeFlow(tradeFlow("1.40", 50))
                .book(book("0.75"))
                .build());
        cases.put("invalid-order-book-after-buy-pressure", base("g-302")
                .tradeFlow(tradeFlow("0.70", 50))
                .book(BookFeature.builder()
                        .levelsUsed(0)
                        .top5Imbalance(new BigDecimal("0.75"))
                        .build())
                .build());

        return cases;
    }

    private static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder base(String snapshotId) {
        return MarketFeaturesSnapshot.builder()
                .snapshotId(snapshotId)
                .exchange("binance")
                .marketType("spot")
                .base("BTC")
                .quote("USDT")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(EVENT_TIME)
                .receivedAt(RECEIVED_AT)
                .computedAt(COMPUTED_AT)
                .featureSetVersion("mfs-core-v2")
                .quality(tradableQuality())
                .bbo(bbo("1.0"))
                .book(book("0.10"))
                .tradeFlow(tradeFlow("0.05", 50))
                .regime(regime("5.0"));
    }

    private static FeatureQuality tradableQuality() {
        return FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .orderBookStateAgeMs(40L)
                .tradeAgeMs(90L)
                .build();
    }

    private static BboFeature bbo(String spreadBps) {
        return BboFeature.builder()
                .bestBidPrice(new BigDecimal("50000.0"))
                .bestAskPrice(new BigDecimal("50005.0"))
                .bestBidQty(new BigDecimal("1.5"))
                .bestAskQty(new BigDecimal("2.0"))
                .spreadAbs(new BigDecimal("5.0"))
                .spreadBps(new BigDecimal(spreadBps))
                .midPrice(new BigDecimal("50002.5"))
                .micropriceTop1(new BigDecimal("50002.14"))
                .micropriceOffsetBps(new BigDecimal("-0.07"))
                .build();
    }

    private static BookFeature book(String top5Imbalance) {
        return BookFeature.builder()
                .levelsUsed(5)
                .bidLiquidityTop5(new BigDecimal("12.5"))
                .askLiquidityTop5(new BigDecimal("10.0"))
                .top1Imbalance(new BigDecimal("-0.14"))
                .top5Imbalance(new BigDecimal(top5Imbalance))
                .bestBidGapTicks(new BigDecimal("1"))
                .bestAskGapTicks(new BigDecimal("1"))
                .build();
    }

    private static TradeFlowFeature tradeFlow(String signedFlowImbalance5s, int tradeCount5s) {
        return TradeFlowFeature.builder()
                .lastTradePrice(new BigDecimal("50003.0"))
                .buyAggressiveVolume1s(new BigDecimal("0.8"))
                .sellAggressiveVolume1s(new BigDecimal("0.4"))
                .totalAggressiveVolume1s(new BigDecimal("1.2"))
                .signedTradeFlow1s(new BigDecimal("0.4"))
                .signedFlowImbalance1s(new BigDecimal("0.3333"))
                .tradeCount1s(Math.min(tradeCount5s, 9))
                .tradeIntensity1s(new BigDecimal("9.0"))
                .avgTradeSize1s(new BigDecimal("0.1333"))
                .vwap1s(new BigDecimal("50002.8"))
                .buyAggressiveVolume5s(new BigDecimal("4.0"))
                .sellAggressiveVolume5s(new BigDecimal("2.0"))
                .totalAggressiveVolume5s(new BigDecimal("6.0"))
                .signedTradeFlow5s(new BigDecimal("2.0"))
                .signedFlowImbalance5s(new BigDecimal(signedFlowImbalance5s))
                .tradeCount5s(tradeCount5s)
                .tradeIntensity5s(new BigDecimal("10.0"))
                .avgTradeSize5s(new BigDecimal("0.12"))
                .vwap5s(new BigDecimal("50002.5"))
                .build();
    }

    private static RegimeFeature regime(String realizedVolatilityBps1s) {
        return RegimeFeature.builder()
                .lastTradeDistanceToMidBps(new BigDecimal("0.4"))
                .realizedVolatilityBps1s(new BigDecimal(realizedVolatilityBps1s))
                .build();
    }
}

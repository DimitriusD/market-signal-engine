package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic, fully-specified input snapshots for the golden replay suite. Each case exercises one
 * distinct engine path on the default {@code mse-signals-v8} configuration. The map is ordered and
 * the key is the golden file name.
 *
 * <p>Inputs are deliberately hand-written (not generated) so that a golden diff points at a real
 * semantic change, never at fixture noise.
 */
final class GoldenFixtures {

    static final Instant EVENT_TIME = Instant.parse("2026-03-01T10:00:00Z");
    static final Instant RECEIVED_AT = Instant.parse("2026-03-01T10:00:00.020Z");
    static final Instant COMPUTED_AT = Instant.parse("2026-03-01T10:00:00.025Z");
    /**
     * Kept at the historical value so the rendered {@code sourceFeatureSetVersion} of every golden
     * stays byte-identical; the golden harness allowlists it next to {@code mfs-features-v2}.
     */
    static final String FEATURE_SET_VERSION = "mfs-core-v2";
    static final String CONFIG_HASH = "cfg-golden-mfs-v2";
    /**
     * Fixtures that are contract-invalid on purpose (they exercise the engine's own defence-in-depth
     * on quality that the MFS v2 validator would never let through). The validated replay rejects
     * them; their golden files are checked against the engine directly. See ReplayGoldenTest.
     */
    static final java.util.Set<String> CONTRACT_REJECTED = java.util.Set.of("quality-missing", "quality-status-missing");

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
                        .sourceOrderBookTrusted(true)
                        .sourceOrderBookReason("NONE")
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("STALE_ORDER_BOOK"))
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
                        .sourceOrderBookTrusted(false)
                        .sourceOrderBookReason("GAP_DETECTED")
                        .status(FeatureQualityStatus.UNSAFE)
                        .qualityReasons(List.of("BOOK_OUT_OF_SYNC", "STALE_TRADES"))
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
                        .sourceOrderBookTrusted(true)
                        .sourceOrderBookReason("NONE")
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("INCOMPLETE_BOOK"))
                        .build())
                .build());
        // Aggregate-status gate (mse-signals-v8): per-source flags clean, status alone decides.
        cases.put("quality-degraded-warming-up", base("g-105")
                .quality(tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("WARMING_UP"))
                        .warmingUp(true)
                        .build())
                .build());
        cases.put("quality-degraded-calculator-failure", base("g-106")
                .quality(tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE", "TRADE_HISTORY_GAP"))
                        .build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("short-term-regime"))
                        .totalFeatureGroups(6)
                        .build())
                .build());
        cases.put("quality-unsafe-untrusted-book", base("g-107")
                .quality(tradableQuality().toBuilder()
                        .sourceOrderBookTrusted(false)
                        .sourceOrderBookReason("CROSSED_BOOK")
                        .status(FeatureQualityStatus.UNSAFE)
                        .qualityReasons(List.of("BOOK_UNTRUSTED"))
                        .build())
                .build());
        cases.put("quality-no-data", base("g-108")
                .quality(tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.NO_DATA)
                        .qualityReasons(List.of("NO_MARKET_DATA"))
                        .build())
                .build());
        cases.put("quality-status-missing", base("g-109")
                .quality(tradableQuality().toBuilder()
                        .status(null)
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
                .schemaVersion(1)
                .eventTime(EVENT_TIME)
                .receivedAt(RECEIVED_AT)
                .computedAt(COMPUTED_AT)
                // MFS v2 lineage: a TRADE-triggered snapshot evaluates as-of the trigger exchangeTs.
                .evaluationTs(EVENT_TIME)
                .triggerSource("TRADE")
                .configHash(CONFIG_HASH)
                .featureSetVersion(FEATURE_SET_VERSION)
                .diagnostics(FeatureDiagnostics.builder().failedFeatureGroups(List.of()).totalFeatureGroups(4).build())
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
                .sourceOrderBookTrusted(true)
                .sourceOrderBookReason("NONE")
                .status(FeatureQualityStatus.OK)
                .qualityReasons(List.of())
                .futureEventDetected(false)
                .warmingUp(false)
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
                .window1s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(new BigDecimal("0.8"))
                        .sellAggressiveVolume(new BigDecimal("0.4"))
                        .totalAggressiveVolume(new BigDecimal("1.2"))
                        .signedTradeFlow(new BigDecimal("0.4"))
                        .signedFlowImbalance(new BigDecimal("0.3333"))
                        .tradeCount(Math.min(tradeCount5s, 9))
                        .validQtyTradeCount(Math.min(tradeCount5s, 9))
                        .aggressiveTradeCount(Math.min(tradeCount5s, 9))
                        .unknownSideCount(0)
                        .tradeIntensity(new BigDecimal("9.0"))
                        .avgTradeSize(new BigDecimal("0.1333"))
                        .vwap(new BigDecimal("50002.8"))
                        .build())
                .window5s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(new BigDecimal("4.0"))
                        .sellAggressiveVolume(new BigDecimal("2.0"))
                        .totalAggressiveVolume(new BigDecimal("6.0"))
                        .signedTradeFlow(new BigDecimal("2.0"))
                        .signedFlowImbalance(new BigDecimal(signedFlowImbalance5s))
                        .tradeCount(tradeCount5s)
                        .validQtyTradeCount(tradeCount5s)
                        .aggressiveTradeCount(tradeCount5s)
                        .unknownSideCount(0)
                        .tradeIntensity(new BigDecimal("10.0"))
                        .avgTradeSize(new BigDecimal("0.12"))
                        .vwap(new BigDecimal("50002.5"))
                        .build())
                // 15s/60s are present (as MFS v2 publishes them) but not read by any rule yet; the
                // golden output must therefore not change when they are populated.
                .window15s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(new BigDecimal("11.0"))
                        .sellAggressiveVolume(new BigDecimal("7.0"))
                        .totalAggressiveVolume(new BigDecimal("18.0"))
                        .signedTradeFlow(new BigDecimal("4.0"))
                        .signedFlowImbalance(new BigDecimal("0.2222"))
                        .tradeCount(tradeCount5s * 3)
                        .validQtyTradeCount(tradeCount5s * 3)
                        .aggressiveTradeCount(tradeCount5s * 3)
                        .unknownSideCount(0)
                        .tradeIntensity(new BigDecimal("10.0"))
                        .avgTradeSize(new BigDecimal("0.12"))
                        .vwap(new BigDecimal("50002.1"))
                        .build())
                .window60s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(new BigDecimal("40.0"))
                        .sellAggressiveVolume(new BigDecimal("38.0"))
                        .totalAggressiveVolume(new BigDecimal("78.0"))
                        .signedTradeFlow(new BigDecimal("2.0"))
                        .signedFlowImbalance(new BigDecimal("0.0256"))
                        .tradeCount(tradeCount5s * 12)
                        .validQtyTradeCount(tradeCount5s * 12)
                        .aggressiveTradeCount(tradeCount5s * 12)
                        .unknownSideCount(1)
                        .tradeIntensity(new BigDecimal("10.0"))
                        .avgTradeSize(new BigDecimal("0.13"))
                        .vwap(new BigDecimal("50001.4"))
                        .build())
                .build();
    }

    private static RegimeFeature regime(String realizedVolatilityBps1s) {
        return RegimeFeature.builder()
                .lastTradeDistanceToMidBps(new BigDecimal("0.4"))
                .realizedVolatilityBps1s(new BigDecimal(realizedVolatilityBps1s))
                .build();
    }
}

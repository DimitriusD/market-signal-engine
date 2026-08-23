package com.trading.marketsignalengine.event.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.DiagnosticsEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.FeatureSourceStateEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.contracts.orderbook.OrderBookReason;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the full MFS v2 → domain mapping. Every field of the Avro contract is asserted
 * here, so an unmapped field is a failing test, not a silent drop (path-to-paper-trading.md, 2.1/2.2).
 */
class MarketFeaturesSnapshotAvroMapperTest {

    // ---------------------------------------------------------------- snapshot-level fields

    @Test
    void mapsSnapshotLevelFieldsAndMetadata() {
        MarketFeaturesSnapshotEvent event = fullEvent()
                .setEvaluationTs(1700000002500L)
                .setTriggerSource("TRADE")
                .setConfigHash("cfg-abc123")
                .build();

        MarketFeaturesSnapshot s = MarketFeaturesSnapshotAvroMapper.toDomain(event);

        assertEquals("feat-1", s.snapshotId());
        // metadata.schemaVersion is carried verbatim for the compatibility validator (MFS v2 → 1)
        assertEquals(Integer.valueOf(1), s.schemaVersion());
        assertEquals("binance", s.exchange());
        assertEquals("spot", s.marketType());
        assertEquals("BTC", s.base());
        assertEquals("USDT", s.quote());
        assertEquals("BTCUSDT", s.symbol());
        assertEquals("binance:spot:BTCUSDT", s.instrumentId());
        assertEquals(Instant.ofEpochMilli(1700000000000L), s.eventTime());
        assertEquals(Instant.ofEpochMilli(1700000001000L), s.receivedAt());
        assertEquals(Instant.ofEpochMilli(1700000002000L), s.computedAt());
        assertEquals(Instant.ofEpochMilli(1700000002500L), s.evaluationTs());
        assertEquals("mfs-core-v2", s.featureSetVersion());
        assertEquals("TRADE", s.triggerSource());
        assertEquals("cfg-abc123", s.configHash());
    }

    @Test
    void schemaDefaultEvaluationTsAndBlankConfigHashMapToNull() {
        // evaluationTs default 0 and configHash default "" mean "writer predates the field".
        MarketFeaturesSnapshotEvent event = fullEvent()
                .setEvaluationTs(0L)
                .setConfigHash("")
                .build();

        MarketFeaturesSnapshot s = MarketFeaturesSnapshotAvroMapper.toDomain(event);

        assertNull(s.evaluationTs());
        assertNull(s.configHash());
        assertEquals("UNKNOWN", s.triggerSource());
    }

    // ---------------------------------------------------------------- quality

    @Test
    void mapsAllQualityFields() {
        FeatureQualityEvent quality = FeatureQualityEvent.newBuilder()
                .setSyncStatus(BookSyncStatus.IN_SYNC)
                .setStaleOrderBookState(false)
                .setStaleTrades(true)
                .setIncompleteBook(false)
                .setSourceOrderBookTrusted(true)
                .setSourceOrderBookReason(OrderBookReason.STALE_STATE)
                .setOrderBookStateAgeMs(120L)
                .setTradeAgeMs(6_500L)
                .setStatus(com.trading.contracts.feature.FeatureQualityStatus.DEGRADED)
                .setQualityReasons(List.of("STALE_TRADES", "WARMING_UP"))
                .setFutureEventDetected(true)
                .setWarmingUp(true)
                .build();

        FeatureQuality q = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setQuality(quality).build()).quality();

        assertEquals(SyncStatus.IN_SYNC, q.syncStatus());
        assertFalse(q.staleOrderBookState());
        assertTrue(q.staleTrades());
        assertFalse(q.incompleteBook());
        assertTrue(q.sourceOrderBookTrusted());
        assertEquals("STALE_STATE", q.sourceOrderBookReason());
        assertEquals(120L, q.orderBookStateAgeMs());
        assertEquals(6_500L, q.tradeAgeMs());
        assertEquals(FeatureQualityStatus.DEGRADED, q.status());
        assertEquals(List.of("STALE_TRADES", "WARMING_UP"), q.qualityReasons());
        assertTrue(q.futureEventDetected());
        assertTrue(q.warmingUp());
    }

    @Test
    void mapsEveryQualityStatusAndNullAges() {
        for (var avro : com.trading.contracts.feature.FeatureQualityStatus.values()) {
            FeatureQualityEvent quality = FeatureQualityEvent.newBuilder()
                    .setSyncStatus(BookSyncStatus.IN_SYNC)
                    .setStatus(avro)
                    .build();
            FeatureQuality q = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setQuality(quality).build()).quality();
            assertEquals(FeatureQualityStatus.valueOf(avro.name()), q.status());
            assertNull(q.orderBookStateAgeMs());
            assertNull(q.tradeAgeMs());
            assertEquals(List.of(), q.qualityReasons());
        }
    }

    @Test
    void mapsSyncStatusIncludingDegradedAsRecovering() {
        assertEquals(SyncStatus.IN_SYNC, syncStatusOf(BookSyncStatus.IN_SYNC));
        assertEquals(SyncStatus.RECOVERING, syncStatusOf(BookSyncStatus.RECOVERING));
        assertEquals(SyncStatus.RECOVERING, syncStatusOf(BookSyncStatus.DEGRADED));
        assertEquals(SyncStatus.OUT_OF_SYNC, syncStatusOf(BookSyncStatus.OUT_OF_SYNC));
        assertEquals(SyncStatus.STALE, syncStatusOf(BookSyncStatus.STALE));
        assertEquals(SyncStatus.UNKNOWN, syncStatusOf(BookSyncStatus.UNKNOWN));
    }

    // ---------------------------------------------------------------- source state / diagnostics

    @Test
    void mapsAllSourceStateFields() {
        FeatureSourceStateEvent sourceState = FeatureSourceStateEvent.newBuilder()
                .setSourceOrderBookStateTs(1699999999000L)
                .setSourceOrderBookStateSeq(42L)
                .setSourceOrderBookExchangeUpdateId(987654321L)
                .setSourceOrderBookStateEventId("ob-evt-7")
                .setSourceOrderBookProcessedTs(1699999999100L)
                .setSourceTradeTs(1699999998000L)
                .setPublishedDepth(20)
                .build();

        var ss = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setSourceState(sourceState).build()).sourceState();

        assertEquals(Instant.ofEpochMilli(1699999999000L), ss.sourceOrderBookStateTs());
        assertEquals(42L, ss.sourceOrderBookStateSeq());
        assertEquals(987654321L, ss.sourceOrderBookExchangeUpdateId());
        assertEquals("ob-evt-7", ss.sourceOrderBookStateEventId());
        assertEquals(Instant.ofEpochMilli(1699999999100L), ss.sourceOrderBookProcessedTs());
        assertEquals(Instant.ofEpochMilli(1699999998000L), ss.sourceTradeTs());
        assertEquals(20, ss.publishedDepth());
    }

    @Test
    void emptySourceStateMapsToNulls() {
        var ss = MarketFeaturesSnapshotAvroMapper.toDomain(
                fullEvent().setSourceState(FeatureSourceStateEvent.newBuilder().build()).build()).sourceState();

        assertNotNull(ss);
        assertNull(ss.sourceOrderBookStateTs());
        assertNull(ss.sourceOrderBookStateSeq());
        assertNull(ss.sourceOrderBookExchangeUpdateId());
        assertNull(ss.sourceOrderBookStateEventId());
        assertNull(ss.sourceOrderBookProcessedTs());
        assertNull(ss.sourceTradeTs());
        assertEquals(0, ss.publishedDepth());
    }

    @Test
    void mapsDiagnostics() {
        DiagnosticsEvent diagnostics = DiagnosticsEvent.newBuilder()
                .setFailedFeatureGroups(List.of("REGIME", "TRADE_FLOW_60S"))
                .setTotalFeatureGroups(6)
                .build();

        var d = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setDiagnostics(diagnostics).build()).diagnostics();

        assertEquals(List.of("REGIME", "TRADE_FLOW_60S"), d.failedFeatureGroups());
        assertEquals(6, d.totalFeatureGroups());
        assertTrue(d.hasFailures());
    }

    @Test
    void defaultDiagnosticsHasNoFailures() {
        var d = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().build()).diagnostics();

        assertNotNull(d);
        assertEquals(List.of(), d.failedFeatureGroups());
        assertFalse(d.hasFailures());
    }

    // ---------------------------------------------------------------- bbo / book

    @Test
    void mapsAllBboAndBookFields() {
        BboFeaturesEvent bbo = BboFeaturesEvent.newBuilder()
                .setBestBidPrice("65000.0")
                .setBestAskPrice("65000.5")
                .setBestBidQty("1.5")
                .setBestAskQty("2.5")
                .setSpreadAbs("0.5")
                .setSpreadBps("0.0769")
                .setMidPrice("65000.25")
                .setMicropriceTop1("65000.3125")
                .setMicropriceOffsetBps("0.0096")
                .build();
        BookFeaturesEvent book = BookFeaturesEvent.newBuilder()
                .setLevelsUsed(5)
                .setBidLiquidityTop5("12.5")
                .setAskLiquidityTop5("10.0")
                .setTop1Imbalance("-0.25")
                .setTop5Imbalance("0.1111")
                .setBestBidGapTicks("1")
                .setBestAskGapTicks("2")
                .build();

        MarketFeaturesSnapshot s = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setBbo(bbo).setBook(book).build());

        assertEquals(new BigDecimal("65000.0"), s.bbo().bestBidPrice());
        assertEquals(new BigDecimal("65000.5"), s.bbo().bestAskPrice());
        assertEquals(new BigDecimal("1.5"), s.bbo().bestBidQty());
        assertEquals(new BigDecimal("2.5"), s.bbo().bestAskQty());
        assertEquals(new BigDecimal("0.5"), s.bbo().spreadAbs());
        assertEquals(new BigDecimal("0.0769"), s.bbo().spreadBps());
        assertEquals(new BigDecimal("65000.25"), s.bbo().midPrice());
        assertEquals(new BigDecimal("65000.3125"), s.bbo().micropriceTop1());
        assertEquals(new BigDecimal("0.0096"), s.bbo().micropriceOffsetBps());

        assertEquals(5, s.book().levelsUsed());
        assertEquals(new BigDecimal("12.5"), s.book().bidLiquidityTop5());
        assertEquals(new BigDecimal("10.0"), s.book().askLiquidityTop5());
        assertEquals(new BigDecimal("-0.25"), s.book().top1Imbalance());
        assertEquals(new BigDecimal("0.1111"), s.book().top5Imbalance());
        assertEquals(new BigDecimal("1"), s.book().bestBidGapTicks());
        assertEquals(new BigDecimal("2"), s.book().bestAskGapTicks());
    }

    // ---------------------------------------------------------------- trade flow (all four windows)

    @Test
    void mapsAllTradeFlowWindows() {
        TradeFlowFeaturesEvent t = TradeFlowFeaturesEvent.newBuilder()
                .setLastTradePrice("65000.12")
                // 1s
                .setBuyAggressiveVolume1s("1.1").setSellAggressiveVolume1s("0.9").setTotalAggressiveVolume1s("2.0")
                .setSignedTradeFlow1s("0.2").setSignedFlowImbalance1s("0.10")
                .setTradeCount1s(12).setValidQtyTradeCount1s(11).setAggressiveTradeCount1s(10).setUnknownSideCount1s(1)
                .setTradeIntensity1s("3.3").setAvgTradeSize1s("0.17").setVwap1s("65000.5")
                // 5s
                .setBuyAggressiveVolume5s("5.5").setSellAggressiveVolume5s("3.5").setTotalAggressiveVolume5s("9.0")
                .setSignedTradeFlow5s("2.0").setSignedFlowImbalance5s("0.2222")
                .setTradeCount5s(47).setValidQtyTradeCount5s(46).setAggressiveTradeCount5s(44).setUnknownSideCount5s(2)
                .setTradeIntensity5s("9.4").setAvgTradeSize5s("0.19").setVwap5s("65001.0")
                // 15s
                .setBuyAggressiveVolume15s("15.0").setSellAggressiveVolume15s("12.0").setTotalAggressiveVolume15s("27.0")
                .setSignedTradeFlow15s("3.0").setSignedFlowImbalance15s("0.1111")
                .setTradeCount15s(140).setValidQtyTradeCount15s(138).setAggressiveTradeCount15s(130).setUnknownSideCount15s(3)
                .setTradeIntensity15s("9.3").setAvgTradeSize15s("0.19").setVwap15s("65000.8")
                // 60s
                .setBuyAggressiveVolume60s("60.0").setSellAggressiveVolume60s("58.0").setTotalAggressiveVolume60s("118.0")
                .setSignedTradeFlow60s("2.0").setSignedFlowImbalance60s("0.0169")
                .setTradeCount60s(560).setValidQtyTradeCount60s(555).setAggressiveTradeCount60s(540).setUnknownSideCount60s(9)
                .setTradeIntensity60s("9.3").setAvgTradeSize60s("0.21").setVwap60s("65000.2")
                .build();

        TradeFlowFeature f = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setTradeFlow(t).build()).tradeFlow();

        assertEquals(new BigDecimal("65000.12"), f.lastTradePrice());

        assertWindow(f.window1s(), "1.1", "0.9", "2.0", "0.2", "0.10", 12, 11, 10, 1, "3.3", "0.17", "65000.5");
        assertWindow(f.window5s(), "5.5", "3.5", "9.0", "2.0", "0.2222", 47, 46, 44, 2, "9.4", "0.19", "65001.0");
        assertWindow(f.window15s(), "15.0", "12.0", "27.0", "3.0", "0.1111", 140, 138, 130, 3, "9.3", "0.19", "65000.8");
        assertWindow(f.window60s(), "60.0", "58.0", "118.0", "2.0", "0.0169", 560, 555, 540, 9, "9.3", "0.21", "65000.2");
    }

    @Test
    void absentLongWindowsMapToNullFieldsNotZero() {
        // MFS v2 publishes 15s/60s as null while warming up. They must stay null in the domain.
        TradeFlowFeaturesEvent t = TradeFlowFeaturesEvent.newBuilder()
                .setTradeCount1s(3)
                .setTradeCount5s(9)
                .setSignedFlowImbalance5s("0.05")
                .build();

        TradeFlowFeature f = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setTradeFlow(t).build()).tradeFlow();

        assertEquals(3, f.window1s().tradeCount());
        assertEquals(9, f.window5s().tradeCount());
        assertEquals(new BigDecimal("0.05"), f.window5s().signedFlowImbalance());
        assertNull(f.window5s().buyAggressiveVolume());

        assertNotNull(f.window15s());
        assertNull(f.window15s().tradeCount());
        assertNull(f.window15s().signedFlowImbalance());
        assertNull(f.window15s().validQtyTradeCount());
        assertNotNull(f.window60s());
        assertNull(f.window60s().tradeCount());
        assertNull(f.window60s().vwap());
    }

    // ---------------------------------------------------------------- regime

    @Test
    void mapsAllRegimeFieldsAndIgnoresDeprecatedVolatilityAlias() {
        ShortTermRegimeFeaturesEvent regime = ShortTermRegimeFeaturesEvent.newBuilder()
                .setLastTradeDistanceToMidBps("1.5")
                .setShortTermVolatility1s("999.0")
                .setRealizedVolatilityBps1s("4.2")
                .setRealizedVolatilityBps5s("9.1")
                .setRealizedVolatilityBps15s("15.7")
                .setRealizedVolatilityBps60s("31.0")
                .setPriceChangeBps5s("-2.5")
                .setPriceChangeBps15s("3.0")
                .setPriceChangeBps60s("12.25")
                .setHighLowRangeBps60s("40.5")
                .build();

        RegimeFeature r = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setRegime(regime).build()).regime();

        assertEquals(new BigDecimal("1.5"), r.lastTradeDistanceToMidBps());
        assertEquals(new BigDecimal("4.2"), r.realizedVolatilityBps1s());
        assertEquals(new BigDecimal("9.1"), r.realizedVolatilityBps5s());
        assertEquals(new BigDecimal("15.7"), r.realizedVolatilityBps15s());
        assertEquals(new BigDecimal("31.0"), r.realizedVolatilityBps60s());
        assertEquals(new BigDecimal("-2.5"), r.priceChangeBps5s());
        assertEquals(new BigDecimal("3.0"), r.priceChangeBps15s());
        assertEquals(new BigDecimal("12.25"), r.priceChangeBps60s());
        assertEquals(new BigDecimal("40.5"), r.highLowRangeBps60s());
    }

    @Test
    void missingRegimeValuesMapToNull() {
        ShortTermRegimeFeaturesEvent regime = ShortTermRegimeFeaturesEvent.newBuilder()
                .setShortTermVolatility1s("999.0")
                .build();

        RegimeFeature r = MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setRegime(regime).build()).regime();

        assertNull(r.realizedVolatilityBps1s());
        assertNull(r.realizedVolatilityBps60s());
        assertNull(r.priceChangeBps5s());
        assertNull(r.highLowRangeBps60s());
    }

    // ---------------------------------------------------------------- errors

    @Test
    void invalidDecimalThrowsAvroMappingException() {
        TradeFlowFeaturesEvent t = TradeFlowFeaturesEvent.newBuilder()
                .setSignedFlowImbalance5s("not-a-number")
                .setTradeCount5s(47)
                .build();

        assertThrows(AvroMappingException.class,
                () -> MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setTradeFlow(t).build()));
    }

    @Test
    void nullEventOrMetadataThrowsAvroMappingException() {
        assertThrows(AvroMappingException.class, () -> MarketFeaturesSnapshotAvroMapper.toDomain(null));
    }

    // ---------------------------------------------------------------- helpers

    private static void assertWindow(TradeFlowWindow w,
                                     String buy, String sell, String total, String signed, String imbalance,
                                     int count, int validQty, int aggressive, int unknownSide,
                                     String intensity, String avgSize, String vwap) {
        assertEquals(new BigDecimal(buy), w.buyAggressiveVolume());
        assertEquals(new BigDecimal(sell), w.sellAggressiveVolume());
        assertEquals(new BigDecimal(total), w.totalAggressiveVolume());
        assertEquals(new BigDecimal(signed), w.signedTradeFlow());
        assertEquals(new BigDecimal(imbalance), w.signedFlowImbalance());
        assertEquals(count, w.tradeCount());
        assertEquals(validQty, w.validQtyTradeCount());
        assertEquals(aggressive, w.aggressiveTradeCount());
        assertEquals(unknownSide, w.unknownSideCount());
        assertEquals(new BigDecimal(intensity), w.tradeIntensity());
        assertEquals(new BigDecimal(avgSize), w.avgTradeSize());
        assertEquals(new BigDecimal(vwap), w.vwap());
    }

    private static SyncStatus syncStatusOf(BookSyncStatus status) {
        FeatureQualityEvent quality = FeatureQualityEvent.newBuilder().setSyncStatus(status).build();
        return MarketFeaturesSnapshotAvroMapper.toDomain(fullEvent().setQuality(quality).build()).quality().syncStatus();
    }

    /** A complete, valid MFS v2 event with schema defaults for every optional field. */
    private static MarketFeaturesSnapshotEvent.Builder fullEvent() {
        return MarketFeaturesSnapshotEvent.newBuilder()
                .setMetadata(metadata())
                .setComputedTs(1700000002000L)
                .setFeatureSetVersion("mfs-core-v2")
                .setQuality(FeatureQualityEvent.newBuilder().setSyncStatus(BookSyncStatus.IN_SYNC).build())
                .setSourceState(FeatureSourceStateEvent.newBuilder().build())
                .setBbo(BboFeaturesEvent.newBuilder().build())
                .setBook(BookFeaturesEvent.newBuilder().setLevelsUsed(5).build())
                .setTradeFlow(TradeFlowFeaturesEvent.newBuilder().build())
                .setRegime(ShortTermRegimeFeaturesEvent.newBuilder().build());
    }

    private static MetadataEvent metadata() {
        return MetadataEvent.newBuilder()
                .setSchemaVersion(1)
                .setEventType("MARKET_FEATURES_SNAPSHOT")
                .setExchange("binance")
                .setMarketType("spot")
                .setBase("BTC")
                .setQuote("USDT")
                .setSymbol("BTCUSDT")
                .setInstrumentId("binance:spot:BTCUSDT")
                .setEventId("feat-1")
                .setSourceStream("market.feature.snapshot.v1")
                .setExchangeTs(1700000000000L)
                .setReceivedTs(1700000001000L)
                .setProcessedTs(1700000001500L)
                .build();
    }
}

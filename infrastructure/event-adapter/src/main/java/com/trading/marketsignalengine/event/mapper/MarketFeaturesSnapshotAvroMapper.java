package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.DiagnosticsEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.FeatureSourceStateEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureSourceState;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Avro → domain mapping of the full MFS v2 {@code MarketFeaturesSnapshotEvent}. Every contract field
 * is mapped; nothing is silently dropped except the deprecated {@code shortTermVolatility1s} alias
 * (superseded by {@code realizedVolatilityBps1s}). Null semantics are preserved: a {@code null} /
 * blank upstream value stays {@code null} in the domain so rules fail closed instead of reading zero.
 * A {@code 0} upstream {@code evaluationTs} (the schema default, i.e. a writer that predates the
 * field) maps to {@code null}.
 */
public final class MarketFeaturesSnapshotAvroMapper {

    private MarketFeaturesSnapshotAvroMapper() {
    }

    public static MarketFeaturesSnapshot toDomain(MarketFeaturesSnapshotEvent event) {
        if (event == null) {
            throw new AvroMappingException("MarketFeaturesSnapshotEvent must not be null");
        }
        if (event.getMetadata() == null) {
            throw new AvroMappingException("metadata is null");
        }

        var metadata = event.getMetadata();

        return MarketFeaturesSnapshot.builder()
                .snapshotId(metadata.getEventId())
                .exchange(metadata.getExchange())
                .marketType(metadata.getMarketType())
                .base(metadata.getBase())
                .quote(metadata.getQuote())
                .symbol(metadata.getSymbol())
                .instrumentId(metadata.getInstrumentId())
                .eventTime(instantFromEpochMillis(metadata.getExchangeTs()))
                .receivedAt(instantFromEpochMillis(metadata.getReceivedTs()))
                .computedAt(instantFromEpochMillis(event.getComputedTs()))
                .evaluationTs(instantFromEpochMillisOrNull(event.getEvaluationTs()))
                .featureSetVersion(event.getFeatureSetVersion())
                .triggerSource(string(event.getTriggerSource()))
                .configHash(string(event.getConfigHash()))
                .quality(mapQuality(event.getQuality()))
                .sourceState(mapSourceState(event.getSourceState()))
                .diagnostics(mapDiagnostics(event.getDiagnostics()))
                .bbo(mapBbo(event.getBbo()))
                .book(mapBook(event.getBook()))
                .tradeFlow(mapTradeFlow(event.getTradeFlow()))
                .regime(mapRegime(event.getRegime()))
                .build();
    }

    private static FeatureQuality mapQuality(FeatureQualityEvent quality) {
        if (quality == null) {
            return null;
        }
        return FeatureQuality.builder()
                .syncStatus(mapSyncStatus(quality.getSyncStatus()))
                .staleOrderBookState(quality.getStaleOrderBookState())
                .staleTrades(quality.getStaleTrades())
                .incompleteBook(quality.getIncompleteBook())
                .orderBookStateAgeMs(quality.getOrderBookStateAgeMs())
                .tradeAgeMs(quality.getTradeAgeMs())
                .sourceOrderBookTrusted(quality.getSourceOrderBookTrusted())
                .sourceOrderBookReason(
                        quality.getSourceOrderBookReason() == null ? null : quality.getSourceOrderBookReason().name())
                .status(mapQualityStatus(quality.getStatus()))
                .qualityReasons(strings(quality.getQualityReasons()))
                .futureEventDetected(quality.getFutureEventDetected())
                .warmingUp(quality.getWarmingUp())
                .build();
    }

    private static FeatureSourceState mapSourceState(FeatureSourceStateEvent sourceState) {
        if (sourceState == null) {
            return null;
        }
        return FeatureSourceState.builder()
                .sourceOrderBookStateTs(instantFromEpochMillisOrNull(sourceState.getSourceOrderBookStateTs()))
                .sourceOrderBookStateSeq(sourceState.getSourceOrderBookStateSeq())
                .sourceOrderBookExchangeUpdateId(sourceState.getSourceOrderBookExchangeUpdateId())
                .sourceOrderBookStateEventId(string(sourceState.getSourceOrderBookStateEventId()))
                .sourceOrderBookProcessedTs(instantFromEpochMillisOrNull(sourceState.getSourceOrderBookProcessedTs()))
                .sourceTradeTs(instantFromEpochMillisOrNull(sourceState.getSourceTradeTs()))
                .publishedDepth(sourceState.getPublishedDepth())
                .build();
    }

    private static FeatureDiagnostics mapDiagnostics(DiagnosticsEvent diagnostics) {
        if (diagnostics == null) {
            return null;
        }
        return FeatureDiagnostics.builder()
                .failedFeatureGroups(strings(diagnostics.getFailedFeatureGroups()))
                .totalFeatureGroups(diagnostics.getTotalFeatureGroups())
                .build();
    }

    private static BboFeature mapBbo(BboFeaturesEvent bbo) {
        if (bbo == null) {
            return null;
        }
        return BboFeature.builder()
                .bestBidPrice(decimal("bbo.bestBidPrice", bbo.getBestBidPrice()))
                .bestAskPrice(decimal("bbo.bestAskPrice", bbo.getBestAskPrice()))
                .bestBidQty(decimal("bbo.bestBidQty", bbo.getBestBidQty()))
                .bestAskQty(decimal("bbo.bestAskQty", bbo.getBestAskQty()))
                .spreadAbs(decimal("bbo.spreadAbs", bbo.getSpreadAbs()))
                .spreadBps(decimal("bbo.spreadBps", bbo.getSpreadBps()))
                .midPrice(decimal("bbo.midPrice", bbo.getMidPrice()))
                .micropriceTop1(decimal("bbo.micropriceTop1", bbo.getMicropriceTop1()))
                .micropriceOffsetBps(decimal("bbo.micropriceOffsetBps", bbo.getMicropriceOffsetBps()))
                .build();
    }

    private static BookFeature mapBook(BookFeaturesEvent book) {
        if (book == null) {
            return null;
        }
        return BookFeature.builder()
                .levelsUsed(book.getLevelsUsed())
                .bidLiquidityTop5(decimal("book.bidLiquidityTop5", book.getBidLiquidityTop5()))
                .askLiquidityTop5(decimal("book.askLiquidityTop5", book.getAskLiquidityTop5()))
                .top1Imbalance(decimal("book.top1Imbalance", book.getTop1Imbalance()))
                .top5Imbalance(decimal("book.top5Imbalance", book.getTop5Imbalance()))
                .bestBidGapTicks(decimal("book.bestBidGapTicks", book.getBestBidGapTicks()))
                .bestAskGapTicks(decimal("book.bestAskGapTicks", book.getBestAskGapTicks()))
                .build();
    }

    private static TradeFlowFeature mapTradeFlow(TradeFlowFeaturesEvent t) {
        if (t == null) {
            return null;
        }
        return TradeFlowFeature.builder()
                .lastTradePrice(decimal("tradeFlow.lastTradePrice", t.getLastTradePrice()))
                .window1s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(decimal("tradeFlow.buyAggressiveVolume1s", t.getBuyAggressiveVolume1s()))
                        .sellAggressiveVolume(decimal("tradeFlow.sellAggressiveVolume1s", t.getSellAggressiveVolume1s()))
                        .totalAggressiveVolume(decimal("tradeFlow.totalAggressiveVolume1s", t.getTotalAggressiveVolume1s()))
                        .signedTradeFlow(decimal("tradeFlow.signedTradeFlow1s", t.getSignedTradeFlow1s()))
                        .signedFlowImbalance(decimal("tradeFlow.signedFlowImbalance1s", t.getSignedFlowImbalance1s()))
                        .tradeCount(t.getTradeCount1s())
                        .validQtyTradeCount(t.getValidQtyTradeCount1s())
                        .aggressiveTradeCount(t.getAggressiveTradeCount1s())
                        .unknownSideCount(t.getUnknownSideCount1s())
                        .tradeIntensity(decimal("tradeFlow.tradeIntensity1s", t.getTradeIntensity1s()))
                        .avgTradeSize(decimal("tradeFlow.avgTradeSize1s", t.getAvgTradeSize1s()))
                        .vwap(decimal("tradeFlow.vwap1s", t.getVwap1s()))
                        .build())
                .window5s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(decimal("tradeFlow.buyAggressiveVolume5s", t.getBuyAggressiveVolume5s()))
                        .sellAggressiveVolume(decimal("tradeFlow.sellAggressiveVolume5s", t.getSellAggressiveVolume5s()))
                        .totalAggressiveVolume(decimal("tradeFlow.totalAggressiveVolume5s", t.getTotalAggressiveVolume5s()))
                        .signedTradeFlow(decimal("tradeFlow.signedTradeFlow5s", t.getSignedTradeFlow5s()))
                        .signedFlowImbalance(decimal("tradeFlow.signedFlowImbalance5s", t.getSignedFlowImbalance5s()))
                        .tradeCount(t.getTradeCount5s())
                        .validQtyTradeCount(t.getValidQtyTradeCount5s())
                        .aggressiveTradeCount(t.getAggressiveTradeCount5s())
                        .unknownSideCount(t.getUnknownSideCount5s())
                        .tradeIntensity(decimal("tradeFlow.tradeIntensity5s", t.getTradeIntensity5s()))
                        .avgTradeSize(decimal("tradeFlow.avgTradeSize5s", t.getAvgTradeSize5s()))
                        .vwap(decimal("tradeFlow.vwap5s", t.getVwap5s()))
                        .build())
                .window15s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(decimal("tradeFlow.buyAggressiveVolume15s", t.getBuyAggressiveVolume15s()))
                        .sellAggressiveVolume(decimal("tradeFlow.sellAggressiveVolume15s", t.getSellAggressiveVolume15s()))
                        .totalAggressiveVolume(decimal("tradeFlow.totalAggressiveVolume15s", t.getTotalAggressiveVolume15s()))
                        .signedTradeFlow(decimal("tradeFlow.signedTradeFlow15s", t.getSignedTradeFlow15s()))
                        .signedFlowImbalance(decimal("tradeFlow.signedFlowImbalance15s", t.getSignedFlowImbalance15s()))
                        .tradeCount(t.getTradeCount15s())
                        .validQtyTradeCount(t.getValidQtyTradeCount15s())
                        .aggressiveTradeCount(t.getAggressiveTradeCount15s())
                        .unknownSideCount(t.getUnknownSideCount15s())
                        .tradeIntensity(decimal("tradeFlow.tradeIntensity15s", t.getTradeIntensity15s()))
                        .avgTradeSize(decimal("tradeFlow.avgTradeSize15s", t.getAvgTradeSize15s()))
                        .vwap(decimal("tradeFlow.vwap15s", t.getVwap15s()))
                        .build())
                .window60s(TradeFlowWindow.builder()
                        .buyAggressiveVolume(decimal("tradeFlow.buyAggressiveVolume60s", t.getBuyAggressiveVolume60s()))
                        .sellAggressiveVolume(decimal("tradeFlow.sellAggressiveVolume60s", t.getSellAggressiveVolume60s()))
                        .totalAggressiveVolume(decimal("tradeFlow.totalAggressiveVolume60s", t.getTotalAggressiveVolume60s()))
                        .signedTradeFlow(decimal("tradeFlow.signedTradeFlow60s", t.getSignedTradeFlow60s()))
                        .signedFlowImbalance(decimal("tradeFlow.signedFlowImbalance60s", t.getSignedFlowImbalance60s()))
                        .tradeCount(t.getTradeCount60s())
                        .validQtyTradeCount(t.getValidQtyTradeCount60s())
                        .aggressiveTradeCount(t.getAggressiveTradeCount60s())
                        .unknownSideCount(t.getUnknownSideCount60s())
                        .tradeIntensity(decimal("tradeFlow.tradeIntensity60s", t.getTradeIntensity60s()))
                        .avgTradeSize(decimal("tradeFlow.avgTradeSize60s", t.getAvgTradeSize60s()))
                        .vwap(decimal("tradeFlow.vwap60s", t.getVwap60s()))
                        .build())
                .build();
    }

    private static RegimeFeature mapRegime(ShortTermRegimeFeaturesEvent regime) {
        if (regime == null) {
            return null;
        }
        // shortTermVolatility1s is a deprecated alias and is intentionally not mapped.
        return RegimeFeature.builder()
                .lastTradeDistanceToMidBps(
                        decimal("regime.lastTradeDistanceToMidBps", regime.getLastTradeDistanceToMidBps()))
                .realizedVolatilityBps1s(
                        decimal("regime.realizedVolatilityBps1s", regime.getRealizedVolatilityBps1s()))
                .realizedVolatilityBps5s(
                        decimal("regime.realizedVolatilityBps5s", regime.getRealizedVolatilityBps5s()))
                .realizedVolatilityBps15s(
                        decimal("regime.realizedVolatilityBps15s", regime.getRealizedVolatilityBps15s()))
                .realizedVolatilityBps60s(
                        decimal("regime.realizedVolatilityBps60s", regime.getRealizedVolatilityBps60s()))
                .priceChangeBps5s(decimal("regime.priceChangeBps5s", regime.getPriceChangeBps5s()))
                .priceChangeBps15s(decimal("regime.priceChangeBps15s", regime.getPriceChangeBps15s()))
                .priceChangeBps60s(decimal("regime.priceChangeBps60s", regime.getPriceChangeBps60s()))
                .highLowRangeBps60s(decimal("regime.highLowRangeBps60s", regime.getHighLowRangeBps60s()))
                .build();
    }

    private static BigDecimal decimal(String fieldName, CharSequence value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            throw new AvroMappingException("Invalid decimal value for " + fieldName + ": " + text, ex);
        }
    }

    private static String string(CharSequence value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static List<String> strings(List<? extends CharSequence> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(CharSequence::toString).toList();
    }

    private static Instant instantFromEpochMillis(long value) {
        return Instant.ofEpochMilli(value);
    }

    private static Instant instantFromEpochMillisOrNull(Long value) {
        return value == null || value == 0L ? null : Instant.ofEpochMilli(value);
    }

    private static SyncStatus mapSyncStatus(BookSyncStatus syncStatus) {
        if (syncStatus == null) {
            return SyncStatus.UNKNOWN;
        }
        return switch (syncStatus) {
            case IN_SYNC -> SyncStatus.IN_SYNC;
            case RECOVERING, DEGRADED -> SyncStatus.RECOVERING;
            case OUT_OF_SYNC -> SyncStatus.OUT_OF_SYNC;
            case STALE -> SyncStatus.STALE;
            case UNKNOWN -> SyncStatus.UNKNOWN;
        };
    }

    private static FeatureQualityStatus mapQualityStatus(
            com.trading.contracts.feature.FeatureQualityStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case OK -> FeatureQualityStatus.OK;
            case DEGRADED -> FeatureQualityStatus.DEGRADED;
            case UNSAFE -> FeatureQualityStatus.UNSAFE;
            case NO_DATA -> FeatureQualityStatus.NO_DATA;
        };
    }
}

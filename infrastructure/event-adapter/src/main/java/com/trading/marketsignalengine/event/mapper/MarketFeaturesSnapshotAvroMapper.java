package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import java.math.BigDecimal;
import java.time.Instant;

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
                .featureSetVersion(event.getFeatureSetVersion())
                .quality(mapQuality(event.getQuality()))
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

    private static TradeFlowFeature mapTradeFlow(TradeFlowFeaturesEvent tradeFlow) {
        if (tradeFlow == null) {
            return null;
        }
        return TradeFlowFeature.builder()
                .lastTradePrice(decimal("tradeFlow.lastTradePrice", tradeFlow.getLastTradePrice()))
                .signedTradeFlow1s(decimal("tradeFlow.signedTradeFlow1s", tradeFlow.getSignedTradeFlow1s()))
                .signedTradeFlow5s(decimal("tradeFlow.signedTradeFlow5s", tradeFlow.getSignedTradeFlow5s()))
                .tradeCount1s(tradeFlow.getTradeCount1s())
                .tradeIntensity1s(decimal("tradeFlow.tradeIntensity1s", tradeFlow.getTradeIntensity1s()))
                .avgTradeSize1s(decimal("tradeFlow.avgTradeSize1s", tradeFlow.getAvgTradeSize1s()))
                .vwap1s(decimal("tradeFlow.vwap1s", tradeFlow.getVwap1s()))
                .build();
    }

    private static RegimeFeature mapRegime(ShortTermRegimeFeaturesEvent regime) {
        if (regime == null) {
            return null;
        }
        return RegimeFeature.builder()
                .lastTradeDistanceToMidBps(
                        decimal("regime.lastTradeDistanceToMidBps", regime.getLastTradeDistanceToMidBps()))
                .shortTermVolatility1s(
                        decimal("regime.shortTermVolatility1s", regime.getShortTermVolatility1s()))
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

    private static Instant instantFromEpochMillis(long value) {
        return Instant.ofEpochMilli(value);
    }

    private static SyncStatus mapSyncStatus(BookSyncStatus syncStatus) {
        if (syncStatus == null) {
            return SyncStatus.UNKNOWN;
        }
        return switch (syncStatus) {
            case IN_SYNC -> SyncStatus.IN_SYNC;
            // DEGRADED has no dedicated domain state; treat it as RECOVERING so the
            // quality gate routes it to a risk-off NO_TRADE_RECOVERING_BOOK signal.
            case RECOVERING, DEGRADED -> SyncStatus.RECOVERING;
            case OUT_OF_SYNC -> SyncStatus.OUT_OF_SYNC;
            case STALE -> SyncStatus.STALE;
            case UNKNOWN -> SyncStatus.UNKNOWN;
        };
    }
}

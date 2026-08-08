package com.trading.marketsignalengine.event.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.FeatureSourceStateEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketFeaturesSnapshotAvroMapperTest {

    @Test
    void mapsAllTradeFlowFields() {
        TradeFlowFeaturesEvent tradeFlowEvent = TradeFlowFeaturesEvent.newBuilder()
                .setLastTradePrice("65000.12")
                .setBuyAggressiveVolume1s("1.1")
                .setSellAggressiveVolume1s("0.9")
                .setTotalAggressiveVolume1s("2.0")
                .setSignedTradeFlow1s("0.2")
                .setSignedFlowImbalance1s("0.10")
                .setTradeCount1s(12)
                .setTradeIntensity1s("3.3")
                .setAvgTradeSize1s("0.17")
                .setVwap1s("65000.5")
                .setBuyAggressiveVolume5s("5.5")
                .setSellAggressiveVolume5s("3.5")
                .setTotalAggressiveVolume5s("9.0")
                .setSignedTradeFlow5s("2.0")
                .setSignedFlowImbalance5s("0.2222")
                .setTradeCount5s(47)
                .setTradeIntensity5s("9.4")
                .setAvgTradeSize5s("0.19")
                .setVwap5s("65001.0")
                .build();

        MarketFeaturesSnapshot snapshot =
                MarketFeaturesSnapshotAvroMapper.toDomain(snapshotEventWith(tradeFlowEvent));
        TradeFlowFeature tradeFlow = snapshot.tradeFlow();

        assertEquals(new BigDecimal("65000.12"), tradeFlow.lastTradePrice());

        assertEquals(new BigDecimal("1.1"), tradeFlow.buyAggressiveVolume1s());
        assertEquals(new BigDecimal("0.9"), tradeFlow.sellAggressiveVolume1s());
        assertEquals(new BigDecimal("2.0"), tradeFlow.totalAggressiveVolume1s());
        assertEquals(new BigDecimal("0.2"), tradeFlow.signedTradeFlow1s());
        assertEquals(new BigDecimal("0.10"), tradeFlow.signedFlowImbalance1s());
        assertEquals(12, tradeFlow.tradeCount1s());
        assertEquals(new BigDecimal("3.3"), tradeFlow.tradeIntensity1s());
        assertEquals(new BigDecimal("0.17"), tradeFlow.avgTradeSize1s());
        assertEquals(new BigDecimal("65000.5"), tradeFlow.vwap1s());

        assertEquals(new BigDecimal("5.5"), tradeFlow.buyAggressiveVolume5s());
        assertEquals(new BigDecimal("3.5"), tradeFlow.sellAggressiveVolume5s());
        assertEquals(new BigDecimal("9.0"), tradeFlow.totalAggressiveVolume5s());
        assertEquals(new BigDecimal("2.0"), tradeFlow.signedTradeFlow5s());
        assertEquals(new BigDecimal("0.2222"), tradeFlow.signedFlowImbalance5s());
        assertEquals(47, tradeFlow.tradeCount5s());
        assertEquals(new BigDecimal("9.4"), tradeFlow.tradeIntensity5s());
        assertEquals(new BigDecimal("0.19"), tradeFlow.avgTradeSize5s());
        assertEquals(new BigDecimal("65001.0"), tradeFlow.vwap5s());
    }

    @Test
    void invalidDecimalThrowsAvroMappingException() {
        TradeFlowFeaturesEvent tradeFlowEvent = TradeFlowFeaturesEvent.newBuilder()
                .setSignedFlowImbalance5s("not-a-number")
                .setTradeCount5s(47)
                .build();

        assertThrows(AvroMappingException.class,
                () -> MarketFeaturesSnapshotAvroMapper.toDomain(snapshotEventWith(tradeFlowEvent)));
    }

    private static MarketFeaturesSnapshotEvent snapshotEventWith(TradeFlowFeaturesEvent tradeFlow) {
        return MarketFeaturesSnapshotEvent.newBuilder()
                .setMetadata(metadata())
                .setComputedTs(1700000002000L)
                .setFeatureSetVersion("mfs-core-v1")
                .setQuality(FeatureQualityEvent.newBuilder().setSyncStatus(BookSyncStatus.IN_SYNC).build())
                .setSourceState(FeatureSourceStateEvent.newBuilder().build())
                .setBbo(BboFeaturesEvent.newBuilder().build())
                .setBook(BookFeaturesEvent.newBuilder().setLevelsUsed(5).build())
                .setTradeFlow(tradeFlow)
                .setRegime(ShortTermRegimeFeaturesEvent.newBuilder().build())
                .build();
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
                .setSourceStream("state.market.features.v1")
                .setExchangeTs(1700000000000L)
                .setReceivedTs(1700000001000L)
                .setProcessedTs(1700000001500L)
                .build();
    }
}

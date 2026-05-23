package com.trading.marketsignalengine.event.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketFeaturesEventMapperTest {

    private final MarketFeaturesEventMapper mapper = new MarketFeaturesEventMapper();

    @Test
    void nullDecimalStringMapsToNull() {
        assertNull(mapper.decimal("testField", null));
    }

    @Test
    void blankDecimalStringMapsToNull() {
        assertNull(mapper.decimal("testField", "   "));
    }

    @Test
    void validDecimalStringMapsToBigDecimal() {
        assertEquals(new BigDecimal("1.23"), mapper.decimal("testField", "1.23"));
    }

    @Test
    void invalidDecimalStringThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> mapper.decimal("testField", "not-a-number"));
    }

    @Test
    void syncStatusMapsCorrectly() {
        assertEquals(SyncStatus.IN_SYNC, mapper.mapSyncStatus(BookSyncStatus.IN_SYNC));
        assertEquals(SyncStatus.RECOVERING, mapper.mapSyncStatus(BookSyncStatus.RECOVERING));
        assertEquals(SyncStatus.OUT_OF_SYNC, mapper.mapSyncStatus(BookSyncStatus.OUT_OF_SYNC));
        assertEquals(SyncStatus.STALE, mapper.mapSyncStatus(BookSyncStatus.STALE));
        assertEquals(SyncStatus.UNKNOWN, mapper.mapSyncStatus(null));
    }

    @Test
    void mapsFullEventToDomain() {
        MetadataEvent metadata = MetadataEvent.newBuilder()
                .setSchemaVersion(1)
                .setEventType("MARKET_FEATURES_SNAPSHOT")
                .setExchange("binance")
                .setMarketType("spot")
                .setBase("BTC")
                .setQuote("USDT")
                .setSymbol("BTCUSDT")
                .setInstrumentId("binance:spot:BTCUSDT")
                .setEventId("evt-1")
                .setSourceStream("market-feature-engine")
                .setExchangeTs(1_700_000_000_000L)
                .setReceivedTs(1_700_000_000_100L)
                .setProcessedTs(1_700_000_000_200L)
                .build();

        FeatureQualityEvent quality = FeatureQualityEvent.newBuilder()
                .setSyncStatus(BookSyncStatus.IN_SYNC)
                .setStaleBbo(false)
                .setStaleBook(false)
                .setStaleTrades(false)
                .setIncompleteBook(false)
                .build();

        BboFeaturesEvent bbo = BboFeaturesEvent.newBuilder()
                .setSpreadBps("1.5")
                .build();

        MarketFeaturesSnapshotEvent event = MarketFeaturesSnapshotEvent.newBuilder()
                .setMetadata(metadata)
                .setComputedTs(1_700_000_000_300L)
                .setFeatureSetVersion("mfs-core-v1")
                .setQuality(quality)
                .setSourceState(FeatureSourceStateEvent.newBuilder().build())
                .setBbo(bbo)
                .setBook(BookFeaturesEvent.newBuilder().build())
                .setTradeFlow(TradeFlowFeaturesEvent.newBuilder().build())
                .setRegime(ShortTermRegimeFeaturesEvent.newBuilder().build())
                .build();

        var domain = mapper.toDomain(event);

        assertEquals("evt-1", domain.snapshotId());
        assertEquals("binance", domain.exchange());
        assertEquals("BTCUSDT", domain.symbol());
        assertEquals(new BigDecimal("1.5"), domain.bbo().spreadBps());
        assertEquals(SyncStatus.IN_SYNC, domain.quality().syncStatus());
    }
}

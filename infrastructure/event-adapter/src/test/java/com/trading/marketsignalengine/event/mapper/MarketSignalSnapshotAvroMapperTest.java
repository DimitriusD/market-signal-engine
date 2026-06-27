package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketSignalSnapshotAvroMapperTest {

    @Test
    void mapsSnapshotToAvro() {
        Instant eventTime = Instant.parse("2024-01-15T10:00:00Z");
        Instant createdAt = Instant.parse("2024-01-15T10:00:01Z");

        MarketSignalSnapshot snapshot = new MarketSignalSnapshot(
                "sig-1",
                "feat-1",
                "binance",
                "spot",
                "BTC",
                "USDT",
                "BTCUSDT",
                "binance:spot:BTCUSDT",
                eventTime,
                createdAt,
                "mfs-core-v1",
                "mse-signals-v1",
                MarketBias.BULLISH,
                new BigDecimal("0.5"),
                RiskLevel.NORMAL,
                List.of(MarketSignal.bullish(
                        SignalType.BUY_PRESSURE,
                        SignalStrength.STRONG,
                        new BigDecimal("0.9"),
                        "buy pressure",
                        Map.of("window", "5s"))));

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(snapshot);

        assertEquals("MARKET_SIGNAL_SNAPSHOT", event.getMetadata().getEventType());
        assertEquals("sig-1", event.getMetadata().getEventId());
        assertEquals("BTC", event.getMetadata().getBase());
        assertEquals("USDT", event.getMetadata().getQuote());
        assertEquals("BTCUSDT", event.getMetadata().getSymbol());
        assertEquals("binance:spot:BTCUSDT", event.getMetadata().getInstrumentId());
        assertEquals("feat-1", event.getSourceFeatureEventId());
        assertEquals("mfs-core-v1", event.getSourceFeatureSetVersion());
        assertEquals(createdAt.toEpochMilli(), event.getEvaluatedTs());
        assertEquals("mse-signals-v1", event.getSignalSetVersion());
        assertEquals("BULLISH", event.getMarketBias());
        assertEquals("0.5", event.getMarketBiasScore());
        assertEquals("NORMAL", event.getRiskLevel());
        assertEquals(1, event.getSignals().size());
        assertEquals("BUY_PRESSURE", event.getSignals().getFirst().getType());
        assertEquals("5s", event.getSignals().getFirst().getAttributes().get("window"));
    }

    @Test
    void nullSnapshotThrowsAvroMappingException() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(null));
    }
}

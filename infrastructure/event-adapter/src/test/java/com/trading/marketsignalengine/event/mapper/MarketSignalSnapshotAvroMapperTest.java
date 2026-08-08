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

    private static final Instant EVENT_TIME = Instant.parse("2024-01-15T10:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2024-01-15T10:00:01Z");

    @Test
    void mapsSnapshotToAvro() {
        Instant validUntil = CREATED_AT.plusMillis(2_000L);

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), validUntil, 2_000L));

        assertEquals("MARKET_SIGNAL_SNAPSHOT", event.getMetadata().getEventType());
        assertEquals("sig-1", event.getMetadata().getEventId());
        assertEquals("BTC", event.getMetadata().getBase());
        assertEquals("USDT", event.getMetadata().getQuote());
        assertEquals("BTCUSDT", event.getMetadata().getSymbol());
        assertEquals("binance:spot:BTCUSDT", event.getMetadata().getInstrumentId());
        assertEquals("feat-1", event.getSourceFeatureEventId());
        assertEquals("mfs-core-v1", event.getSourceFeatureSetVersion());
        assertEquals(CREATED_AT.toEpochMilli(), event.getEvaluatedTs());
        assertEquals("mse-signals-v5", event.getSignalSetVersion());
        assertEquals("BULLISH", event.getMarketBias());
        assertEquals("0.5", event.getMarketBiasScore());
        assertEquals("NORMAL", event.getRiskLevel());
        assertEquals(1, event.getSignals().size());
        assertEquals("BUY_PRESSURE", event.getSignals().getFirst().getType());
        assertEquals("5s", event.getSignals().getFirst().getAttributes().get("window"));
    }

    @Test
    void mapsSetup() {
        MarketSetup setup = new MarketSetup(
                SetupSide.LONG,
                SetupType.MICROSTRUCTURE_MOMENTUM,
                SignalStrength.STRONG,
                new BigDecimal("0.8"),
                "long setup forming",
                Map.of("trigger", "buy_pressure"));

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(setup, CREATED_AT.plusMillis(2_000L), 2_000L));

        assertEquals("LONG", event.getSetup().getSide());
        assertEquals("MICROSTRUCTURE_MOMENTUM", event.getSetup().getType());
        assertEquals("STRONG", event.getSetup().getStrength());
        assertEquals("0.8", event.getSetup().getConfidence());
        assertEquals("long setup forming", event.getSetup().getReason());
        assertEquals("buy_pressure", event.getSetup().getAttributes().get("trigger"));
    }

    @Test
    void mapsValidity() {
        Instant validUntil = Instant.parse("2024-01-15T10:00:03Z");

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), validUntil, 2_000L));

        assertEquals(validUntil.toEpochMilli(), event.getValidUntilTs().longValue());
        assertEquals(2_000L, event.getTtlMs().longValue());
    }

    @Test
    void mapsNoneSetup() {
        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("no directional setup"), CREATED_AT.plusMillis(1_000L), 1_000L));

        assertEquals("NONE", event.getSetup().getSide());
        assertEquals("NONE", event.getSetup().getType());
    }

    @Test
    void keepsExistingFields() {
        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), CREATED_AT.plusMillis(2_000L), 2_000L));

        assertEquals("feat-1", event.getSourceFeatureEventId());
        assertEquals("mse-signals-v5", event.getSignalSetVersion());
        assertEquals("BULLISH", event.getMarketBias());
        assertEquals("0.5", event.getMarketBiasScore());
        assertEquals("NORMAL", event.getRiskLevel());
        assertEquals(1, event.getSignals().size());
        assertEquals("BUY_PRESSURE", event.getSignals().getFirst().getType());
    }

    @Test
    void schemaVersionIs2() {
        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), CREATED_AT.plusMillis(2_000L), 2_000L));

        assertEquals(2, event.getMetadata().getSchemaVersion());
    }

    @Test
    void nullSnapshotThrowsAvroMappingException() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(null));
    }

    @Test
    void blankSignalSnapshotIdFails() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(" ", MarketSetup.none("test"), CREATED_AT.plusMillis(2_000L), 2_000L)));
    }

    @Test
    void nullValidUntilFails() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), null, 2_000L)));
    }

    @Test
    void nonPositiveTtlFails() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(MarketSetup.none("test"), CREATED_AT.plusMillis(2_000L), 0L)));
    }

    @Test
    void nullSetupFails() {
        assertThrows(AvroMappingException.class, () -> MarketSignalSnapshotAvroMapper.toAvro(
                snapshot(null, CREATED_AT.plusMillis(2_000L), 2_000L)));
    }

    private static MarketSignalSnapshot snapshot(MarketSetup setup, Instant validUntil, long ttlMs) {
        return snapshot("sig-1", setup, validUntil, ttlMs);
    }

    private static MarketSignalSnapshot snapshot(String signalSnapshotId, MarketSetup setup,
                                                 Instant validUntil, long ttlMs) {
        return new MarketSignalSnapshot(
                signalSnapshotId,
                "feat-1",
                "binance",
                "spot",
                "BTC",
                "USDT",
                "BTCUSDT",
                "binance:spot:BTCUSDT",
                EVENT_TIME,
                CREATED_AT,
                validUntil,
                ttlMs,
                "mfs-core-v1",
                "mse-signals-v5",
                MarketBias.BULLISH,
                new BigDecimal("0.5"),
                RiskLevel.NORMAL,
                setup,
                List.of(MarketSignal.bullish(
                        SignalType.BUY_PRESSURE,
                        SignalStrength.STRONG,
                        new BigDecimal("0.9"),
                        "buy pressure",
                        Map.of("window", "5s"))));
    }
}

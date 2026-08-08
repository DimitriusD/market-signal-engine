package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.signal.MarketSetupEvent;
import com.trading.contracts.signal.MarketSignalEvent;
import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MarketSignalSnapshotAvroMapper {

    private static final int SCHEMA_VERSION = 2;
    private static final String EVENT_TYPE = "MARKET_SIGNAL_SNAPSHOT";
    private static final String SOURCE_STREAM = "market-signal-engine";

    private MarketSignalSnapshotAvroMapper() {
    }

    public static MarketSignalSnapshotEvent toAvro(MarketSignalSnapshot snapshot) {
        if (snapshot == null) {
            throw new AvroMappingException("MarketSignalSnapshot must not be null");
        }
        validateRequired(snapshot);

        return MarketSignalSnapshotEvent.newBuilder()
                .setMetadata(buildMetadata(snapshot))
                .setSourceFeatureEventId(required(snapshot.sourceFeatureSnapshotId(), "sourceFeatureSnapshotId"))
                .setSourceFeatureSetVersion(required(snapshot.sourceFeatureSetVersion(), "sourceFeatureSetVersion"))
                .setEvaluatedTs(requiredEpochMillis(snapshot.createdAt(), "createdAt"))
                .setValidUntilTs(requiredEpochMillis(snapshot.validUntil(), "validUntil"))
                .setTtlMs(requiredPositiveTtl(snapshot.ttlMs()))
                .setSignalSetVersion(required(snapshot.signalSetVersion(), "signalSetVersion"))
                .setMarketBias(required(snapshot.marketBias(), "marketBias").name())
                .setMarketBiasScore(decimal(required(snapshot.marketBiasScore(), "marketBiasScore")))
                .setRiskLevel(required(snapshot.riskLevel(), "riskLevel").name())
                .setSetup(buildSetup(required(snapshot.setup(), "setup")))
                .setSignals(buildSignals(snapshot.signals()))
                .build();
    }

    /**
     * Fail-fast on required fields. The mapper must never coerce a required {@code null}/blank into
     * {@code ""} or {@code 0L} via {@link #nz(String)}/epoch fallbacks: that would publish a
     * structurally-broken signal event downstream instead of routing the offending record to the DLT.
     */
    private static void validateRequired(MarketSignalSnapshot snapshot) {
        required(snapshot.signalSnapshotId(), "signalSnapshotId");
        required(snapshot.sourceFeatureSnapshotId(), "sourceFeatureSnapshotId");
        required(snapshot.instrumentId(), "instrumentId");
        required(snapshot.createdAt(), "createdAt");
        required(snapshot.validUntil(), "validUntil");
        required(snapshot.sourceFeatureSetVersion(), "sourceFeatureSetVersion");
        required(snapshot.signalSetVersion(), "signalSetVersion");
        required(snapshot.marketBias(), "marketBias");
        required(snapshot.marketBiasScore(), "marketBiasScore");
        required(snapshot.riskLevel(), "riskLevel");
        required(snapshot.setup(), "setup");
    }

    private static MetadataEvent buildMetadata(MarketSignalSnapshot snapshot) {
        long exchangeTs = requiredEpochMillis(snapshot.eventTime(), "eventTime");
        long processedTs = requiredEpochMillis(snapshot.createdAt(), "createdAt");

        return MetadataEvent.newBuilder()
                .setSchemaVersion(SCHEMA_VERSION)
                .setEventType(EVENT_TYPE)
                .setExchange(nz(snapshot.exchange()))
                .setMarketType(nz(snapshot.marketType()))
                .setBase(nz(snapshot.base()))
                .setQuote(nz(snapshot.quote()))
                .setSymbol(nz(snapshot.symbol()))
                .setInstrumentId(required(snapshot.instrumentId(), "instrumentId"))
                .setEventId(required(snapshot.signalSnapshotId(), "signalSnapshotId"))
                .setSourceStream(SOURCE_STREAM)
                .setExchangeTs(exchangeTs)
                .setReceivedTs(processedTs)
                .setProcessedTs(processedTs)
                .build();
    }

    private static MarketSetupEvent buildSetup(MarketSetup setup) {
        if (setup == null) {
            return null;
        }

        return MarketSetupEvent.newBuilder()
                .setSide(setup.side().name())
                .setType(setup.type().name())
                .setStrength(setup.strength().name())
                .setConfidence(decimal(setup.confidence()))
                .setReason(setup.reason())
                .setAttributes(copyAttributes(setup.attributes()))
                .build();
    }

    private static List<MarketSignalEvent> buildSignals(List<MarketSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }

        List<MarketSignalEvent> result = new ArrayList<>(signals.size());
        for (MarketSignal signal : signals) {
            result.add(MarketSignalEvent.newBuilder()
                    .setType(signal.type().name())
                    .setDirection(signal.direction().name())
                    .setStrength(signal.strength().name())
                    .setConfidence(decimal(signal.confidence()))
                    .setReason(signal.reason())
                    .setAttributes(copyAttributes(signal.attributes()))
                    .build());
        }
        return result;
    }

    private static Map<String, String> copyAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(attributes);
    }

    private static String decimal(BigDecimal value) {
        return value != null ? value.toPlainString() : null;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AvroMappingException(fieldName + " must not be blank");
        }
        return value;
    }

    private static long requiredEpochMillis(Instant instant, String fieldName) {
        if (instant == null) {
            throw new AvroMappingException(fieldName + " must not be null");
        }
        return instant.toEpochMilli();
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new AvroMappingException(fieldName + " must not be null");
        }
        return value;
    }

    private static long requiredPositiveTtl(long ttlMs) {
        if (ttlMs <= 0) {
            throw new AvroMappingException("ttlMs must be positive");
        }
        return ttlMs;
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }
}

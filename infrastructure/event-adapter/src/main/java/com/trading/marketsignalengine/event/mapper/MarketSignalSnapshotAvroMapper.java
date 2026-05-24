package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.signal.MarketSignalEvent;
import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MarketSignalSnapshotAvroMapper {

    private static final int SCHEMA_VERSION = 1;
    private static final String EVENT_TYPE = "MARKET_SIGNAL_SNAPSHOT";
    private static final String SOURCE_STREAM = "market-signal-engine";

    private MarketSignalSnapshotAvroMapper() {
    }

    public static MarketSignalSnapshotEvent toAvro(MarketSignalSnapshot snapshot) {
        if (snapshot == null) {
            throw new AvroMappingException("MarketSignalSnapshot must not be null");
        }

        return MarketSignalSnapshotEvent.newBuilder()
                .setMetadata(buildMetadata(snapshot))
                .setSourceFeatureEventId(nz(snapshot.sourceFeatureSnapshotId()))
                .setSourceFeatureSetVersion(nz(snapshot.sourceFeatureSetVersion()))
                .setEvaluatedTs(toEpochMillis(snapshot.createdAt()))
                .setSignalSetVersion(nz(snapshot.signalSetVersion()))
                .setMarketBias(snapshot.marketBias().name())
                .setMarketBiasScore(decimal(snapshot.marketBiasScore()))
                .setRiskLevel(snapshot.riskLevel().name())
                .setSignals(buildSignals(snapshot.signals()))
                .build();
    }

    private static MetadataEvent buildMetadata(MarketSignalSnapshot snapshot) {
        long exchangeTs = toEpochMillis(snapshot.eventTime());
        long processedTs = toEpochMillis(snapshot.createdAt());

        return MetadataEvent.newBuilder()
                .setSchemaVersion(SCHEMA_VERSION)
                .setEventType(EVENT_TYPE)
                .setExchange(nz(snapshot.exchange()))
                .setMarketType(nz(snapshot.marketType()))
                .setBase("")
                .setQuote("")
                .setSymbol(nz(snapshot.symbol()))
                .setInstrumentId(nz(snapshot.instrumentId()))
                .setEventId(nz(snapshot.signalSnapshotId()))
                .setSourceStream(SOURCE_STREAM)
                .setExchangeTs(exchangeTs)
                .setReceivedTs(processedTs)
                .setProcessedTs(processedTs)
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

    private static long toEpochMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : 0L;
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }
}

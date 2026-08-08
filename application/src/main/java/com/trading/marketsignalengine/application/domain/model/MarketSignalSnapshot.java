package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketSignalSnapshot(
        String signalSnapshotId,
        String sourceFeatureSnapshotId,
        String exchange,
        String marketType,
        String base,
        String quote,
        String symbol,
        String instrumentId,
        Instant eventTime,
        // Semantically the instant the signal was evaluated (evaluatedAt). Kept named createdAt for
        // now so the Avro mapper and Kafka adapter stay untouched; renamed in a later stage.
        Instant createdAt,
        Instant validUntil,
        long ttlMs,
        String sourceFeatureSetVersion,
        String signalSetVersion,
        MarketBias marketBias,
        BigDecimal marketBiasScore,
        RiskLevel riskLevel,
        MarketSetup setup,
        List<MarketSignal> signals) {
}

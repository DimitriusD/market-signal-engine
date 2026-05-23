package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketSignalSnapshot(
        String signalSnapshotId,
        String sourceFeatureSnapshotId,
        String exchange,
        String marketType,
        String symbol,
        String instrumentId,
        Instant eventTime,
        Instant createdAt,
        String sourceFeatureSetVersion,
        String signalSetVersion,
        MarketBias marketBias,
        BigDecimal marketBiasScore,
        RiskLevel riskLevel,
        List<MarketSignal> signals) {
}

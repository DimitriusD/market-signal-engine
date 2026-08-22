package com.trading.marketsignalengine.application.domain.model.feature;

import java.time.Instant;

import lombok.Builder;

/**
 * Domain view of one MFS v2 {@code MarketFeaturesSnapshotEvent}. Identity and timing come from the
 * event metadata; {@code evaluationTs} is the upstream evaluation tick (null when the writer
 * predates the field), {@code triggerSource} says what caused the publish (market event, clock tick,
 * quality transition), {@code configHash} pins the upstream feature configuration for lineage.
 */
@Builder(toBuilder = true)
public record MarketFeaturesSnapshot(
        String snapshotId,
        String exchange,
        String marketType,
        String base,
        String quote,
        String symbol,
        String instrumentId,
        Instant eventTime,
        Instant receivedAt,
        Instant computedAt,
        Instant evaluationTs,
        String featureSetVersion,
        String triggerSource,
        String configHash,
        FeatureQuality quality,
        FeatureSourceState sourceState,
        FeatureDiagnostics diagnostics,
        BboFeature bbo,
        BookFeature book,
        TradeFlowFeature tradeFlow,
        RegimeFeature regime) {
}

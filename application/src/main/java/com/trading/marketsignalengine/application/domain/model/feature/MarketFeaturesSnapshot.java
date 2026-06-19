package com.trading.marketsignalengine.application.domain.model.feature;

import java.time.Instant;

import lombok.Builder;

@Builder
public record MarketFeaturesSnapshot(
        String snapshotId,
        String exchange,
        String marketType,
        String symbol,
        String instrumentId,
        Instant eventTime,
        Instant receivedAt,
        Instant computedAt,
        String featureSetVersion,
        BboFeature bbo,
        BookFeature book,
        TradeFlowFeature tradeFlow,
        RegimeFeature regime,
        FeatureQuality quality) {
}

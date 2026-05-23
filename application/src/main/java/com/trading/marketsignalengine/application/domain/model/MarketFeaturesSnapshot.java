package com.trading.marketsignalengine.application.domain.model;

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
        BboFeatureView bbo,
        BookFeatureView book,
        TradeFlowFeatureView tradeFlow,
        RegimeFeatureView regime,
        FeatureQuality quality) {
}

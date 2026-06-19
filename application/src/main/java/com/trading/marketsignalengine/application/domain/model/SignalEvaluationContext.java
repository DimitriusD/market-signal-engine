package com.trading.marketsignalengine.application.domain.model;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

import java.time.Instant;

public record SignalEvaluationContext(MarketFeaturesSnapshot features,
                                      SignalConfiguration configuration,
                                      Instant evaluatedAt) {
}

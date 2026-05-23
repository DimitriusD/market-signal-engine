package com.trading.marketsignalengine.application.domain.model;

import java.time.Instant;

public record SignalEvaluationContext(
        MarketFeaturesSnapshot features,
        SignalConfiguration configuration,
        Instant evaluatedAt) {
}

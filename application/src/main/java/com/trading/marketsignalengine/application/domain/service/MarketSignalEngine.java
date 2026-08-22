package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import java.time.Instant;

public interface MarketSignalEngine {

    /**
     * Evaluates a feature snapshot at the engine's current clock time (live path).
     */
    MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features);

    /**
     * Evaluates a feature snapshot at an explicit evaluation instant. The engine is stateless and
     * deterministic, so the same {@code features}, configuration and {@code evaluatedAt} always yield
     * the same snapshot. This is the replay entry point: it lets a replay harness pin evaluation time
     * to a value derived from the recorded input instead of the wall clock.
     */
    MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant evaluatedAt);
}

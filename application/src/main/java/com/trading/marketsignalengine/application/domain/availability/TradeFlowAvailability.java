package com.trading.marketsignalengine.application.domain.availability;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-horizon availability of the trade-flow feature group for one snapshot: exactly one verdict per
 * {@link MarketHorizon}. Immutable.
 */
public final class TradeFlowAvailability {

    private final Map<MarketHorizon, FeatureWindowAvailability> byHorizon;

    public TradeFlowAvailability(Map<MarketHorizon, FeatureWindowAvailability> byHorizon) {
        Objects.requireNonNull(byHorizon, "byHorizon");
        EnumMap<MarketHorizon, FeatureWindowAvailability> copy = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            FeatureWindowAvailability availability = byHorizon.get(horizon);
            if (availability == null) {
                throw new IllegalArgumentException("missing availability for horizon " + horizon);
            }
            if (availability.horizon() != horizon) {
                throw new IllegalArgumentException(
                        "availability for " + horizon + " carries horizon " + availability.horizon());
            }
            copy.put(horizon, availability);
        }
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public FeatureWindowAvailability of(MarketHorizon horizon) {
        return byHorizon.get(Objects.requireNonNull(horizon, "horizon"));
    }

    public FeatureAvailabilityStatus statusOf(MarketHorizon horizon) {
        return of(horizon).status();
    }

    public boolean isAvailable(MarketHorizon horizon) {
        return of(horizon).isAvailable();
    }

    /** Unmodifiable view in canonical horizon order (1S, 5S, 15S, 60S). */
    public Map<MarketHorizon, FeatureWindowAvailability> asMap() {
        return byHorizon;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TradeFlowAvailability other && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "TradeFlowAvailability" + byHorizon;
    }
}

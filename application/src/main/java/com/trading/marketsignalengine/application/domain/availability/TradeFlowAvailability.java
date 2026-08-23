package com.trading.marketsignalengine.application.domain.availability;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-horizon availability of the trade-flow feature group for one snapshot: exactly one verdict per
 * {@link FeatureWindowHorizon}. Immutable.
 */
public final class TradeFlowAvailability {

    private final Map<FeatureWindowHorizon, FeatureWindowAvailability> byHorizon;

    public TradeFlowAvailability(Map<FeatureWindowHorizon, FeatureWindowAvailability> byHorizon) {
        Objects.requireNonNull(byHorizon, "byHorizon");
        EnumMap<FeatureWindowHorizon, FeatureWindowAvailability> copy = new EnumMap<>(FeatureWindowHorizon.class);
        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
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

    public FeatureWindowAvailability of(FeatureWindowHorizon horizon) {
        return byHorizon.get(Objects.requireNonNull(horizon, "horizon"));
    }

    public FeatureAvailabilityStatus statusOf(FeatureWindowHorizon horizon) {
        return of(horizon).status();
    }

    public boolean isAvailable(FeatureWindowHorizon horizon) {
        return of(horizon).isAvailable();
    }

    /** Unmodifiable view in horizon order (1S, 5S, 15S, 60S). */
    public Map<FeatureWindowHorizon, FeatureWindowAvailability> asMap() {
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

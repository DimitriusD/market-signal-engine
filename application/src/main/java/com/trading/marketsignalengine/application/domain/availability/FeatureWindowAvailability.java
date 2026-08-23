package com.trading.marketsignalengine.application.domain.availability;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.List;
import java.util.Objects;

/**
 * Availability verdict for one feature group at one horizon, with stable machine-readable reason
 * codes explaining it. Immutable value.
 */
public record FeatureWindowAvailability(
        MarketHorizon horizon,
        FeatureAvailabilityStatus status,
        List<String> reasonCodes) {

    public FeatureWindowAvailability {
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(status, "status");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public static FeatureWindowAvailability of(MarketHorizon horizon, FeatureAvailabilityStatus status,
                                               String... reasonCodes) {
        return new FeatureWindowAvailability(horizon, status, List.of(reasonCodes));
    }

    public boolean isAvailable() {
        return status.isAvailable();
    }
}

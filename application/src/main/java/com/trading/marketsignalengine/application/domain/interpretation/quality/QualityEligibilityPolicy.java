package com.trading.marketsignalengine.application.domain.interpretation.quality;

import java.time.Duration;
import java.util.Objects;

/**
 * Safety policy of the quality layer: how old a feature snapshot may be (market as-of age), how long
 * the producer → engine path may take (processing latency) and whether an upstream-reported future
 * event blocks the snapshot outright. Immutable, explicit, no defaults: production values and their
 * Spring properties are wired in a later stage, the resolvers only ever see a policy instance.
 *
 * <p>Boundaries are inclusive on the accepting side: {@code age <= maxFeatureAge} is acceptable,
 * {@code age > maxFeatureAge} is stale (same for latency). These are safety thresholds, not trading
 * weights or confidence parameters.
 */
public record QualityEligibilityPolicy(
        Duration maxFeatureAge,
        Duration maxProcessingLatency,
        boolean blockFutureEvents) {

    public QualityEligibilityPolicy {
        requirePositive(maxFeatureAge, "maxFeatureAge");
        requirePositive(maxProcessingLatency, "maxProcessingLatency");
    }

    public static QualityEligibilityPolicy of(Duration maxFeatureAge, Duration maxProcessingLatency,
                                              boolean blockFutureEvents) {
        return new QualityEligibilityPolicy(maxFeatureAge, maxProcessingLatency, blockFutureEvents);
    }

    public long maxFeatureAgeMs() {
        return maxFeatureAge.toMillis();
    }

    public long maxProcessingLatencyMs() {
        return maxProcessingLatency.toMillis();
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive, got " + value);
        }
    }
}

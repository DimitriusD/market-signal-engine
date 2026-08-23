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
 *
 * <p>All timing arithmetic runs in epoch milliseconds, so each duration must be non-null, at least
 * {@code 1 ms} and of whole-millisecond precision: a sub-millisecond ({@code PT0.000000001S}) or
 * fractional-millisecond ({@code PT0.0015S}) duration is rejected instead of silently truncating to a
 * different (possibly zero) threshold, and a duration whose millisecond value overflows {@code long}
 * is rejected as well.
 */
public record QualityEligibilityPolicy(
        Duration maxFeatureAge,
        Duration maxProcessingLatency,
        boolean blockFutureEvents) {

    private static final int NANOS_PER_MILLI = 1_000_000;

    public QualityEligibilityPolicy {
        requireWholeMillisThreshold(maxFeatureAge, "maxFeatureAge");
        requireWholeMillisThreshold(maxProcessingLatency, "maxProcessingLatency");
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

    /** Non-null, positive, whole milliseconds, at least 1 ms, representable as a {@code long} of millis. */
    private static void requireWholeMillisThreshold(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive, got " + value);
        }
        if (value.getNano() % NANOS_PER_MILLI != 0) {
            throw new IllegalArgumentException(field + " must have whole-millisecond precision (timing arithmetic is"
                    + " in epoch milliseconds; sub-millisecond precision would be lost), got " + value);
        }
        long millis;
        try {
            millis = value.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(field + " is too large to express in milliseconds, got " + value,
                    overflow);
        }
        if (millis <= 0L) {
            throw new IllegalArgumentException(field + " must be at least 1 ms, got " + value);
        }
    }
}

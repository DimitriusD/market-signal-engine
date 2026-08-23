package com.trading.marketsignalengine.application.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The one canonical horizon type of the signal engine: the rolling-window lengths MFS v2 publishes
 * for windowed features and the interpretation horizons of the V2 market-interpretation model. The
 * same value is used on the input side ({@code FeatureAvailabilityResolver}: "is the 5S trade-flow
 * window there") and on the interpretation side ({@code HorizonAssessment}: "what does the 5S horizon
 * say"), so availability and interpretation can never disagree about what "5S" means.
 *
 * <p>The canonical order is always {@code 1S → 5S → 15S → 60S} (= declaration order, = ascending
 * duration); {@link #canonicalOrder()} is the single source of that order for every per-horizon
 * collection. {@link #wireValue()} is the contract identifier ({@code "1S"}, ...) used by the Avro
 * contracts; it is a transport label carried here so mappers do not invent their own.
 *
 * <p>This enum deliberately knows nothing about trade-flow counters, window nullability or any other
 * feature-group-specific wire detail — those stay in the resolver of the feature group concerned.
 */
public enum MarketHorizon {
    H1S("1S", Duration.ofSeconds(1)),
    H5S("5S", Duration.ofSeconds(5)),
    H15S("15S", Duration.ofSeconds(15)),
    H60S("60S", Duration.ofSeconds(60));

    private static final List<MarketHorizon> CANONICAL_ORDER = List.of(values());

    private final String wireValue;
    private final Duration duration;

    MarketHorizon(String wireValue, Duration duration) {
        this.wireValue = wireValue;
        this.duration = duration;
    }

    /** Contract identifier of the horizon on the wire ({@code 1S}, {@code 5S}, {@code 15S}, {@code 60S}). */
    public String wireValue() {
        return wireValue;
    }

    /** Rolling-window / interpretation length of the horizon. */
    public Duration duration() {
        return duration;
    }

    /** {@link #duration()} in milliseconds, for arithmetic on epoch-millis timestamps. */
    public long durationMs() {
        return duration.toMillis();
    }

    /** All horizons in canonical order {@code 1S, 5S, 15S, 60S}; unmodifiable. */
    public static List<MarketHorizon> canonicalOrder() {
        return CANONICAL_ORDER;
    }

    /**
     * Resolves a contract identifier; {@code IllegalArgumentException} for an unknown one (fail
     * closed — an unrecognised horizon is a contract error, never silently the nearest one).
     */
    public static MarketHorizon fromWireValue(String wireValue) {
        Objects.requireNonNull(wireValue, "wireValue");
        for (MarketHorizon horizon : CANONICAL_ORDER) {
            if (horizon.wireValue.equals(wireValue)) {
                return horizon;
            }
        }
        throw new IllegalArgumentException("unknown market horizon '" + wireValue + "'");
    }
}

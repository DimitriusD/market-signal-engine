package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The typed result of {@link VolatilityAssessmentEvaluator}: exactly one {@link VolatilityAssessment}
 * per {@link MarketHorizon}, always in canonical order ({@code 1S, 5S, 15S, 60S}). A missing horizon
 * or a map entry beyond the four canonical keys (e.g. a {@code null} key) fails fast — nothing is
 * silently dropped; a duplicate cannot exist (the only ways to build one are a per-horizon map or the
 * four-argument factory); the {@code VOLATILITY} dimension and the level ↔ availability coupling are
 * enforced by {@link VolatilityAssessment} itself. Lookups never return {@code null}. Immutable;
 * value equality, so two evaluations of the same input and policy compare equal.
 */
public final class VolatilityAssessments {

    private final Map<MarketHorizon, VolatilityAssessment> byHorizon;

    public VolatilityAssessments(Map<MarketHorizon, VolatilityAssessment> byHorizon) {
        requireNonNull(byHorizon, "volatility assessments");
        EnumMap<MarketHorizon, VolatilityAssessment> copy = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            VolatilityAssessment assessment = byHorizon.get(horizon);
            require(assessment != null, "missing VOLATILITY assessment for horizon " + horizon);
            copy.put(horizon, assessment);
        }
        // fail fast instead of silently dropping anything beyond the four canonical keys (e.g. a null key)
        require(byHorizon.size() == copy.size(),
                "volatility assessments must contain exactly the four canonical horizons, got keys " + byHorizon.keySet());
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static VolatilityAssessments of(VolatilityAssessment h1s, VolatilityAssessment h5s,
                                           VolatilityAssessment h15s, VolatilityAssessment h60s) {
        EnumMap<MarketHorizon, VolatilityAssessment> map = new EnumMap<>(MarketHorizon.class);
        map.put(MarketHorizon.H1S, requireNonNull(h1s, "1S volatility assessment"));
        map.put(MarketHorizon.H5S, requireNonNull(h5s, "5S volatility assessment"));
        map.put(MarketHorizon.H15S, requireNonNull(h15s, "15S volatility assessment"));
        map.put(MarketHorizon.H60S, requireNonNull(h60s, "60S volatility assessment"));
        return new VolatilityAssessments(map);
    }

    /** The VOLATILITY assessment of {@code horizon}; never {@code null}. */
    public VolatilityAssessment of(MarketHorizon horizon) {
        return byHorizon.get(requireNonNull(horizon, "horizon"));
    }

    /** Unmodifiable view in canonical horizon order. */
    public Map<MarketHorizon, VolatilityAssessment> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical horizon order ({@code 1S, 5S, 15S, 60S}). */
    public List<VolatilityAssessment> asList() {
        return List.copyOf(byHorizon.values());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof VolatilityAssessments other && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "VolatilityAssessments" + byHorizon;
    }
}

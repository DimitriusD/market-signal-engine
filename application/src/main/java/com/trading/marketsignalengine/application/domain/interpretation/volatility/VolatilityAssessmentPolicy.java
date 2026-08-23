package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned, immutable Volatility V1 policy: exactly one {@link VolatilityHorizonPolicy} per
 * {@link MarketHorizon} ({@code 1S, 5S, 15S, 60S} — MFS v2 publishes realized volatility for all
 * four), kept in canonical order. A missing or duplicate horizon fails fast; {@code policyVersion} is
 * mandatory lineage (non-blank, not a placeholder) because a volatility classification is only
 * reproducible together with the bounds that produced it.
 *
 * <p>No defaults and no production values live here or in the evaluator: every bound is an explicit,
 * horizon-specific input; calibration is replay-driven and comes later.
 */
public final class VolatilityAssessmentPolicy {

    private final String policyVersion;
    private final Map<MarketHorizon, VolatilityHorizonPolicy> byHorizon;

    public VolatilityAssessmentPolicy(String policyVersion, Collection<VolatilityHorizonPolicy> horizonPolicies) {
        this.policyVersion = requireNotPlaceholder(policyVersion, "volatility policyVersion");
        requireNonNull(horizonPolicies, "volatility horizonPolicies");
        EnumMap<MarketHorizon, VolatilityHorizonPolicy> copy = new EnumMap<>(MarketHorizon.class);
        for (VolatilityHorizonPolicy policy : horizonPolicies) {
            requireNonNull(policy, "volatility horizonPolicies element");
            require(copy.putIfAbsent(policy.horizon(), policy) == null,
                    "volatility policy contains duplicate horizon " + policy.horizon());
        }
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            require(copy.containsKey(horizon), "volatility policy is missing horizon " + horizon);
        }
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static VolatilityAssessmentPolicy of(String policyVersion,
                                                VolatilityHorizonPolicy h1s, VolatilityHorizonPolicy h5s,
                                                VolatilityHorizonPolicy h15s, VolatilityHorizonPolicy h60s) {
        return new VolatilityAssessmentPolicy(policyVersion, List.of(
                requireNonNull(h1s, "1S volatility policy"),
                requireNonNull(h5s, "5S volatility policy"),
                requireNonNull(h15s, "15S volatility policy"),
                requireNonNull(h60s, "60S volatility policy")));
    }

    public String policyVersion() {
        return policyVersion;
    }

    /** The policy of {@code horizon}; never {@code null}. */
    public VolatilityHorizonPolicy of(MarketHorizon horizon) {
        return byHorizon.get(requireNonNull(horizon, "horizon"));
    }

    /** Unmodifiable view in canonical horizon order. */
    public Map<MarketHorizon, VolatilityHorizonPolicy> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical horizon order ({@code 1S, 5S, 15S, 60S}). */
    public List<VolatilityHorizonPolicy> asList() {
        return List.copyOf(byHorizon.values());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof VolatilityAssessmentPolicy other
                && policyVersion.equals(other.policyVersion)
                && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return 31 * policyVersion.hashCode() + byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "VolatilityAssessmentPolicy[" + policyVersion + "]" + byHorizon;
    }
}

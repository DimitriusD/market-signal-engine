package com.trading.marketsignalengine.application.domain.interpretation.flow;

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
 * Versioned, immutable Flow V1 policy: exactly one {@link FlowHorizonPolicy} per {@link MarketHorizon},
 * kept in canonical order ({@code 1S, 5S, 15S, 60S}). A missing or duplicate horizon fails fast;
 * {@code policyVersion} is mandatory lineage (non-blank, not a placeholder) because a flow verdict is
 * only reproducible together with the parameters that produced it.
 *
 * <p>No defaults and no production values live here or in the evaluator: every threshold is an
 * explicit, horizon-specific input (a single V1 threshold copied to every horizon would be a hidden
 * production decision; calibration is replay-driven and comes later).
 */
public final class FlowAssessmentPolicy {

    private final String policyVersion;
    private final Map<MarketHorizon, FlowHorizonPolicy> byHorizon;

    public FlowAssessmentPolicy(String policyVersion, Collection<FlowHorizonPolicy> horizonPolicies) {
        this.policyVersion = requireNotPlaceholder(policyVersion, "flow policyVersion");
        requireNonNull(horizonPolicies, "flow horizonPolicies");
        EnumMap<MarketHorizon, FlowHorizonPolicy> copy = new EnumMap<>(MarketHorizon.class);
        for (FlowHorizonPolicy policy : horizonPolicies) {
            requireNonNull(policy, "flow horizonPolicies element");
            require(copy.putIfAbsent(policy.horizon(), policy) == null,
                    "flow policy contains duplicate horizon " + policy.horizon());
        }
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            require(copy.containsKey(horizon), "flow policy is missing horizon " + horizon);
        }
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static FlowAssessmentPolicy of(String policyVersion,
                                          FlowHorizonPolicy h1s, FlowHorizonPolicy h5s,
                                          FlowHorizonPolicy h15s, FlowHorizonPolicy h60s) {
        return new FlowAssessmentPolicy(policyVersion, List.of(
                requireNonNull(h1s, "1S flow policy"),
                requireNonNull(h5s, "5S flow policy"),
                requireNonNull(h15s, "15S flow policy"),
                requireNonNull(h60s, "60S flow policy")));
    }

    public String policyVersion() {
        return policyVersion;
    }

    /** The policy of {@code horizon}; never {@code null}. */
    public FlowHorizonPolicy of(MarketHorizon horizon) {
        return byHorizon.get(requireNonNull(horizon, "horizon"));
    }

    /** Unmodifiable view in canonical horizon order. */
    public Map<MarketHorizon, FlowHorizonPolicy> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical horizon order ({@code 1S, 5S, 15S, 60S}). */
    public List<FlowHorizonPolicy> asList() {
        return List.copyOf(byHorizon.values());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof FlowAssessmentPolicy other
                && policyVersion.equals(other.policyVersion)
                && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return 31 * policyVersion.hashCode() + byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "FlowAssessmentPolicy[" + policyVersion + "]" + byHorizon;
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.momentum;

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
 * Versioned, immutable Momentum V1 policy: exactly one {@link MomentumHorizonPolicy} per <b>scoped</b>
 * {@link MarketHorizon} ({@code 5S, 15S, 60S}), kept in canonical order. 1S is deliberately absent —
 * MFS v2 publishes no {@code priceChangeBps1s}, so a 1S policy would be a fiction (and
 * {@link MomentumHorizonPolicy} rejects one). A missing or duplicate scoped horizon fails fast;
 * {@code policyVersion} is mandatory lineage (non-blank, not a placeholder) because a momentum verdict
 * is only reproducible together with the parameters that produced it.
 *
 * <p>No defaults and no production values live here or in the evaluator: every threshold is an
 * explicit, horizon-specific input; calibration is replay-driven and comes later.
 */
public final class MomentumAssessmentPolicy {

    /** The horizons momentum evidence is scoped to, in canonical order ({@code 5S, 15S, 60S}). */
    public static final List<MarketHorizon> SCOPED_HORIZONS =
            List.of(MarketHorizon.H5S, MarketHorizon.H15S, MarketHorizon.H60S);

    private final String policyVersion;
    private final Map<MarketHorizon, MomentumHorizonPolicy> byHorizon;

    public MomentumAssessmentPolicy(String policyVersion, Collection<MomentumHorizonPolicy> horizonPolicies) {
        this.policyVersion = requireNotPlaceholder(policyVersion, "momentum policyVersion");
        requireNonNull(horizonPolicies, "momentum horizonPolicies");
        EnumMap<MarketHorizon, MomentumHorizonPolicy> copy = new EnumMap<>(MarketHorizon.class);
        for (MomentumHorizonPolicy policy : horizonPolicies) {
            requireNonNull(policy, "momentum horizonPolicies element");
            require(copy.putIfAbsent(policy.horizon(), policy) == null,
                    "momentum policy contains duplicate horizon " + policy.horizon());
        }
        for (MarketHorizon horizon : SCOPED_HORIZONS) {
            require(copy.containsKey(horizon), "momentum policy is missing horizon " + horizon);
        }
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static MomentumAssessmentPolicy of(String policyVersion,
                                              MomentumHorizonPolicy h5s,
                                              MomentumHorizonPolicy h15s,
                                              MomentumHorizonPolicy h60s) {
        return new MomentumAssessmentPolicy(policyVersion, List.of(
                requireNonNull(h5s, "5S momentum policy"),
                requireNonNull(h15s, "15S momentum policy"),
                requireNonNull(h60s, "60S momentum policy")));
    }

    public String policyVersion() {
        return policyVersion;
    }

    /** The policy of a scoped {@code horizon}; asking for 1S fails fast (momentum has no 1S policy). */
    public MomentumHorizonPolicy of(MarketHorizon horizon) {
        requireNonNull(horizon, "horizon");
        MomentumHorizonPolicy policy = byHorizon.get(horizon);
        require(policy != null, "momentum has no policy for horizon " + horizon + " (not scoped)");
        return policy;
    }

    /** Unmodifiable view in canonical scoped-horizon order ({@code 5S, 15S, 60S}). */
    public Map<MarketHorizon, MomentumHorizonPolicy> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical scoped-horizon order ({@code 5S, 15S, 60S}). */
    public List<MomentumHorizonPolicy> asList() {
        return List.copyOf(byHorizon.values());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MomentumAssessmentPolicy other
                && policyVersion.equals(other.policyVersion)
                && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return 31 * policyVersion.hashCode() + byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "MomentumAssessmentPolicy[" + policyVersion + "]" + byHorizon;
    }
}

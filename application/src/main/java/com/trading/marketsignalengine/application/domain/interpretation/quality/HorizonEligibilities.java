package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exactly one {@link HorizonEligibility} per {@link MarketHorizon}, always in canonical order
 * ({@code 1S, 5S, 15S, 60S}). A missing horizon fails fast; a duplicate cannot exist (the only ways
 * to build one are a per-horizon map or the four-argument factory). Lookups never return {@code null}.
 * Immutable.
 */
public final class HorizonEligibilities {

    private final Map<MarketHorizon, HorizonEligibility> byHorizon;

    public HorizonEligibilities(Map<MarketHorizon, HorizonEligibility> byHorizon) {
        Objects.requireNonNull(byHorizon, "byHorizon");
        EnumMap<MarketHorizon, HorizonEligibility> copy = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonEligibility eligibility = byHorizon.get(horizon);
            if (eligibility == null) {
                throw new IllegalArgumentException("missing eligibility for horizon " + horizon);
            }
            copy.put(horizon, eligibility);
        }
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static HorizonEligibilities of(HorizonEligibility h1s, HorizonEligibility h5s,
                                          HorizonEligibility h15s, HorizonEligibility h60s) {
        EnumMap<MarketHorizon, HorizonEligibility> map = new EnumMap<>(MarketHorizon.class);
        map.put(MarketHorizon.H1S, Objects.requireNonNull(h1s, "1S eligibility"));
        map.put(MarketHorizon.H5S, Objects.requireNonNull(h5s, "5S eligibility"));
        map.put(MarketHorizon.H15S, Objects.requireNonNull(h15s, "15S eligibility"));
        map.put(MarketHorizon.H60S, Objects.requireNonNull(h60s, "60S eligibility"));
        return new HorizonEligibilities(map);
    }

    /** The same eligibility on every horizon (e.g. every horizon UNAVAILABLE on {@code NO_DATA}). */
    public static HorizonEligibilities uniform(HorizonEligibility eligibility) {
        Objects.requireNonNull(eligibility, "eligibility");
        return of(eligibility, eligibility, eligibility, eligibility);
    }

    public HorizonEligibility of(MarketHorizon horizon) {
        return byHorizon.get(Objects.requireNonNull(horizon, "horizon"));
    }

    public HorizonEligibilityStatus statusOf(MarketHorizon horizon) {
        return of(horizon).status();
    }

    public boolean isEligible(MarketHorizon horizon) {
        return of(horizon).isEligible();
    }

    /** Unmodifiable view in canonical horizon order. */
    public Map<MarketHorizon, HorizonEligibility> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical horizon order ({@code 1S, 5S, 15S, 60S}). */
    public List<HorizonEligibility> asList() {
        return List.copyOf(byHorizon.values());
    }

    /** ELIGIBLE horizons in canonical order; empty when none. */
    public List<MarketHorizon> eligibleHorizons() {
        List<MarketHorizon> eligible = new ArrayList<>(MarketHorizon.canonicalOrder().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            if (byHorizon.get(horizon).isEligible()) {
                eligible.add(horizon);
            }
        }
        return List.copyOf(eligible);
    }

    public boolean allEligible() {
        return eligibleHorizons().size() == MarketHorizon.canonicalOrder().size();
    }

    public boolean anyEligible() {
        return !eligibleHorizons().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof HorizonEligibilities other && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "HorizonEligibilities" + byHorizon;
    }
}

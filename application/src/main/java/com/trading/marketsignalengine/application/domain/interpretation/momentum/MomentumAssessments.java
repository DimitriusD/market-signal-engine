package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The typed result of {@link MomentumAssessmentEvaluator}: exactly one {@code MOMENTUM}
 * {@link EvidenceAssessment} per {@link MarketHorizon}, always in canonical order
 * ({@code 1S, 5S, 15S, 60S}) — the 1S entry exists and is explicitly UNAVAILABLE (momentum is not
 * scoped to 1S), never silently dropped. A missing horizon, a non-MOMENTUM dimension or a map entry
 * beyond the four canonical keys (e.g. a {@code null} key) fails fast; a duplicate cannot exist (the
 * only ways to build one are a per-horizon map or the four-argument factory). Lookups never return
 * {@code null}. Immutable; value equality, so two evaluations of the same input and policy compare
 * equal.
 */
public final class MomentumAssessments {

    private final Map<MarketHorizon, EvidenceAssessment> byHorizon;

    public MomentumAssessments(Map<MarketHorizon, EvidenceAssessment> byHorizon) {
        requireNonNull(byHorizon, "momentum assessments");
        EnumMap<MarketHorizon, EvidenceAssessment> copy = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = byHorizon.get(horizon);
            require(evidence != null, "missing MOMENTUM evidence for horizon " + horizon);
            require(evidence.dimension() == EvidenceDimension.MOMENTUM,
                    horizon.wireValue() + " momentum assessment must have dimension MOMENTUM, got " + evidence.dimension());
            copy.put(horizon, evidence);
        }
        // fail fast instead of silently dropping anything beyond the four canonical keys (e.g. a null key)
        require(byHorizon.size() == copy.size(),
                "momentum assessments must contain exactly the four canonical horizons, got keys " + byHorizon.keySet());
        this.byHorizon = Collections.unmodifiableMap(copy);
    }

    public static MomentumAssessments of(EvidenceAssessment h1s, EvidenceAssessment h5s,
                                         EvidenceAssessment h15s, EvidenceAssessment h60s) {
        EnumMap<MarketHorizon, EvidenceAssessment> map = new EnumMap<>(MarketHorizon.class);
        map.put(MarketHorizon.H1S, requireNonNull(h1s, "1S momentum evidence"));
        map.put(MarketHorizon.H5S, requireNonNull(h5s, "5S momentum evidence"));
        map.put(MarketHorizon.H15S, requireNonNull(h15s, "15S momentum evidence"));
        map.put(MarketHorizon.H60S, requireNonNull(h60s, "60S momentum evidence"));
        return new MomentumAssessments(map);
    }

    /** The MOMENTUM evidence of {@code horizon}; never {@code null}. */
    public EvidenceAssessment of(MarketHorizon horizon) {
        return byHorizon.get(requireNonNull(horizon, "horizon"));
    }

    /** Unmodifiable view in canonical horizon order. */
    public Map<MarketHorizon, EvidenceAssessment> asMap() {
        return byHorizon;
    }

    /** Unmodifiable list in canonical horizon order ({@code 1S, 5S, 15S, 60S}). */
    public List<EvidenceAssessment> asList() {
        return List.copyOf(byHorizon.values());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MomentumAssessments other && byHorizon.equals(other.byHorizon));
    }

    @Override
    public int hashCode() {
        return byHorizon.hashCode();
    }

    @Override
    public String toString() {
        return "MomentumAssessments" + byHorizon;
    }
}

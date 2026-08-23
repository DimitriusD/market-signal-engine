package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Typed identifier of an MFS feature group as reported in {@code diagnostics.failedFeatureGroups}.
 * The known ids ({@link #BBO}, {@link #ORDER_BOOK}, {@link #TRADE_FLOW}, {@link #SHORT_TERM_REGIME})
 * drive the dependency policy of the quality layer; an unknown id (a future producer group) is kept
 * verbatim so diagnostics are never lost, it just drives no policy. Not an Avro enum on purpose.
 * Non-null, non-blank, value equality; the raw id is preserved exactly (no trimming / case folding:
 * normalising would hide a producer contract drift).
 */
public record FeatureGroupId(String value) implements Comparable<FeatureGroupId> {

    public static final FeatureGroupId BBO = new FeatureGroupId("bbo");
    public static final FeatureGroupId ORDER_BOOK = new FeatureGroupId("order-book");
    public static final FeatureGroupId TRADE_FLOW = new FeatureGroupId("trade-flow");
    public static final FeatureGroupId SHORT_TERM_REGIME = new FeatureGroupId("short-term-regime");

    /** The ids MFS v2 reports today, in producer order. */
    public static final List<FeatureGroupId> KNOWN = List.of(BBO, ORDER_BOOK, TRADE_FLOW, SHORT_TERM_REGIME);

    public FeatureGroupId {
        Objects.requireNonNull(value, "feature group id");
        if (value.isBlank()) {
            throw new IllegalArgumentException("feature group id must not be blank");
        }
    }

    public static FeatureGroupId of(String value) {
        return new FeatureGroupId(value);
    }

    /** Whether this is one of the ids the quality layer has a dependency rule for. */
    public boolean isKnown() {
        return KNOWN.contains(this);
    }

    /**
     * Immutable, duplicate-free, order-preserving list of the groups the producer reported as failed;
     * {@code null} diagnostics → empty. A repeated id on the wire is collapsed (a group failed once);
     * a blank id is rejected (contract corruption the validator already refuses).
     */
    public static List<FeatureGroupId> failedGroupsOf(FeatureDiagnostics diagnostics) {
        if (diagnostics == null) {
            return List.of();
        }
        return distinct(diagnostics.failedFeatureGroups().stream().map(FeatureGroupId::of).toList());
    }

    /** Immutable copy without nulls or duplicates, insertion order preserved; {@code null} → empty. */
    public static List<FeatureGroupId> distinct(Collection<FeatureGroupId> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<FeatureGroupId> seen = new LinkedHashSet<>();
        for (FeatureGroupId id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("feature group ids must not contain null");
            }
            seen.add(id);
        }
        return List.copyOf(seen);
    }

    @Override
    public int compareTo(FeatureGroupId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.trading.marketsignalengine.application.domain.interpretation;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Package-private invariant helpers shared by the V2 value objects: defensive, immutable,
 * null-element-free, duplicate-free collections with a canonical order where one exists.
 */
final class Invariants {

    private Invariants() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static Instant requirePositiveInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (value.toEpochMilli() <= 0L) {
            throw new IllegalArgumentException(field + " must be a positive epoch timestamp, got " + value);
        }
        return value;
    }

    /**
     * Immutable copy of a reason-code collection: {@code null} collection → empty; null elements and
     * duplicates are rejected (a repeated reason is a producer bug, not a stronger reason); insertion
     * order is preserved because it is the producer's explanation order.
     */
    static List<ReasonCode> reasonCodes(Collection<ReasonCode> codes, String field) {
        if (codes == null) {
            return List.of();
        }
        Set<ReasonCode> seen = new LinkedHashSet<>();
        for (ReasonCode code : codes) {
            if (code == null) {
                throw new IllegalArgumentException(field + " must not contain null");
            }
            if (!seen.add(code)) {
                throw new IllegalArgumentException(field + " contains duplicate reason code " + code);
            }
        }
        return List.copyOf(seen);
    }

    /**
     * Immutable horizon list in canonical order ({@code 1S, 5S, 15S, 60S}): {@code null} → empty;
     * null elements and duplicates are rejected; any input order is accepted and normalised (safe,
     * because uniqueness is enforced first, so normalisation cannot hide information).
     */
    static List<MarketHorizon> canonicalHorizons(Collection<MarketHorizon> horizons, String field) {
        if (horizons == null) {
            return List.of();
        }
        EnumSet<MarketHorizon> seen = EnumSet.noneOf(MarketHorizon.class);
        for (MarketHorizon horizon : horizons) {
            if (horizon == null) {
                throw new IllegalArgumentException(field + " must not contain null");
            }
            if (!seen.add(horizon)) {
                throw new IllegalArgumentException(field + " contains duplicate horizon " + horizon);
            }
        }
        List<MarketHorizon> ordered = new ArrayList<>(seen.size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            if (seen.contains(horizon)) {
                ordered.add(horizon);
            }
        }
        return List.copyOf(ordered);
    }

    static <T> List<T> requireNoNulls(Collection<T> items, String field) {
        if (items == null) {
            return List.of();
        }
        for (T item : items) {
            if (item == null) {
                throw new IllegalArgumentException(field + " must not contain null");
            }
        }
        return List.copyOf(items);
    }
}

package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonBlank;

import java.util.Locale;
import java.util.Set;

/**
 * Version and configuration lineage of the engine interpretation that produced a snapshot (contract:
 * {@code InterpretationLineageEvent}). Two snapshots with equal interpretation lineage and equal
 * {@link FeatureLineage} were produced by identical rules and configuration.
 *
 * <p>{@code interpretationVersion} is the successor of V1 {@code signalSetVersion} (e.g.
 * {@code mse-interpretation-v1}); {@code interpretationConfigHash} is the hash of the canonical
 * serialisation of the interpretation configuration the engine ran with. Both are mandatory lineage:
 * blank values and obvious placeholders ({@code unknown}, {@code todo}, {@code n/a}, ...) are rejected
 * — a snapshot whose configuration is not identifiable is not reproducible and must not exist. The real
 * canonical config hashing is wired with the V2 configuration (later stage); this type only guarantees
 * the value is present and honest.
 */
public record InterpretationLineage(
        String interpretationVersion,
        String interpretationConfigHash) {

    /** Values that are not lineage but an admission that lineage is missing. */
    static final Set<String> PLACEHOLDERS = Set.of("unknown", "todo", "tbd", "n/a", "na", "null", "none", "placeholder");

    public InterpretationLineage {
        requireNotPlaceholder(requireNonBlank(interpretationVersion, "interpretationVersion"), "interpretationVersion");
        requireNotPlaceholder(requireNonBlank(interpretationConfigHash, "interpretationConfigHash"), "interpretationConfigHash");
    }

    private static void requireNotPlaceholder(String value, String field) {
        if (PLACEHOLDERS.contains(value.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(field + " must be real lineage, not the placeholder '" + value + "'");
        }
    }
}

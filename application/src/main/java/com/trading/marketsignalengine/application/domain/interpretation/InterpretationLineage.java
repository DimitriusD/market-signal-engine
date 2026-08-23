package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

/**
 * Version and configuration lineage of the engine interpretation that produced a snapshot (contract:
 * {@code InterpretationLineageEvent}). Two snapshots with equal interpretation lineage and equal
 * {@link FeatureLineage} were produced by identical rules and configuration.
 *
 * <p>{@code interpretationVersion} is the successor of V1 {@code signalSetVersion} (e.g.
 * {@code mse-interpretation-v1}); {@code interpretationConfigHash} is the hash of the canonical
 * serialisation of the interpretation configuration the engine ran with. Both are mandatory lineage:
 * blank values and obvious placeholders ({@code unknown}, {@code todo}, {@code n/a}, ...;
 * {@link Invariants#PLACEHOLDERS}) are rejected — a snapshot whose configuration is not identifiable is
 * not reproducible and must not exist. The real canonical config hashing is wired with the V2
 * configuration (later stage); this type only guarantees the value is present and honest.
 */
public record InterpretationLineage(
        String interpretationVersion,
        String interpretationConfigHash) {

    public InterpretationLineage {
        requireNotPlaceholder(interpretationVersion, "interpretationVersion");
        requireNotPlaceholder(interpretationConfigHash, "interpretationConfigHash");
    }
}

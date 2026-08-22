package com.trading.marketsignalengine.application.domain.model.feature;

import java.util.List;
import lombok.Builder;

/**
 * MFS v2 calculator diagnostics: which feature groups failed to compute on this snapshot out of how
 * many. A failed group means its features are {@code null} upstream; the engine's null semantics
 * already fail closed on them, this record only makes the cause visible downstream.
 */
@Builder(toBuilder = true)
public record FeatureDiagnostics(
        List<String> failedFeatureGroups,
        int totalFeatureGroups) {

    public FeatureDiagnostics {
        failedFeatureGroups = failedFeatureGroups == null ? List.of() : List.copyOf(failedFeatureGroups);
    }

    public boolean hasFailures() {
        return !failedFeatureGroups.isEmpty();
    }
}

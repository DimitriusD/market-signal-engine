package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonBlank;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requirePositiveInstant;

import java.time.Instant;

/**
 * Minimal lineage of the source {@code MarketFeaturesSnapshot} an interpretation was derived from
 * (contract: {@code FeatureLineageEvent}). Full feature values are intentionally <b>not</b> copied:
 * the full snapshot is joined by {@link #sourceFeatureEventId()}. Together with
 * {@link InterpretationLineage} it identifies the exact inputs and configuration behind an
 * interpretation and is the input of the deterministic {@link InterpretationSnapshotIdGenerator}.
 *
 * <p>{@code sourceEvaluationAt <= sourceComputedAt} is deliberately <b>not</b> required: the producer
 * may honestly report a future-event / clock-skew condition ({@code evaluationTs > computedAt} with
 * {@code futureEventDetected = true}); that consistency is the input validator's concern, lineage only
 * records what the source said.
 */
public record FeatureLineage(
        String sourceFeatureEventId,
        int sourceFeatureSchemaVersion,
        String sourceFeatureSetVersion,
        String sourceFeatureConfigHash,
        Instant sourceEvaluationAt,
        Instant sourceComputedAt,
        String sourceTriggerSource) {

    public FeatureLineage {
        requireNonBlank(sourceFeatureEventId, "sourceFeatureEventId");
        require(sourceFeatureSchemaVersion > 0,
                "sourceFeatureSchemaVersion must be positive, got " + sourceFeatureSchemaVersion);
        requireNonBlank(sourceFeatureSetVersion, "sourceFeatureSetVersion");
        requireNonBlank(sourceFeatureConfigHash, "sourceFeatureConfigHash");
        requirePositiveInstant(sourceEvaluationAt, "sourceEvaluationAt");
        requirePositiveInstant(sourceComputedAt, "sourceComputedAt");
        requireNonBlank(sourceTriggerSource, "sourceTriggerSource");
    }

    /** Convenience for callers holding the boxed schema version of the domain feature snapshot. */
    public static FeatureLineage of(String sourceFeatureEventId,
                                    Integer sourceFeatureSchemaVersion,
                                    String sourceFeatureSetVersion,
                                    String sourceFeatureConfigHash,
                                    Instant sourceEvaluationAt,
                                    Instant sourceComputedAt,
                                    String sourceTriggerSource) {
        requireNonNull(sourceFeatureSchemaVersion, "sourceFeatureSchemaVersion");
        return new FeatureLineage(sourceFeatureEventId, sourceFeatureSchemaVersion, sourceFeatureSetVersion,
                sourceFeatureConfigHash, sourceEvaluationAt, sourceComputedAt, sourceTriggerSource);
    }
}

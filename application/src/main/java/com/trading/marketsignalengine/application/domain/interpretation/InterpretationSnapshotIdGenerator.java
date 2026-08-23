package com.trading.marketsignalengine.application.domain.interpretation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic {@code interpretationSnapshotId}: a name-based RFC 4122 v3 UUID
 * ({@link UUID#nameUUIDFromBytes}) over a canonical key built from the feature and interpretation
 * lineage only. Same lineage ⇒ same id in every process, on every platform; any change to a
 * source-identity / version / config field ⇒ a different id. {@code validUntil}, wall clock, evaluation
 * results and other runtime values never enter the key, so a replay of the same input under the same
 * interpretation version and configuration reproduces the id exactly (and a duplicate after a retry
 * dedupes downstream).
 *
 * <h2>Canonical key (algorithm version {@value #ALGORITHM_VERSION})</h2>
 * <pre>
 *   key = ALGORITHM_VERSION
 *       + "|" + field(sourceFeatureEventId)
 *       + "|" + field(decimal(sourceFeatureSchemaVersion))
 *       + "|" + field(sourceFeatureSetVersion)
 *       + "|" + field(sourceFeatureConfigHash)
 *       + "|" + field(decimal(sourceEvaluationAt.toEpochMilli()))
 *       + "|" + field(sourceTriggerSource)
 *       + "|" + field(interpretationVersion)
 *       + "|" + field(interpretationConfigHash)
 *   field(v) = utf8ByteLength(v) + ":" + v      // length-prefixed ⇒ unambiguous even if v contains '|'
 *   id  = UUID.nameUUIDFromBytes(key.getBytes(UTF_8)).toString()
 * </pre>
 * Numbers are rendered with {@link Long#toString(long)} / {@link Integer#toString(int)} (ASCII digits,
 * no locale, no grouping); timestamps as epoch milliseconds UTC (no timezone); the charset is always
 * UTF-8 (no platform default); the field order is fixed (no iteration order). {@code sourceComputedAt}
 * is deliberately excluded: it is producer wall-clock time, not input identity — the same evaluation
 * tick re-computed by the producer must map to the same interpretation id. Changing anything in this
 * key construction requires bumping {@link #ALGORITHM_VERSION} and the pinned fixture test.
 */
public final class InterpretationSnapshotIdGenerator {

    /** Version prefix of the id algorithm itself; bump on any change to the canonical key. */
    public static final String ALGORITHM_VERSION = "mse-interpretation-id-v1";

    private static final String SEPARATOR = "|";

    private InterpretationSnapshotIdGenerator() {
    }

    public static String generate(FeatureLineage featureLineage, InterpretationLineage interpretationLineage) {
        String key = canonicalKey(featureLineage, interpretationLineage);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** The exact canonical key the id is derived from; exposed for tests and documentation. */
    public static String canonicalKey(FeatureLineage featureLineage, InterpretationLineage interpretationLineage) {
        Objects.requireNonNull(featureLineage, "featureLineage");
        Objects.requireNonNull(interpretationLineage, "interpretationLineage");
        return ALGORITHM_VERSION
                + SEPARATOR + field(featureLineage.sourceFeatureEventId())
                + SEPARATOR + field(Integer.toString(featureLineage.sourceFeatureSchemaVersion()))
                + SEPARATOR + field(featureLineage.sourceFeatureSetVersion())
                + SEPARATOR + field(featureLineage.sourceFeatureConfigHash())
                + SEPARATOR + field(Long.toString(featureLineage.sourceEvaluationAt().toEpochMilli()))
                + SEPARATOR + field(featureLineage.sourceTriggerSource())
                + SEPARATOR + field(interpretationLineage.interpretationVersion())
                + SEPARATOR + field(interpretationLineage.interpretationConfigHash());
    }

    private static String field(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
    }
}

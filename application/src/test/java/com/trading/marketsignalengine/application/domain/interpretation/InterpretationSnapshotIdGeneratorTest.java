package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.COMPUTED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.EVALUATED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.featureLineage;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.interpretationLineage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class InterpretationSnapshotIdGeneratorTest {

    /**
     * Pinned fixture. The key and the id were computed independently (MD5 → RFC 4122 v3 UUID) from the
     * documented canonical format; if this test fails, the id algorithm changed and
     * {@link InterpretationSnapshotIdGenerator#ALGORITHM_VERSION} must be bumped deliberately.
     */
    private static final String PINNED_KEY = "mse-interpretation-id-v1"
            + "|9:feat-0001"
            + "|1:1"
            + "|15:mfs-features-v2"
            + "|15:cfg-test-mfs-v2"
            + "|13:1767225600000"
            + "|5:TRADE"
            + "|21:mse-interpretation-v1"
            + "|26:cfg-test-interpretation-v1";
    private static final String PINNED_ID = "b48150cc-00f0-3fd9-8f5c-7397e14828c9";

    @Test
    void pinnedCanonicalFixtureProducesThePinnedId() {
        assertEquals(1_767_225_600_000L, EVALUATED_AT.toEpochMilli());
        assertEquals(PINNED_KEY, InterpretationSnapshotIdGenerator.canonicalKey(featureLineage(), interpretationLineage()));
        assertEquals(PINNED_ID, InterpretationSnapshotIdGenerator.generate(featureLineage(), interpretationLineage()));
        assertEquals("mse-interpretation-id-v1", InterpretationSnapshotIdGenerator.ALGORITHM_VERSION);
        // the id is exactly the v3 UUID of the UTF-8 key
        assertEquals(UUID.nameUUIDFromBytes(PINNED_KEY.getBytes(StandardCharsets.UTF_8)).toString(), PINNED_ID);
    }

    @Test
    void sameLineageGivesSameIdAcrossInstances() {
        FeatureLineage a = featureLineage();
        FeatureLineage b = new FeatureLineage("feat-0001", 1, "mfs-features-v2", "cfg-test-mfs-v2",
                Instant.ofEpochMilli(EVALUATED_AT.toEpochMilli()), Instant.ofEpochMilli(COMPUTED_AT.toEpochMilli()), "TRADE");
        assertEquals(InterpretationSnapshotIdGenerator.generate(a, interpretationLineage()),
                InterpretationSnapshotIdGenerator.generate(b, new InterpretationLineage("mse-interpretation-v1", "cfg-test-interpretation-v1")));
    }

    @Test
    void everyIdentityVersionAndConfigFieldChangesTheId() {
        String baseline = InterpretationSnapshotIdGenerator.generate(featureLineage(), interpretationLineage());

        assertChanges(baseline, l -> new FeatureLineage("feat-0002", l.sourceFeatureSchemaVersion(), l.sourceFeatureSetVersion(),
                l.sourceFeatureConfigHash(), l.sourceEvaluationAt(), l.sourceComputedAt(), l.sourceTriggerSource()), "sourceFeatureEventId");
        assertChanges(baseline, l -> new FeatureLineage(l.sourceFeatureEventId(), 2, l.sourceFeatureSetVersion(),
                l.sourceFeatureConfigHash(), l.sourceEvaluationAt(), l.sourceComputedAt(), l.sourceTriggerSource()), "sourceFeatureSchemaVersion");
        assertChanges(baseline, l -> new FeatureLineage(l.sourceFeatureEventId(), l.sourceFeatureSchemaVersion(), "mfs-features-v3",
                l.sourceFeatureConfigHash(), l.sourceEvaluationAt(), l.sourceComputedAt(), l.sourceTriggerSource()), "sourceFeatureSetVersion");
        assertChanges(baseline, l -> new FeatureLineage(l.sourceFeatureEventId(), l.sourceFeatureSchemaVersion(), l.sourceFeatureSetVersion(),
                "cfg-other", l.sourceEvaluationAt(), l.sourceComputedAt(), l.sourceTriggerSource()), "sourceFeatureConfigHash");
        assertChanges(baseline, l -> new FeatureLineage(l.sourceFeatureEventId(), l.sourceFeatureSchemaVersion(), l.sourceFeatureSetVersion(),
                l.sourceFeatureConfigHash(), l.sourceEvaluationAt().plusMillis(1), l.sourceComputedAt(), l.sourceTriggerSource()), "sourceEvaluationAt");
        assertChanges(baseline, l -> new FeatureLineage(l.sourceFeatureEventId(), l.sourceFeatureSchemaVersion(), l.sourceFeatureSetVersion(),
                l.sourceFeatureConfigHash(), l.sourceEvaluationAt(), l.sourceComputedAt(), "TIMER"), "sourceTriggerSource");

        assertNotEquals(baseline, InterpretationSnapshotIdGenerator.generate(featureLineage(),
                new InterpretationLineage("mse-interpretation-v2", "cfg-test-interpretation-v1")), "interpretationVersion");
        assertNotEquals(baseline, InterpretationSnapshotIdGenerator.generate(featureLineage(),
                new InterpretationLineage("mse-interpretation-v1", "cfg-test-interpretation-v2")), "interpretationConfigHash");
    }

    @Test
    void producerWallClockDoesNotChangeTheId() {
        // sourceComputedAt is producer wall-clock time, not input identity
        FeatureLineage recomputed = new FeatureLineage("feat-0001", 1, "mfs-features-v2", "cfg-test-mfs-v2",
                EVALUATED_AT, COMPUTED_AT.plusSeconds(3), "TRADE");
        assertEquals(PINNED_ID, InterpretationSnapshotIdGenerator.generate(recomputed, interpretationLineage()));
    }

    @Test
    void canonicalKeyIsUnambiguousForValuesContainingTheSeparator() {
        // length prefixes make "a|b" + "c" distinguishable from "a" + "b|c"
        FeatureLineage x = new FeatureLineage("a|b", 1, "c", "cfg", EVALUATED_AT, COMPUTED_AT, "TRADE");
        FeatureLineage y = new FeatureLineage("a", 1, "b|c", "cfg", EVALUATED_AT, COMPUTED_AT, "TRADE");
        assertNotEquals(InterpretationSnapshotIdGenerator.canonicalKey(x, interpretationLineage()),
                InterpretationSnapshotIdGenerator.canonicalKey(y, interpretationLineage()));
        assertNotEquals(InterpretationSnapshotIdGenerator.generate(x, interpretationLineage()),
                InterpretationSnapshotIdGenerator.generate(y, interpretationLineage()));
        // UTF-8 byte length, not char count
        FeatureLineage utf8 = new FeatureLineage("\u00e9", 1, "c", "cfg", EVALUATED_AT, COMPUTED_AT, "TRADE");
        assertEquals("mse-interpretation-id-v1|2:\u00e9|1:1|1:c|3:cfg|13:1767225600000|5:TRADE|21:mse-interpretation-v1|26:cfg-test-interpretation-v1",
                InterpretationSnapshotIdGenerator.canonicalKey(utf8, interpretationLineage()));
    }

    @Test
    void nullLineageIsRejected() {
        assertThrows(NullPointerException.class, () -> InterpretationSnapshotIdGenerator.generate(null, interpretationLineage()));
        assertThrows(NullPointerException.class, () -> InterpretationSnapshotIdGenerator.generate(featureLineage(), null));
    }

    private static void assertChanges(String baseline, Function<FeatureLineage, FeatureLineage> mutation, String field) {
        assertNotEquals(baseline, InterpretationSnapshotIdGenerator.generate(mutation.apply(featureLineage()), interpretationLineage()),
                field + " must influence the id");
    }
}

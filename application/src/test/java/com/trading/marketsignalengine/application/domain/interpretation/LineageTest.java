package com.trading.marketsignalengine.application.domain.interpretation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LineageTest {

    private static final Instant EVAL = Instant.parse("2026-03-04T05:06:07.890Z");
    private static final Instant COMPUTED = EVAL.plusMillis(42);

    // ------------------------------------------------------------------ FeatureLineageFactory

    @Test
    void featureLineageFactoryTransfersEveryLineageFieldLosslessly() {
        MarketFeaturesSnapshot snapshot = SignalRuleTestSupport.tradableFeaturesBuilder()
                .snapshotId("evt-42")
                .schemaVersion(1)
                .featureSetVersion("mfs-features-v2")
                .configHash("cfg-abc")
                .evaluationTs(EVAL)
                .computedAt(COMPUTED)
                .triggerSource("ORDER_BOOK_L2_SNAPSHOT")
                .build();

        FeatureLineage lineage = FeatureLineageFactory.from(snapshot);

        assertEquals("evt-42", lineage.sourceFeatureEventId());
        assertEquals(1, lineage.sourceFeatureSchemaVersion());
        assertEquals("mfs-features-v2", lineage.sourceFeatureSetVersion());
        assertEquals("cfg-abc", lineage.sourceFeatureConfigHash());
        assertEquals(EVAL, lineage.sourceEvaluationAt());
        assertEquals(COMPUTED, lineage.sourceComputedAt());
        assertEquals("ORDER_BOOK_L2_SNAPSHOT", lineage.sourceTriggerSource());
        assertEquals(lineage, FeatureLineageFactory.from(snapshot), "pure: same input, same lineage");
    }

    @Test
    void featureLineageFactoryKeepsFutureEventSkewAsReported() {
        // evaluationTs after computedAt is an honest producer report (futureEventDetected), not a lineage error
        MarketFeaturesSnapshot snapshot = SignalRuleTestSupport.tradableFeaturesBuilder()
                .evaluationTs(COMPUTED.plusMillis(500))
                .computedAt(COMPUTED)
                .build();
        FeatureLineage lineage = FeatureLineageFactory.from(snapshot);
        assertEquals(COMPUTED.plusMillis(500), lineage.sourceEvaluationAt());
        assertEquals(COMPUTED, lineage.sourceComputedAt());
    }

    @Test
    void featureLineageFactoryNeverProducesAnIncompleteLineage() {
        assertThrows(NullPointerException.class, () -> FeatureLineageFactory.from(null));
        assertThrows(IllegalArgumentException.class, () -> FeatureLineageFactory.from(
                SignalRuleTestSupport.tradableFeaturesBuilder().schemaVersion(null).build()));
        assertThrows(IllegalArgumentException.class, () -> FeatureLineageFactory.from(
                SignalRuleTestSupport.tradableFeaturesBuilder().configHash(" ").build()));
        assertThrows(IllegalArgumentException.class, () -> FeatureLineageFactory.from(
                SignalRuleTestSupport.tradableFeaturesBuilder().evaluationTs(null).build()));
        assertThrows(IllegalArgumentException.class, () -> FeatureLineageFactory.from(
                SignalRuleTestSupport.tradableFeaturesBuilder().triggerSource("").build()));
    }

    // ------------------------------------------------------------------ FeatureLineage

    @Test
    void featureLineageRejectsBlankOrInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> lineage(" ", 1, "fs", "cfg", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage(null, 1, "fs", "cfg", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 0, "fs", "cfg", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", -1, "fs", "cfg", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "", "cfg", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "", EVAL, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "cfg", null, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "cfg", Instant.EPOCH, COMPUTED, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "cfg", EVAL, null, "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "cfg", EVAL, Instant.ofEpochMilli(-1), "TRADE"));
        assertThrows(IllegalArgumentException.class, () -> lineage("e", 1, "fs", "cfg", EVAL, COMPUTED, " "));
        assertThrows(IllegalArgumentException.class, () -> FeatureLineage.of("e", null, "fs", "cfg", EVAL, COMPUTED, "TRADE"));

        // evaluation after computed is allowed (future event / clock skew is reported upstream)
        lineage("e", 1, "fs", "cfg", COMPUTED.plusSeconds(1), COMPUTED, "TRADE");
    }

    // ------------------------------------------------------------------ InterpretationLineage

    @Test
    void interpretationLineageRejectsBlankAndPlaceholderValues() {
        new InterpretationLineage("mse-interpretation-v1", "7f3a9c");
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage(null, "7f3a9c"));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage(" ", "7f3a9c"));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage("mse-interpretation-v1", null));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage("mse-interpretation-v1", ""));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage("unknown", "7f3a9c"));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage("mse-interpretation-v1", "TODO"));
        assertThrows(IllegalArgumentException.class, () -> new InterpretationLineage("mse-interpretation-v1", "n/a"));
    }

    private static FeatureLineage lineage(String id, int schema, String fs, String cfg, Instant eval, Instant computed,
                                          String trigger) {
        return new FeatureLineage(id, schema, fs, cfg, eval, computed, trigger);
    }
}

package com.trading.marketsignalengine.application.domain.interpretation;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.Objects;

/**
 * Pure mapper from the domain feature snapshot to its {@link FeatureLineage}: a lossless transfer of
 * the identity / version / config / timing / trigger fields, no clock, no defaults. The input is
 * normally already validated by {@code MarketFeaturesSnapshotValidator}, but the factory never
 * produces an inconsistent lineage on its own — a missing field fails here with
 * {@link IllegalArgumentException} instead of becoming a blank or zero in the lineage.
 *
 * <p>Field mapping: {@code snapshotId → sourceFeatureEventId}, {@code schemaVersion →
 * sourceFeatureSchemaVersion}, {@code featureSetVersion → sourceFeatureSetVersion}, {@code configHash →
 * sourceFeatureConfigHash}, {@code evaluationTs → sourceEvaluationAt}, {@code computedAt →
 * sourceComputedAt}, {@code triggerSource → sourceTriggerSource}.
 */
public final class FeatureLineageFactory {

    private FeatureLineageFactory() {
    }

    public static FeatureLineage from(MarketFeaturesSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return FeatureLineage.of(
                snapshot.snapshotId(),
                snapshot.schemaVersion(),
                snapshot.featureSetVersion(),
                snapshot.configHash(),
                snapshot.evaluationTs(),
                snapshot.computedAt(),
                snapshot.triggerSource());
    }
}

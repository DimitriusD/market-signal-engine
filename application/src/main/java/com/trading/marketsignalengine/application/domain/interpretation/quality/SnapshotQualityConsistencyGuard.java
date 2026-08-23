package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.Objects;

/**
 * Pure consistency guard shared by every evidence evaluator: the {@link QualityAssessment} handed to
 * an evaluator must have been produced from <em>this</em> {@link MarketFeaturesSnapshot}. The two
 * arrive as separate arguments (Stage 3 returns a standalone assessment), so a caller could otherwise
 * pair a fresh, all-ELIGIBLE assessment of snapshot A with the feature values of an UNSAFE / stale
 * snapshot B and mint directional evidence from data the quality layer never cleared.
 *
 * <p>Every source fact the assessment carries is compared against the snapshot:
 * {@code sourceQualityStatus}, {@code futureEventDetected}, {@code timing.sourceEvaluationAt}
 * ({@code evaluationTs}), {@code timing.sourceComputedAt} ({@code computedAt}), the failed feature
 * groups ({@link FeatureGroupId#failedGroupsOf}) — and the full per-horizon
 * {@code horizonEligibilities}, re-derived from the snapshot through the canonical
 * {@link HorizonEligibilityResolver} (never a copy of its rules). The eligibility check is what
 * separates two same-status DEGRADED snapshots whose degradation differs (e.g. incomplete book vs
 * stale trades): identical timestamps, status and failed groups, but ELIGIBLE vs UNTRUSTED horizons.
 *
 * <p>This is a structural cross-check, not full lineage binding — a typed Stage 3 result carrying the
 * snapshot (and, on the wire, {@code sourceFeatureEventId}) is the runtime assembler's job. Each
 * evaluator runs {@link #verify} exactly once per public {@code evaluate(...)} call, before any
 * feature value is read — never once per horizon. Stateless and thread-safe; deliberately not
 * {@code final} so tests can instrument the call count without a mocking library.
 */
public class SnapshotQualityConsistencyGuard {

    /**
     * The canonical Stage 3 eligibility rules, used to re-derive the eligibilities this snapshot must
     * produce — never a copy of those rules.
     */
    private final HorizonEligibilityResolver horizonEligibilityResolver;

    public SnapshotQualityConsistencyGuard() {
        this(new HorizonEligibilityResolver());
    }

    public SnapshotQualityConsistencyGuard(HorizonEligibilityResolver horizonEligibilityResolver) {
        this.horizonEligibilityResolver = requireNonNull(horizonEligibilityResolver, "horizonEligibilityResolver");
    }

    /**
     * Fails fast ({@link IllegalArgumentException}) when {@code qualityAssessment} was not produced
     * from {@code snapshot}; returns silently for a consistent pair.
     */
    public void verify(MarketFeaturesSnapshot snapshot, QualityAssessment qualityAssessment) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        FeatureQuality quality = snapshot.quality();
        FeatureQualityStatus expectedStatus = quality == null ? null : quality.status();
        requireMatch(qualityAssessment.sourceQualityStatus() == expectedStatus,
                "sourceQualityStatus", expectedStatus, qualityAssessment.sourceQualityStatus());
        boolean expectedFutureEvent = quality != null && quality.futureEventDetected();
        requireMatch(qualityAssessment.futureEventDetected() == expectedFutureEvent,
                "futureEventDetected", expectedFutureEvent, qualityAssessment.futureEventDetected());
        requireMatch(Objects.equals(qualityAssessment.timing().sourceEvaluationAt(), snapshot.evaluationTs()),
                "timing.sourceEvaluationAt", snapshot.evaluationTs(), qualityAssessment.timing().sourceEvaluationAt());
        requireMatch(Objects.equals(qualityAssessment.timing().sourceComputedAt(), snapshot.computedAt()),
                "timing.sourceComputedAt", snapshot.computedAt(), qualityAssessment.timing().sourceComputedAt());
        requireMatch(qualityAssessment.failedFeatureGroups().equals(FeatureGroupId.failedGroupsOf(snapshot.diagnostics())),
                "failedFeatureGroups", FeatureGroupId.failedGroupsOf(snapshot.diagnostics()),
                qualityAssessment.failedFeatureGroups());
        HorizonEligibilities expectedEligibilities = horizonEligibilityResolver.resolve(snapshot);
        requireMatch(expectedEligibilities.equals(qualityAssessment.horizonEligibilities()),
                "horizonEligibilities", expectedEligibilities, qualityAssessment.horizonEligibilities());
    }

    private static void requireMatch(boolean matches, String fact, Object fromSnapshot, Object fromAssessment) {
        if (!matches) {
            throw new IllegalArgumentException("qualityAssessment was not produced from this snapshot: "
                    + fact + " is '" + fromAssessment + "' but the snapshot says '" + fromSnapshot + "'");
        }
    }
}

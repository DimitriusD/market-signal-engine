package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure quality resolver: one <em>validated</em> {@link MarketFeaturesSnapshot}, an explicit
 * {@code assessedAt} and a {@link QualityEligibilityPolicy} in, a {@link QualityAssessment} out. No
 * clock, no defaults, no metrics; the same inputs always give the same result. It runs after
 * {@code MarketFeaturesSnapshotValidator} and does not repeat structural validation — it only fails
 * fast on what it cannot work without (snapshot, quality, quality status, source instants, assessedAt,
 * policy).
 *
 * <h2>Global quality policy</h2>
 * <pre>
 *   source NO_DATA                                   → NO_DATA,  eligible = false  [SOURCE_NO_DATA]
 *   source UNSAFE                                    → BLOCKED,  eligible = false  [SOURCE_QUALITY_UNSAFE]
 *   timing CLOCK_SKEW                                → BLOCKED,  eligible = false  [SOURCE_CLOCK_SKEW (+ SOURCE_FUTURE_EVENT)]
 *   timing STALE                                     → BLOCKED,  eligible = false  [FEATURE_SNAPSHOT_STALE / PROCESSING_LATENCY_EXCEEDED]
 *   futureEventDetected and policy.blockFutureEvents → BLOCKED,  eligible = false  [SOURCE_FUTURE_EVENT]
 *   futureEventDetected, policy allows               → at least DEGRADED           [SOURCE_FUTURE_EVENT], eligibility by the rules below
 *   source OK, all horizons ELIGIBLE, timing FRESH   → OK,       eligible = true
 *   source OK, some horizons not ELIGIBLE            → DEGRADED, eligible = (≥ 1 ELIGIBLE horizon)  [HORIZONS_PARTIALLY_ELIGIBLE | NO_ELIGIBLE_HORIZONS]
 *   source DEGRADED (no hard gate)                   → DEGRADED, eligible = (≥ 1 ELIGIBLE horizon)  [SOURCE_QUALITY_DEGRADED, ...]
 *   any failed feature group (no hard gate)          → at least DEGRADED           [FEATURE_GROUP_FAILURE]
 * </pre>
 * Hard gates are evaluated in the order NO_DATA → UNSAFE → CLOCK_SKEW → STALE → future event; the
 * overall reason list is the union of every applicable reason (source, future event, timing, feature
 * group failure, horizon summary), deterministic and duplicate-free. An overall hard gate does
 * <b>not</b> rewrite a valid trade-flow horizon to FAILED — the overall gate and the per-horizon
 * feature eligibility are different facts and both are reported. "Eligible" at this stage means the
 * engine may continue interpretation; which horizons a concrete pattern needs is decided later.
 */
public final class QualityAssessmentResolver {

    private final HorizonEligibilityResolver horizonEligibilityResolver;
    private final TimingAssessmentResolver timingAssessmentResolver;

    public QualityAssessmentResolver() {
        this(new HorizonEligibilityResolver(), new TimingAssessmentResolver());
    }

    public QualityAssessmentResolver(HorizonEligibilityResolver horizonEligibilityResolver,
                                     TimingAssessmentResolver timingAssessmentResolver) {
        this.horizonEligibilityResolver = Objects.requireNonNull(horizonEligibilityResolver, "horizonEligibilityResolver");
        this.timingAssessmentResolver = Objects.requireNonNull(timingAssessmentResolver, "timingAssessmentResolver");
    }

    public QualityAssessment resolve(MarketFeaturesSnapshot snapshot, Instant assessedAt, QualityEligibilityPolicy policy) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(assessedAt, "assessedAt");
        Objects.requireNonNull(policy, "policy");
        FeatureQuality quality = Objects.requireNonNull(snapshot.quality(), "snapshot.quality");
        FeatureQualityStatus sourceStatus = Objects.requireNonNull(quality.status(), "snapshot.quality.status");

        HorizonEligibilities horizons = horizonEligibilityResolver.resolve(snapshot);
        TimingAssessment timing = timingAssessmentResolver.resolve(
                assessedAt,
                Objects.requireNonNull(snapshot.evaluationTs(), "snapshot.evaluationTs"),
                Objects.requireNonNull(snapshot.computedAt(), "snapshot.computedAt"),
                policy);
        List<FeatureGroupId> failedGroups = FeatureGroupId.failedGroupsOf(snapshot.diagnostics());
        boolean futureEvent = quality.futureEventDetected();

        List<ReasonCode> reasons = new ArrayList<>();
        switch (sourceStatus) {
            case OK -> { }
            case DEGRADED -> reasons.add(QualityReasonCodes.SOURCE_QUALITY_DEGRADED);
            case UNSAFE -> reasons.add(QualityReasonCodes.SOURCE_QUALITY_UNSAFE);
            case NO_DATA -> reasons.add(QualityReasonCodes.SOURCE_NO_DATA);
        }
        if (futureEvent) {
            reasons.add(QualityReasonCodes.SOURCE_FUTURE_EVENT);
        }
        addDistinct(reasons, timing.reasonCodes());
        if (!failedGroups.isEmpty()) {
            reasons.add(QualityReasonCodes.FEATURE_GROUP_FAILURE);
        }
        if (!horizons.anyEligible()) {
            reasons.add(QualityReasonCodes.NO_ELIGIBLE_HORIZONS);
        } else if (!horizons.allEligible()) {
            reasons.add(QualityReasonCodes.HORIZONS_PARTIALLY_ELIGIBLE);
        }

        InterpretationQuality interpretationQuality;
        if (sourceStatus == FeatureQualityStatus.NO_DATA) {
            interpretationQuality = InterpretationQuality.noData(reasons);
        } else if (sourceStatus == FeatureQualityStatus.UNSAFE
                || timing.status() != TimingStatus.FRESH
                || (futureEvent && policy.blockFutureEvents())) {
            interpretationQuality = InterpretationQuality.blocked(reasons);
        } else {
            boolean degraded = sourceStatus == FeatureQualityStatus.DEGRADED
                    || !horizons.allEligible()
                    || futureEvent
                    || !failedGroups.isEmpty();
            interpretationQuality = degraded
                    ? InterpretationQuality.degraded(horizons.anyEligible(), reasons)
                    : InterpretationQuality.ok(reasons);
        }

        return new QualityAssessment(sourceStatus, interpretationQuality, timing, horizons,
                failedGroups, futureEvent, reasons);
    }

    private static void addDistinct(List<ReasonCode> target, List<ReasonCode> codes) {
        for (ReasonCode code : codes) {
            if (!target.contains(code)) {
                target.add(code);
            }
        }
    }
}

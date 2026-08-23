package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure freshness resolver: explicit instants in, {@link TimingAssessment} out. No clock, no defaults,
 * no clamping. Rules (in precedence order):
 * <ol>
 *   <li>{@code featureAgeMs < 0} or {@code processingLatencyMs < 0} → {@link TimingStatus#CLOCK_SKEW}
 *       with {@code SOURCE_CLOCK_SKEW}; a negative <em>age</em> additionally carries
 *       {@code SOURCE_FUTURE_EVENT} (the market as-of instant is in the engine's future).</li>
 *   <li>{@code featureAgeMs > policy.maxFeatureAge} → {@link TimingStatus#STALE} with
 *       {@code FEATURE_SNAPSHOT_STALE}; {@code processingLatencyMs > policy.maxProcessingLatency} →
 *       STALE with {@code PROCESSING_LATENCY_EXCEEDED}; both may apply.</li>
 *   <li>Otherwise {@link TimingStatus#FRESH} (thresholds inclusive: exactly at the limit is fresh).</li>
 * </ol>
 * {@link TimingStatus#UNKNOWN} is never produced: missing instants fail fast here (the validator
 * guarantees them for every accepted snapshot).
 */
public final class TimingAssessmentResolver {

    public TimingAssessment resolve(Instant assessedAt, Instant sourceEvaluationAt, Instant sourceComputedAt,
                                    QualityEligibilityPolicy policy) {
        Objects.requireNonNull(assessedAt, "assessedAt");
        Objects.requireNonNull(sourceEvaluationAt, "sourceEvaluationAt");
        Objects.requireNonNull(sourceComputedAt, "sourceComputedAt");
        Objects.requireNonNull(policy, "policy");

        long featureAgeMs = assessedAt.toEpochMilli() - sourceEvaluationAt.toEpochMilli();
        long processingLatencyMs = assessedAt.toEpochMilli() - sourceComputedAt.toEpochMilli();

        List<ReasonCode> reasons = new ArrayList<>(3);
        TimingStatus status;
        if (featureAgeMs < 0L || processingLatencyMs < 0L) {
            status = TimingStatus.CLOCK_SKEW;
            reasons.add(QualityReasonCodes.SOURCE_CLOCK_SKEW);
            if (featureAgeMs < 0L) {
                reasons.add(QualityReasonCodes.SOURCE_FUTURE_EVENT);
            }
        } else {
            boolean stale = false;
            if (featureAgeMs > policy.maxFeatureAgeMs()) {
                stale = true;
                reasons.add(QualityReasonCodes.FEATURE_SNAPSHOT_STALE);
            }
            if (processingLatencyMs > policy.maxProcessingLatencyMs()) {
                stale = true;
                reasons.add(QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED);
            }
            status = stale ? TimingStatus.STALE : TimingStatus.FRESH;
        }
        return new TimingAssessment(assessedAt, sourceEvaluationAt, sourceComputedAt,
                featureAgeMs, processingLatencyMs, status, reasons);
    }
}

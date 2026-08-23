package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.Invariants;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.time.Instant;
import java.util.List;

/**
 * Typed freshness result of one feature snapshot at one explicit assessment instant.
 * <pre>
 *   featureAgeMs        = assessedAt - sourceEvaluationAt   (market as-of age)
 *   processingLatencyMs = assessedAt - sourceComputedAt     (producer → engine path)
 * </pre>
 * Both values are the raw signed differences: a negative value is <b>never clamped</b> to zero — it
 * is the evidence of {@link TimingStatus#CLOCK_SKEW}. The record re-derives both differences from the
 * instants so a stored value cannot disagree with them, and enforces the status ↔ value ↔ reason table
 * (the policy thresholds themselves stay in {@link TimingAssessmentResolver}; the record only
 * guarantees that every non-FRESH status is explainable):
 * <pre>
 *   FRESH       age >= 0, latency >= 0   reasonCodes empty
 *   STALE       age >= 0, latency >= 0   reasonCodes contain FEATURE_SNAPSHOT_STALE and/or PROCESSING_LATENCY_EXCEEDED
 *   CLOCK_SKEW  age < 0 or latency < 0   reasonCodes contain SOURCE_CLOCK_SKEW; age < 0 additionally SOURCE_FUTURE_EVENT
 *   UNKNOWN     fail-closed fallback     nothing enforced (never produced by the resolver)
 * </pre>
 */
public record TimingAssessment(
        Instant assessedAt,
        Instant sourceEvaluationAt,
        Instant sourceComputedAt,
        long featureAgeMs,
        long processingLatencyMs,
        TimingStatus status,
        List<ReasonCode> reasonCodes) {

    public TimingAssessment {
        requireNonNull(assessedAt, "timing.assessedAt");
        requireNonNull(sourceEvaluationAt, "timing.sourceEvaluationAt");
        requireNonNull(sourceComputedAt, "timing.sourceComputedAt");
        requireNonNull(status, "timing.status");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "timing.reasonCodes");

        long expectedAge = assessedAt.toEpochMilli() - sourceEvaluationAt.toEpochMilli();
        long expectedLatency = assessedAt.toEpochMilli() - sourceComputedAt.toEpochMilli();
        require(featureAgeMs == expectedAge,
                "featureAgeMs " + featureAgeMs + " must equal assessedAt - sourceEvaluationAt = " + expectedAge);
        require(processingLatencyMs == expectedLatency,
                "processingLatencyMs " + processingLatencyMs + " must equal assessedAt - sourceComputedAt = "
                        + expectedLatency);

        boolean anyNegative = featureAgeMs < 0L || processingLatencyMs < 0L;
        switch (status) {
            case FRESH -> {
                require(!anyNegative, "timing FRESH cannot carry a negative age / latency");
                require(reasonCodes.isEmpty(), "timing FRESH must not carry reason codes");
            }
            case STALE -> {
                require(!anyNegative, "timing STALE with a negative age / latency must be CLOCK_SKEW");
                require(reasonCodes.contains(QualityReasonCodes.FEATURE_SNAPSHOT_STALE)
                                || reasonCodes.contains(QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED),
                        "timing STALE requires FEATURE_SNAPSHOT_STALE or PROCESSING_LATENCY_EXCEEDED, got "
                                + reasonCodes);
            }
            case CLOCK_SKEW -> {
                require(anyNegative, "timing CLOCK_SKEW requires a negative age or latency");
                require(reasonCodes.contains(QualityReasonCodes.SOURCE_CLOCK_SKEW),
                        "timing CLOCK_SKEW requires SOURCE_CLOCK_SKEW, got " + reasonCodes);
                require(featureAgeMs >= 0L || reasonCodes.contains(QualityReasonCodes.SOURCE_FUTURE_EVENT),
                        "timing CLOCK_SKEW with a negative featureAgeMs requires SOURCE_FUTURE_EVENT, got "
                                + reasonCodes);
            }
            case UNKNOWN -> { /* fail-closed fallback; nothing to check */ }
        }
    }

    public boolean isFresh() {
        return status.isFresh();
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Timing formulas and thresholds: {@code featureAgeMs = assessedAt - evaluationTs},
 * {@code processingLatencyMs = assessedAt - computedAt}; inclusive thresholds; negative values are
 * reported (never clamped) and mean CLOCK_SKEW, which wins over STALE; deterministic.
 */
class TimingAssessmentResolverTest {

    private static final Instant EVALUATION_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant COMPUTED_AT = EVALUATION_AT.plusMillis(25);
    private static final QualityEligibilityPolicy POLICY = QualityEligibilityPolicy.of(
            Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);

    private final TimingAssessmentResolver resolver = new TimingAssessmentResolver();

    @Test
    void freshWhenBothWithinThresholds() {
        TimingAssessment timing = resolver.resolve(EVALUATION_AT.plusMillis(100), EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.FRESH, timing.status());
        assertEquals(100L, timing.featureAgeMs());
        assertEquals(75L, timing.processingLatencyMs());
        assertTrue(timing.reasonCodes().isEmpty());
        assertTrue(timing.isFresh());
    }

    @Test
    void ageExactlyAtThresholdIsFresh() {
        // Keep latency inside its threshold by moving computedAt close to assessedAt.
        Instant assessedAt = EVALUATION_AT.plusMillis(2_000);
        TimingAssessment timing = resolver.resolve(assessedAt, EVALUATION_AT, assessedAt.minusMillis(10), POLICY);

        assertEquals(2_000L, timing.featureAgeMs());
        assertEquals(TimingStatus.FRESH, timing.status());
    }

    @Test
    void latencyExactlyAtThresholdIsFresh() {
        // computedAt 25 ms after evaluationTs: age 1025 (fine), latency exactly 1000.
        TimingAssessment timing = resolver.resolve(COMPUTED_AT.plusMillis(1_000), EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(1_000L, timing.processingLatencyMs());
        assertEquals(TimingStatus.FRESH, timing.status());
    }

    @Test
    void ageOneMillisecondOverThresholdIsStale() {
        // Keep latency inside its threshold by moving computedAt close to assessedAt.
        Instant assessedAt = EVALUATION_AT.plusMillis(2_001);
        TimingAssessment timing = resolver.resolve(assessedAt, EVALUATION_AT, assessedAt.minusMillis(10), POLICY);

        assertEquals(TimingStatus.STALE, timing.status());
        assertEquals(2_001L, timing.featureAgeMs());
        assertEquals(List.of(QualityReasonCodes.FEATURE_SNAPSHOT_STALE), timing.reasonCodes());
    }

    @Test
    void latencyOneMillisecondOverThresholdIsStale() {
        TimingAssessment timing = resolver.resolve(COMPUTED_AT.plusMillis(1_001), EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.STALE, timing.status());
        assertEquals(1_001L, timing.processingLatencyMs());
        assertEquals(List.of(QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED), timing.reasonCodes());
    }

    @Test
    void bothThresholdsExceededCarryBothReasons() {
        TimingAssessment timing = resolver.resolve(EVALUATION_AT.plusSeconds(10), EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.STALE, timing.status());
        assertEquals(List.of(QualityReasonCodes.FEATURE_SNAPSHOT_STALE, QualityReasonCodes.PROCESSING_LATENCY_EXCEEDED),
                timing.reasonCodes());
    }

    @Test
    void negativeFeatureAgeIsClockSkewAndFutureEvent() {
        // The market as-of instant is 50 ms in the engine's future; computedAt even more so.
        Instant assessedAt = EVALUATION_AT.minusMillis(50);
        TimingAssessment timing = resolver.resolve(assessedAt, EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.CLOCK_SKEW, timing.status());
        assertEquals(-50L, timing.featureAgeMs(), "not clamped");
        assertEquals(-75L, timing.processingLatencyMs(), "not clamped");
        assertEquals(List.of(QualityReasonCodes.SOURCE_CLOCK_SKEW, QualityReasonCodes.SOURCE_FUTURE_EVENT),
                timing.reasonCodes());
    }

    @Test
    void negativeProcessingLatencyAloneIsClockSkewWithoutFutureEvent() {
        // evaluationTs in the past, computedAt 10 ms after the assessment instant (producer clock ahead).
        Instant assessedAt = COMPUTED_AT.minusMillis(10);
        TimingAssessment timing = resolver.resolve(assessedAt, EVALUATION_AT, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.CLOCK_SKEW, timing.status());
        assertEquals(15L, timing.featureAgeMs());
        assertEquals(-10L, timing.processingLatencyMs(), "not clamped");
        assertEquals(List.of(QualityReasonCodes.SOURCE_CLOCK_SKEW), timing.reasonCodes());
    }

    @Test
    void clockSkewWinsOverStaleness() {
        // Age far beyond the threshold but latency negative: CLOCK_SKEW, and no stale reason.
        Instant evaluationAt = EVALUATION_AT.minusSeconds(60);
        Instant assessedAt = COMPUTED_AT.minusMillis(1);
        TimingAssessment timing = resolver.resolve(assessedAt, evaluationAt, COMPUTED_AT, POLICY);

        assertEquals(TimingStatus.CLOCK_SKEW, timing.status());
        assertTrue(timing.featureAgeMs() > POLICY.maxFeatureAgeMs());
        assertEquals(List.of(QualityReasonCodes.SOURCE_CLOCK_SKEW), timing.reasonCodes());
    }

    @Test
    void resultIsDeterministicForEqualInputs() {
        Instant assessedAt = EVALUATION_AT.plusMillis(123);

        assertEquals(resolver.resolve(assessedAt, EVALUATION_AT, COMPUTED_AT, POLICY),
                new TimingAssessmentResolver().resolve(assessedAt, EVALUATION_AT, COMPUTED_AT, POLICY));
    }

    @Test
    void recordRejectsValuesThatContradictTheInstants() {
        Instant assessedAt = EVALUATION_AT.plusMillis(100);
        assertThrows(IllegalArgumentException.class, () -> new TimingAssessment(
                assessedAt, EVALUATION_AT, COMPUTED_AT, 0L, 75L, TimingStatus.FRESH, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TimingAssessment(
                assessedAt, EVALUATION_AT, COMPUTED_AT, 100L, 75L, TimingStatus.CLOCK_SKEW,
                List.of(QualityReasonCodes.SOURCE_CLOCK_SKEW)));
        assertThrows(IllegalArgumentException.class, () -> new TimingAssessment(
                EVALUATION_AT.minusMillis(1), EVALUATION_AT, COMPUTED_AT, -1L, -26L, TimingStatus.FRESH, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TimingAssessment(
                assessedAt, EVALUATION_AT, COMPUTED_AT, 100L, 75L, TimingStatus.FRESH,
                List.of(ReasonCode.of("SOME_REASON"))));
    }

    @Test
    void resolverFailsFastOnMissingInputs() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null, EVALUATION_AT, COMPUTED_AT, POLICY));
        assertThrows(NullPointerException.class, () -> resolver.resolve(COMPUTED_AT, null, COMPUTED_AT, POLICY));
        assertThrows(NullPointerException.class, () -> resolver.resolve(COMPUTED_AT, EVALUATION_AT, null, POLICY));
        assertThrows(NullPointerException.class, () -> resolver.resolve(COMPUTED_AT, EVALUATION_AT, COMPUTED_AT, null));
    }

    @Test
    void policyRequiresPositiveDurationsAndNoDefaults() {
        assertThrows(NullPointerException.class, () -> QualityEligibilityPolicy.of(null, Duration.ofSeconds(1), true));
        assertThrows(NullPointerException.class, () -> QualityEligibilityPolicy.of(Duration.ofSeconds(1), null, true));
        assertThrows(IllegalArgumentException.class, () -> QualityEligibilityPolicy.of(Duration.ZERO, Duration.ofSeconds(1), true));
        assertThrows(IllegalArgumentException.class, () -> QualityEligibilityPolicy.of(Duration.ofSeconds(1), Duration.ofMillis(-1), true));

        QualityEligibilityPolicy policy = QualityEligibilityPolicy.of(Duration.ofMillis(1), Duration.ofMillis(1), false);
        assertEquals(1L, policy.maxFeatureAgeMs());
        assertEquals(1L, policy.maxProcessingLatencyMs());
    }
}

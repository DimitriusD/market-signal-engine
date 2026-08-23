package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityResolver;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;

/**
 * The minimal typed reason taxonomy of the quality layer. Overall codes explain
 * {@code InterpretationQuality} / {@code QualityAssessment}; per-horizon codes explain one
 * {@code HorizonEligibility} and are, where the verdict comes straight from input availability, the
 * same identifiers {@link FeatureAvailabilityResolver} emits (one vocabulary, typed here). Deliberately
 * small: codes are added when an evaluator needs them, not speculatively.
 */
public final class QualityReasonCodes {

    private QualityReasonCodes() {
    }

    // ------------------------------------------------------------------ overall (InterpretationQuality)

    /** Upstream aggregate quality is {@code DEGRADED}. */
    public static final ReasonCode SOURCE_QUALITY_DEGRADED = ReasonCode.of("SOURCE_QUALITY_DEGRADED");
    /** Upstream aggregate quality is {@code UNSAFE}: hard gate, never eligible for trading. */
    public static final ReasonCode SOURCE_QUALITY_UNSAFE = ReasonCode.of("SOURCE_QUALITY_UNSAFE");
    /** Upstream has no market data at all ({@code NO_DATA}); also the per-horizon reason for every horizon. */
    public static final ReasonCode SOURCE_NO_DATA = ReasonCode.of("SOURCE_NO_DATA");
    /**
     * The source reported a future event ({@code quality.futureEventDetected}) or its market as-of
     * instant lies after the assessment instant. Kept whenever either is true, independent of whether
     * the policy blocks on it.
     */
    public static final ReasonCode SOURCE_FUTURE_EVENT = ReasonCode.of("SOURCE_FUTURE_EVENT");
    /** {@code assessedAt - evaluationTs > policy.maxFeatureAge}. */
    public static final ReasonCode FEATURE_SNAPSHOT_STALE = ReasonCode.of("FEATURE_SNAPSHOT_STALE");
    /** {@code assessedAt - computedAt > policy.maxProcessingLatency}. */
    public static final ReasonCode PROCESSING_LATENCY_EXCEEDED = ReasonCode.of("PROCESSING_LATENCY_EXCEEDED");
    /** A source instant lies after {@code assessedAt} (negative age or latency); hard gate. */
    public static final ReasonCode SOURCE_CLOCK_SKEW = ReasonCode.of("SOURCE_CLOCK_SKEW");
    /** At least one but not every horizon is ELIGIBLE. */
    public static final ReasonCode HORIZONS_PARTIALLY_ELIGIBLE = ReasonCode.of("HORIZONS_PARTIALLY_ELIGIBLE");
    /** No horizon is ELIGIBLE: interpretation cannot continue. */
    public static final ReasonCode NO_ELIGIBLE_HORIZONS = ReasonCode.of("NO_ELIGIBLE_HORIZONS");
    /** {@code diagnostics.failedFeatureGroups} is not empty (the groups are listed in the assessment). */
    public static final ReasonCode FEATURE_GROUP_FAILURE = ReasonCode.of("FEATURE_GROUP_FAILURE");

    // ------------------------------------------------------------------ per horizon (HorizonEligibility)

    public static final ReasonCode WINDOW_WARMING_UP = ReasonCode.of(FeatureAvailabilityResolver.CODE_WINDOW_WARMING_UP);
    public static final ReasonCode WINDOW_NOT_COMPUTED = ReasonCode.of(FeatureAvailabilityResolver.CODE_WINDOW_NOT_COMPUTED);
    public static final ReasonCode TRADE_FLOW_GROUP_ABSENT = ReasonCode.of(FeatureAvailabilityResolver.CODE_GROUP_ABSENT);
    public static final ReasonCode TRADE_FLOW_CALCULATOR_FAILED = ReasonCode.of(FeatureAvailabilityResolver.CODE_CALCULATOR_FAILED);
    public static final ReasonCode STALE_TRADES = ReasonCode.of(FeatureAvailabilityResolver.CODE_STALE_TRADES);
    public static final ReasonCode TRADE_HISTORY_GAP = ReasonCode.of(FeatureAvailabilityResolver.CODE_TRADE_HISTORY_GAP);
}

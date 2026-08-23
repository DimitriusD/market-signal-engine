package com.trading.marketsignalengine.application.domain.interpretation.flow;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of the Flow V1 evaluator: every code a {@code FLOW}
 * {@code EvidenceAssessment} produced by {@link FlowAssessmentEvaluator} can carry <em>in addition
 * to</em> the Stage 3 eligibility reasons, which are kept verbatim (never renamed or re-encoded).
 * Deliberately minimal; one constant per distinct verdict, no free-form strings in the evaluator.
 */
public final class FlowReasonCodes {

    private FlowReasonCodes() {
    }

    // ------------------------------------------------------------------ missing input → UNAVAILABLE

    /** The trade-flow group or the horizon's window is absent. */
    public static final ReasonCode FLOW_WINDOW_MISSING = ReasonCode.of("FLOW_WINDOW_MISSING");
    /** The window is present but {@code signedFlowImbalance} is {@code null}. */
    public static final ReasonCode FLOW_IMBALANCE_MISSING = ReasonCode.of("FLOW_IMBALANCE_MISSING");
    /** {@code tradeCount}, {@code aggressiveTradeCount} or {@code unknownSideCount} is {@code null}. */
    public static final ReasonCode FLOW_ACTIVITY_COUNTS_MISSING = ReasonCode.of("FLOW_ACTIVITY_COUNTS_MISSING");

    // ------------------------------------------------------------------ invalid input → UNTRUSTED

    /** {@code signedFlowImbalance} lies outside {@code [-1, 1]}. */
    public static final ReasonCode FLOW_IMBALANCE_OUT_OF_RANGE = ReasonCode.of("FLOW_IMBALANCE_OUT_OF_RANGE");
    /** A negative count or a count contradiction (see {@link FlowAssessmentEvaluator}). */
    public static final ReasonCode FLOW_ACTIVITY_COUNTS_INVALID = ReasonCode.of("FLOW_ACTIVITY_COUNTS_INVALID");
    /** {@code unknownSideCount / tradeCount > policy.maxUnknownSideRatio}. */
    public static final ReasonCode FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED = ReasonCode.of("FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED");

    // ------------------------------------------------------------------ computed, AVAILABLE

    /** Counts are valid but below the policy activity minimum: direction UNKNOWN, not NEUTRAL. */
    public static final ReasonCode FLOW_INSUFFICIENT_ACTIVITY = ReasonCode.of("FLOW_INSUFFICIENT_ACTIVITY");
    /** {@code signedFlowImbalance >= bullishImbalanceThreshold}. */
    public static final ReasonCode FLOW_BULLISH_IMBALANCE = ReasonCode.of("FLOW_BULLISH_IMBALANCE");
    /** {@code signedFlowImbalance <= bearishImbalanceThreshold}. */
    public static final ReasonCode FLOW_BEARISH_IMBALANCE = ReasonCode.of("FLOW_BEARISH_IMBALANCE");
    /** {@code bearishImbalanceThreshold < signedFlowImbalance < bullishImbalanceThreshold}. */
    public static final ReasonCode FLOW_NEUTRAL_IMBALANCE = ReasonCode.of("FLOW_NEUTRAL_IMBALANCE");

    /** Every flow code, in the deterministic order of the evaluation pipeline; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            FLOW_WINDOW_MISSING,
            FLOW_IMBALANCE_MISSING,
            FLOW_ACTIVITY_COUNTS_MISSING,
            FLOW_IMBALANCE_OUT_OF_RANGE,
            FLOW_ACTIVITY_COUNTS_INVALID,
            FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED,
            FLOW_INSUFFICIENT_ACTIVITY,
            FLOW_BULLISH_IMBALANCE,
            FLOW_BEARISH_IMBALANCE,
            FLOW_NEUTRAL_IMBALANCE);
}

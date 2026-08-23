package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of the Momentum V1 evaluator: every code a {@code MOMENTUM}
 * {@code EvidenceAssessment} produced by {@link MomentumAssessmentEvaluator} can carry <em>in
 * addition to</em> the Stage 3 eligibility reasons, which are kept verbatim (never renamed or
 * re-encoded). Deliberately minimal; one constant per distinct verdict, no free-form strings in the
 * evaluator.
 */
public final class MomentumReasonCodes {

    private MomentumReasonCodes() {
    }

    // ------------------------------------------------------------------ not scoped → UNAVAILABLE

    /** MFS v2 publishes no {@code priceChangeBps1s}; the 1S horizon has no momentum evidence by design. */
    public static final ReasonCode MOMENTUM_NOT_SCOPED_TO_HORIZON = ReasonCode.of("MOMENTUM_NOT_SCOPED_TO_HORIZON");

    // ------------------------------------------------------------------ failed input → FAILED

    /** {@code diagnostics.failedFeatureGroups} contains {@code short-term-regime}. */
    public static final ReasonCode MOMENTUM_REGIME_CALCULATOR_FAILED = ReasonCode.of("MOMENTUM_REGIME_CALCULATOR_FAILED");

    // ------------------------------------------------------------------ missing input → UNAVAILABLE

    /** The regime feature group is absent from the snapshot. */
    public static final ReasonCode MOMENTUM_REGIME_MISSING = ReasonCode.of("MOMENTUM_REGIME_MISSING");
    /** The regime group is present but the horizon's {@code priceChangeBps*s} is {@code null}. */
    public static final ReasonCode MOMENTUM_PRICE_CHANGE_MISSING = ReasonCode.of("MOMENTUM_PRICE_CHANGE_MISSING");

    // ------------------------------------------------------------------ invalid input → UNTRUSTED

    /** {@code abs(priceChangeBps) > policy.maxSafeAbsMoveBps}: implausible move, no direction derived. */
    public static final ReasonCode MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE =
            ReasonCode.of("MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE");

    // ------------------------------------------------------------------ computed, AVAILABLE

    /** {@code priceChangeBps >= bullishPriceChangeBpsThreshold}. */
    public static final ReasonCode MOMENTUM_BULLISH_MOVE = ReasonCode.of("MOMENTUM_BULLISH_MOVE");
    /** {@code priceChangeBps <= bearishPriceChangeBpsThreshold}. */
    public static final ReasonCode MOMENTUM_BEARISH_MOVE = ReasonCode.of("MOMENTUM_BEARISH_MOVE");
    /** {@code bearish threshold < priceChangeBps < bullish threshold}. */
    public static final ReasonCode MOMENTUM_NEUTRAL_MOVE = ReasonCode.of("MOMENTUM_NEUTRAL_MOVE");

    /** Every momentum code, in the deterministic order of the evaluation pipeline; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            MOMENTUM_NOT_SCOPED_TO_HORIZON,
            MOMENTUM_REGIME_CALCULATOR_FAILED,
            MOMENTUM_REGIME_MISSING,
            MOMENTUM_PRICE_CHANGE_MISSING,
            MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE,
            MOMENTUM_BULLISH_MOVE,
            MOMENTUM_BEARISH_MOVE,
            MOMENTUM_NEUTRAL_MOVE);
}

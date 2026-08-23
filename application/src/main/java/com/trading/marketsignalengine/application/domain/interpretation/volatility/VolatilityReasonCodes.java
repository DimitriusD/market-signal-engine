package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of the Volatility V1 evaluator: every code a {@code VOLATILITY}
 * assessment produced by {@link VolatilityAssessmentEvaluator} can carry <em>in addition to</em> the
 * Stage 3 eligibility reasons, which are kept verbatim (never renamed or re-encoded). The level
 * itself is typed ({@link VolatilityLevel}); the level codes here are explanation, never the source
 * of the level. Deliberately minimal; one constant per distinct verdict, no free-form strings in the
 * evaluator.
 */
public final class VolatilityReasonCodes {

    private VolatilityReasonCodes() {
    }

    // ------------------------------------------------------------------ failed input → FAILED

    /** {@code diagnostics.failedFeatureGroups} contains {@code short-term-regime}. */
    public static final ReasonCode VOLATILITY_REGIME_CALCULATOR_FAILED =
            ReasonCode.of("VOLATILITY_REGIME_CALCULATOR_FAILED");

    // ------------------------------------------------------------------ missing input → UNAVAILABLE

    /** The regime feature group is absent from the snapshot. */
    public static final ReasonCode VOLATILITY_REGIME_MISSING = ReasonCode.of("VOLATILITY_REGIME_MISSING");
    /** The regime group is present but the horizon's {@code realizedVolatilityBps*s} is {@code null}. */
    public static final ReasonCode VOLATILITY_VALUE_MISSING = ReasonCode.of("VOLATILITY_VALUE_MISSING");

    // ------------------------------------------------------------------ invalid input → UNTRUSTED

    /** Realized volatility is negative — impossible for a magnitude, so the value is corrupt. */
    public static final ReasonCode VOLATILITY_NEGATIVE = ReasonCode.of("VOLATILITY_NEGATIVE");

    // ------------------------------------------------------------------ computed, AVAILABLE

    /** {@code value <= lowUpperBoundBps}. */
    public static final ReasonCode VOLATILITY_LOW = ReasonCode.of("VOLATILITY_LOW");
    /** {@code lowUpperBoundBps < value <= normalUpperBoundBps}. */
    public static final ReasonCode VOLATILITY_NORMAL = ReasonCode.of("VOLATILITY_NORMAL");
    /** {@code normalUpperBoundBps < value <= highUpperBoundBps}. */
    public static final ReasonCode VOLATILITY_HIGH = ReasonCode.of("VOLATILITY_HIGH");
    /** {@code value > highUpperBoundBps}; context only — no block, no NO_TRADE, no direction. */
    public static final ReasonCode VOLATILITY_EXTREME = ReasonCode.of("VOLATILITY_EXTREME");

    /** Every volatility code, in the deterministic order of the evaluation pipeline; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            VOLATILITY_REGIME_CALCULATOR_FAILED,
            VOLATILITY_REGIME_MISSING,
            VOLATILITY_VALUE_MISSING,
            VOLATILITY_NEGATIVE,
            VOLATILITY_LOW,
            VOLATILITY_NORMAL,
            VOLATILITY_HIGH,
            VOLATILITY_EXTREME);
}

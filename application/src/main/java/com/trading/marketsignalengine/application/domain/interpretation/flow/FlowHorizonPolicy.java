package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.math.BigDecimal;

/**
 * Flow V1 heuristic parameters of <b>one</b> {@link MarketHorizon}. The horizon is part of the value so
 * a policy can never be filed under the wrong horizon and a {@link FlowAssessmentPolicy} can reject a
 * duplicate or missing one. Exact {@link BigDecimal} only — no {@code double} (binary rounding, NaN
 * and ±∞ have no place in a deterministic, replayable verdict).
 *
 * <p>Invariants: {@code bullishImbalanceThreshold ∈ (0, 1]}, {@code bearishImbalanceThreshold ∈ [-1, 0)}
 * (so bearish is strictly below bullish and a dead zone always exists around zero),
 * {@code minTradeCount > 0}, {@code minAggressiveTradeCount >= 0}, {@code maxUnknownSideRatio ∈ [0, 1]}.
 * Boundaries are inclusive on the directional side ({@code imbalance >= bullish} is BULLISH,
 * {@code imbalance <= bearish} is BEARISH) and on the accepting side of the unknown-side gate
 * ({@code ratio == max} is still trusted).
 *
 * <p>These are uncalibrated heuristic thresholds, not probabilities, confidences or trading weights;
 * production values are a replay-driven decision of a later stage and never live inside the evaluator.
 */
public record FlowHorizonPolicy(
        MarketHorizon horizon,
        BigDecimal bullishImbalanceThreshold,
        BigDecimal bearishImbalanceThreshold,
        int minTradeCount,
        int minAggressiveTradeCount,
        BigDecimal maxUnknownSideRatio) {

    private static final BigDecimal MINUS_ONE = BigDecimal.ONE.negate();

    public FlowHorizonPolicy {
        requireNonNull(horizon, "flow policy horizon");
        String prefix = "flow policy " + horizon.wireValue() + ".";
        requireNonNull(bullishImbalanceThreshold, prefix + "bullishImbalanceThreshold");
        requireNonNull(bearishImbalanceThreshold, prefix + "bearishImbalanceThreshold");
        requireNonNull(maxUnknownSideRatio, prefix + "maxUnknownSideRatio");
        require(bullishImbalanceThreshold.signum() > 0 && bullishImbalanceThreshold.compareTo(BigDecimal.ONE) <= 0,
                prefix + "bullishImbalanceThreshold must be within (0, 1], got " + bullishImbalanceThreshold.toPlainString());
        require(bearishImbalanceThreshold.signum() < 0 && bearishImbalanceThreshold.compareTo(MINUS_ONE) >= 0,
                prefix + "bearishImbalanceThreshold must be within [-1, 0), got " + bearishImbalanceThreshold.toPlainString());
        require(bearishImbalanceThreshold.compareTo(bullishImbalanceThreshold) < 0,
                prefix + "bearishImbalanceThreshold must be strictly below bullishImbalanceThreshold");
        require(minTradeCount > 0, prefix + "minTradeCount must be positive, got " + minTradeCount);
        require(minAggressiveTradeCount >= 0,
                prefix + "minAggressiveTradeCount must not be negative, got " + minAggressiveTradeCount);
        require(maxUnknownSideRatio.signum() >= 0 && maxUnknownSideRatio.compareTo(BigDecimal.ONE) <= 0,
                prefix + "maxUnknownSideRatio must be within [0, 1], got " + maxUnknownSideRatio.toPlainString());
    }

    public static FlowHorizonPolicy of(MarketHorizon horizon,
                                       BigDecimal bullishImbalanceThreshold,
                                       BigDecimal bearishImbalanceThreshold,
                                       int minTradeCount,
                                       int minAggressiveTradeCount,
                                       BigDecimal maxUnknownSideRatio) {
        return new FlowHorizonPolicy(horizon, bullishImbalanceThreshold, bearishImbalanceThreshold,
                minTradeCount, minAggressiveTradeCount, maxUnknownSideRatio);
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.math.BigDecimal;

/**
 * Momentum V1 heuristic parameters of <b>one</b> scoped {@link MarketHorizon} (5S, 15S or 60S). The
 * horizon is part of the value so a policy can never be filed under the wrong horizon and a
 * {@link MomentumAssessmentPolicy} can reject a duplicate or missing one. MFS v2 publishes no 1S
 * price change, so an H1S momentum policy is rejected here — a fictitious policy for an unscoped
 * horizon would suggest the evaluator could use it. Exact {@link BigDecimal} only — no {@code double}
 * (binary rounding, NaN and ±∞ have no place in a deterministic, replayable verdict).
 *
 * <p>Invariants: {@code bullishPriceChangeBpsThreshold > 0}, {@code bearishPriceChangeBpsThreshold < 0}
 * (a dead zone always exists around zero), {@code fullStrengthAbsMoveBps > 0},
 * {@code maxSafeAbsMoveBps > fullStrengthAbsMoveBps}, and full strength is only reached at or beyond
 * both directional thresholds ({@code fullStrengthAbsMoveBps >= bullishPriceChangeBpsThreshold} and
 * {@code >= abs(bearishPriceChangeBpsThreshold)}). Boundaries are inclusive on the directional side
 * and on the accepting side of the safe range ({@code abs(move) == maxSafeAbsMoveBps} is still
 * trusted).
 *
 * <p>These are uncalibrated heuristic thresholds, not probabilities, confidences or trading weights;
 * production values are a replay-driven decision of a later stage and never live inside the evaluator.
 */
public record MomentumHorizonPolicy(
        MarketHorizon horizon,
        BigDecimal bullishPriceChangeBpsThreshold,
        BigDecimal bearishPriceChangeBpsThreshold,
        BigDecimal fullStrengthAbsMoveBps,
        BigDecimal maxSafeAbsMoveBps) {

    public MomentumHorizonPolicy {
        requireNonNull(horizon, "momentum policy horizon");
        require(horizon != MarketHorizon.H1S,
                "momentum has no 1S evidence (no priceChangeBps1s in MFS v2); an H1S policy must not exist");
        String prefix = "momentum policy " + horizon.wireValue() + ".";
        requireNonNull(bullishPriceChangeBpsThreshold, prefix + "bullishPriceChangeBpsThreshold");
        requireNonNull(bearishPriceChangeBpsThreshold, prefix + "bearishPriceChangeBpsThreshold");
        requireNonNull(fullStrengthAbsMoveBps, prefix + "fullStrengthAbsMoveBps");
        requireNonNull(maxSafeAbsMoveBps, prefix + "maxSafeAbsMoveBps");
        require(bullishPriceChangeBpsThreshold.signum() > 0,
                prefix + "bullishPriceChangeBpsThreshold must be positive, got "
                        + bullishPriceChangeBpsThreshold.toPlainString());
        require(bearishPriceChangeBpsThreshold.signum() < 0,
                prefix + "bearishPriceChangeBpsThreshold must be negative, got "
                        + bearishPriceChangeBpsThreshold.toPlainString());
        require(fullStrengthAbsMoveBps.signum() > 0,
                prefix + "fullStrengthAbsMoveBps must be positive, got " + fullStrengthAbsMoveBps.toPlainString());
        require(maxSafeAbsMoveBps.compareTo(fullStrengthAbsMoveBps) > 0,
                prefix + "maxSafeAbsMoveBps must be strictly above fullStrengthAbsMoveBps");
        require(fullStrengthAbsMoveBps.compareTo(bullishPriceChangeBpsThreshold) >= 0,
                prefix + "fullStrengthAbsMoveBps must be at least bullishPriceChangeBpsThreshold");
        require(fullStrengthAbsMoveBps.compareTo(bearishPriceChangeBpsThreshold.abs()) >= 0,
                prefix + "fullStrengthAbsMoveBps must be at least abs(bearishPriceChangeBpsThreshold)");
    }

    public static MomentumHorizonPolicy of(MarketHorizon horizon,
                                           BigDecimal bullishPriceChangeBpsThreshold,
                                           BigDecimal bearishPriceChangeBpsThreshold,
                                           BigDecimal fullStrengthAbsMoveBps,
                                           BigDecimal maxSafeAbsMoveBps) {
        return new MomentumHorizonPolicy(horizon, bullishPriceChangeBpsThreshold, bearishPriceChangeBpsThreshold,
                fullStrengthAbsMoveBps, maxSafeAbsMoveBps);
    }
}

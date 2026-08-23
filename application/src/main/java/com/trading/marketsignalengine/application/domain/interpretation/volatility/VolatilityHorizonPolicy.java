package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.math.BigDecimal;

/**
 * Volatility V1 classification bounds of <b>one</b> {@link MarketHorizon}. The horizon is part of the
 * value so a policy can never be filed under the wrong horizon and a
 * {@link VolatilityAssessmentPolicy} can reject a duplicate or missing one. Exact {@link BigDecimal}
 * only — no {@code double}.
 *
 * <p>Invariants: {@code 0 <= lowUpperBoundBps < normalUpperBoundBps < highUpperBoundBps} — three
 * strictly ordered, non-negative upper bounds that carve the value axis into LOW / NORMAL / HIGH /
 * EXTREME with inclusive upper boundaries ({@code value <= bound} belongs to the lower band).
 *
 * <p>These are uncalibrated heuristic bounds, not a risk model; production values are a replay-driven
 * decision of a later stage and never live inside the evaluator.
 */
public record VolatilityHorizonPolicy(
        MarketHorizon horizon,
        BigDecimal lowUpperBoundBps,
        BigDecimal normalUpperBoundBps,
        BigDecimal highUpperBoundBps) {

    public VolatilityHorizonPolicy {
        requireNonNull(horizon, "volatility policy horizon");
        String prefix = "volatility policy " + horizon.wireValue() + ".";
        requireNonNull(lowUpperBoundBps, prefix + "lowUpperBoundBps");
        requireNonNull(normalUpperBoundBps, prefix + "normalUpperBoundBps");
        requireNonNull(highUpperBoundBps, prefix + "highUpperBoundBps");
        require(lowUpperBoundBps.signum() >= 0,
                prefix + "lowUpperBoundBps must not be negative, got " + lowUpperBoundBps.toPlainString());
        require(lowUpperBoundBps.compareTo(normalUpperBoundBps) < 0,
                prefix + "normalUpperBoundBps must be strictly above lowUpperBoundBps");
        require(normalUpperBoundBps.compareTo(highUpperBoundBps) < 0,
                prefix + "highUpperBoundBps must be strictly above normalUpperBoundBps");
    }

    public static VolatilityHorizonPolicy of(MarketHorizon horizon,
                                             BigDecimal lowUpperBoundBps,
                                             BigDecimal normalUpperBoundBps,
                                             BigDecimal highUpperBoundBps) {
        return new VolatilityHorizonPolicy(horizon, lowUpperBoundBps, normalUpperBoundBps, highUpperBoundBps);
    }
}

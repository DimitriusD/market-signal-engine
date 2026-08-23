package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityLevel;
import java.util.List;

/**
 * Pure, deterministic regime reducer for one <b>eligible</b> horizon, from typed inputs only: the
 * {@link VolatilityAssessment} (its typed {@link VolatilityLevel} — never parsed out of reason codes)
 * and the MOMENTUM evidence. Flow direction, Book direction, spread, the horizon direction and raw
 * snapshot fields deliberately take no part; every numeric threshold already lives in the volatility
 * and momentum policies, none is invented here. Non-eligible horizons never reach this resolver
 * (their regime is an absent {@code null}).
 *
 * <h2>Rules (first match wins)</h2>
 * <pre>
 *   volatility not AVAILABLE / level UNKNOWN → UNKNOWN   [HORIZON_REGIME_UNKNOWN]
 *   level HIGH or EXTREME                    → VOLATILE  [HORIZON_REGIME_VOLATILE]  (momentum ignored)
 *   level LOW    + directional Momentum      → TRENDING  [HORIZON_REGIME_TRENDING]
 *   level LOW    + non-directional Momentum  → QUIET     [HORIZON_REGIME_QUIET]     (incl. 1S not-scoped)
 *   level NORMAL + directional Momentum      → TRENDING  [HORIZON_REGIME_TRENDING]
 *   level NORMAL + AVAILABLE NEUTRAL Momentum→ RANGING   [HORIZON_REGIME_RANGING]
 *   level NORMAL + unknown / non-AVAILABLE   → UNKNOWN   [HORIZON_REGIME_UNKNOWN]
 * </pre>
 * {@code MarketRegime.UNKNOWN} means assessed but not classifiable — distinct from the {@code null}
 * regime of a non-eligible horizon.
 */
final class MarketRegimeResolver {

    private MarketRegimeResolver() {
    }

    /** Regime resolution of one eligible horizon from its typed VOLATILITY and MOMENTUM evidence. */
    static MarketRegimeResolution resolve(VolatilityAssessment volatility, EvidenceAssessment momentum) {
        requireNonNull(volatility, "volatility assessment");
        requireNonNull(momentum, "momentum evidence");
        require(momentum.dimension() == EvidenceDimension.MOMENTUM,
                "regime resolver expected MOMENTUM evidence, got " + momentum.dimension());

        VolatilityLevel level = volatility.level();
        if (!volatility.isAvailable() || !level.isClassified()) {
            return unknown();
        }
        if (level == VolatilityLevel.HIGH || level == VolatilityLevel.EXTREME) {
            return new MarketRegimeResolution(MarketRegime.VOLATILE,
                    List.of(HorizonReasonCodes.HORIZON_REGIME_VOLATILE));
        }
        boolean momentumDirectional = momentum.isAvailable() && momentum.direction().isDirectional();
        if (momentumDirectional) {
            return new MarketRegimeResolution(MarketRegime.TRENDING,
                    List.of(HorizonReasonCodes.HORIZON_REGIME_TRENDING));
        }
        if (level == VolatilityLevel.LOW) {
            // low volatility without a directional move is a quiet market — even when momentum is
            // not scoped (1S) or otherwise not available
            return new MarketRegimeResolution(MarketRegime.QUIET,
                    List.of(HorizonReasonCodes.HORIZON_REGIME_QUIET));
        }
        // NORMAL volatility: a real interpreted neutral is ranging; anything else is not classifiable
        if (momentum.isAvailable() && momentum.direction() == InterpretationDirection.NEUTRAL) {
            return new MarketRegimeResolution(MarketRegime.RANGING,
                    List.of(HorizonReasonCodes.HORIZON_REGIME_RANGING));
        }
        return unknown();
    }

    private static MarketRegimeResolution unknown() {
        return new MarketRegimeResolution(MarketRegime.UNKNOWN,
                List.of(HorizonReasonCodes.HORIZON_REGIME_UNKNOWN));
    }
}

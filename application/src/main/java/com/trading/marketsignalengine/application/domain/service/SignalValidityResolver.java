package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalValidity;
import java.time.Instant;

/**
 * Resolves how long a signal snapshot stays valid. A directional microstructure setup expires fast
 * (TTL from {@code microstructureSetupTtlMs}); a no-trade/risk-off snapshot can be held longer
 * ({@code riskOffTtlMs}); a neutral snapshot without an actionable setup is short-lived
 * ({@code neutralTtlMs}). {@code validUntil} is always {@code evaluatedAt + ttl}.
 */
public class SignalValidityResolver {

    public SignalValidity resolve(SignalEvaluationContext context, RiskLevel riskLevel, MarketSetup setup) {
        long ttlMs;

        if (riskLevel == RiskLevel.NO_TRADE) {
            ttlMs = context.configuration().riskOffTtlMs();
        } else if (setup != null && (setup.side() == SetupSide.LONG || setup.side() == SetupSide.SHORT)) {
            ttlMs = context.configuration().microstructureSetupTtlMs();
        } else {
            ttlMs = context.configuration().neutralTtlMs();
        }

        Instant validUntil = context.evaluatedAt().plusMillis(ttlMs);
        return new SignalValidity(ttlMs, validUntil);
    }
}

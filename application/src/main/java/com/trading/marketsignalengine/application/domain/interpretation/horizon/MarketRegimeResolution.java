package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.Invariants;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * Internal, immutable result of {@link MarketRegimeResolver} for one <b>eligible</b> horizon: the
 * typed regime and exactly one regime reason. {@link MarketRegime#UNKNOWN} means "assessed but not
 * classifiable"; an absent ({@code null}) regime is reserved for non-eligible horizons, which never
 * reach the resolver, so {@code null} is forbidden here.
 */
record MarketRegimeResolution(
        MarketRegime regime,
        List<ReasonCode> reasonCodes) {

    MarketRegimeResolution {
        requireNonNull(regime, "regime resolution regime (null regime is a non-eligible horizon, not a resolution)");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "regime resolution reasonCodes");
        require(reasonCodes.size() == 1,
                "a regime resolution carries exactly one regime reason, got " + reasonCodes);
    }
}

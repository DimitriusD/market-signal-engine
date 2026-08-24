package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;

/**
 * Versioned, immutable aggregate policy of one full opportunity evaluation: the Stage 7
 * {@link CrossHorizonInterpretationPolicy} bundled under one {@code policyVersion} (non-blank, not a
 * placeholder), because an opportunity is only reproducible together with every parameter that
 * produced its cross-horizon evaluation. {@code allowVolatileMomentumContinuation} is the single
 * explicit switch of this stage: whether a {@link MarketRegime#VOLATILE} regime may still produce a
 * momentum-continuation candidate — high volatility is not an automatic hard block, but the decision
 * must be explicit and versioned. Deliberately no numeric weights, minimum confidence, probability /
 * expected-return thresholds or cost assumptions — those require replay calibration. No production
 * defaults and no Spring configuration at this stage.
 */
public record OpportunityInterpretationPolicy(
        String policyVersion,
        CrossHorizonInterpretationPolicy crossHorizonPolicy,
        boolean allowVolatileMomentumContinuation) {

    public OpportunityInterpretationPolicy {
        requireNotPlaceholder(policyVersion, "opportunity policyVersion");
        requireNonNull(crossHorizonPolicy, "opportunity crossHorizonPolicy");
    }
}

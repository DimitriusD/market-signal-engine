package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentPolicy;

/**
 * Versioned, immutable aggregate policy of one full per-horizon evaluation: the four evidence
 * policies bundled under one {@code policyVersion} (non-blank, not a placeholder), because a horizon
 * assessment is only reproducible together with <em>all</em> the parameters that produced it. Pure
 * composition — no threshold is copied out of the nested policies and none is added here; the
 * horizon-level resolution itself is threshold-free (it reads only typed evidence). No production
 * defaults and no Spring configuration at this stage.
 */
public record HorizonInterpretationPolicy(
        String policyVersion,
        FlowAssessmentPolicy flowPolicy,
        MomentumAssessmentPolicy momentumPolicy,
        VolatilityAssessmentPolicy volatilityPolicy,
        BookAssessmentPolicy bookPolicy) {

    public HorizonInterpretationPolicy {
        requireNotPlaceholder(policyVersion, "horizon policyVersion");
        requireNonNull(flowPolicy, "horizon flowPolicy");
        requireNonNull(momentumPolicy, "horizon momentumPolicy");
        requireNonNull(volatilityPolicy, "horizon volatilityPolicy");
        requireNonNull(bookPolicy, "horizon bookPolicy");
    }
}

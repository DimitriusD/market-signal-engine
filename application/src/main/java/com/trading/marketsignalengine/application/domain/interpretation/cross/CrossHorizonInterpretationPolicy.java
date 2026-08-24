package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;

/**
 * Versioned, immutable aggregate policy of one full cross-horizon evaluation: the Stage 6
 * {@link HorizonInterpretationPolicy} bundled under one {@code policyVersion} (non-blank, not a
 * placeholder), because a cross-horizon assessment is only reproducible together with every parameter
 * that produced its horizon assessments. Deliberately no numeric weights, voting thresholds or
 * configurable horizon priorities — the H60S→H15S→H5S hierarchy with H1S as micro-context is fixed
 * semantics of the algorithm version, not a set of coefficients. No production defaults and no Spring
 * configuration at this stage.
 */
public record CrossHorizonInterpretationPolicy(
        String policyVersion,
        HorizonInterpretationPolicy horizonPolicy) {

    public CrossHorizonInterpretationPolicy {
        requireNotPlaceholder(policyVersion, "cross-horizon policyVersion");
        requireNonNull(horizonPolicy, "cross-horizon horizonPolicy");
    }
}

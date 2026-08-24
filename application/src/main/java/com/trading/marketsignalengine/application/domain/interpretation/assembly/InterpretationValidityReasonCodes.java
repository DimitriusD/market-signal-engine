package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of validity resolution: codes only the Stage 9 validity layer can add on
 * top of the Stage 8 opportunity codes. Deliberately minimal; no free-form strings in the resolver.
 */
public final class InterpretationValidityReasonCodes {

    private InterpretationValidityReasonCodes() {
    }

    /**
     * The candidate's validity deadline had already passed at the quality assessment instant
     * ({@code assessedAt >= candidate validUntil}, the deadline being exclusive), so the candidate was
     * downgraded to NO_OPPORTUNITY before assembly — an expired candidate never stays active.
     */
    public static final ReasonCode OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY =
            ReasonCode.of("OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY");

    /** Every validity code; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY);
}

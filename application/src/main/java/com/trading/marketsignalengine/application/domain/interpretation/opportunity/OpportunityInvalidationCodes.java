package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed invalidation taxonomy of a momentum-continuation CANDIDATE: <em>future</em> conditions
 * that would cancel a still-valid candidate — the mirror image of the gates that had to hold when it
 * was created. At creation time none of them is true (the resolver refuses the candidate otherwise);
 * a later evaluation that finds one true simply produces no candidate — there is no temporal
 * invalidator evaluator or state machine at this stage. BLOCKED / NO_OPPORTUNITY results carry no
 * invalidation codes ({@code MarketOpportunity} enforces that); the causes of a current negative
 * verdict belong to {@code reasonCodes}.
 */
public final class OpportunityInvalidationCodes {

    private OpportunityInvalidationCodes() {
    }

    /** The snapshot quality stops being {@code eligibleForTrading}. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_QUALITY = ReasonCode.of("OPPORTUNITY_INVALIDATE_QUALITY");
    /** The cross-horizon alignment stops being ALIGNED on the candidate direction. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_CROSS_HORIZON_ALIGNMENT =
            ReasonCode.of("OPPORTUNITY_INVALIDATE_CROSS_HORIZON_ALIGNMENT");
    /** The H15S MOMENTUM evidence stops confirming the candidate direction. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_H15_MOMENTUM =
            ReasonCode.of("OPPORTUNITY_INVALIDATE_H15_MOMENTUM");
    /** The H5S FLOW trigger stops confirming the candidate direction. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_H5_FLOW_TRIGGER =
            ReasonCode.of("OPPORTUNITY_INVALIDATE_H5_FLOW_TRIGGER");
    /** AVAILABLE Book evidence turns opposite to the candidate or MIXED. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_BOOK_CONTRADICTION =
            ReasonCode.of("OPPORTUNITY_INVALIDATE_BOOK_CONTRADICTION");
    /** The cross-horizon evidence strength becomes absent or zero. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_STRENGTH = ReasonCode.of("OPPORTUNITY_INVALIDATE_STRENGTH");
    /** The cross-horizon regime stops being continuation-compatible under the policy. */
    public static final ReasonCode OPPORTUNITY_INVALIDATE_REGIME = ReasonCode.of("OPPORTUNITY_INVALIDATE_REGIME");

    /**
     * Every invalidation code in deterministic gate order (quality → alignment → H15S momentum → H5S
     * flow → book → strength → regime); unmodifiable, duplicate-free. This is exactly the list every
     * CANDIDATE carries.
     */
    public static final List<ReasonCode> ALL = List.of(
            OPPORTUNITY_INVALIDATE_QUALITY,
            OPPORTUNITY_INVALIDATE_CROSS_HORIZON_ALIGNMENT,
            OPPORTUNITY_INVALIDATE_H15_MOMENTUM,
            OPPORTUNITY_INVALIDATE_H5_FLOW_TRIGGER,
            OPPORTUNITY_INVALIDATE_BOOK_CONTRADICTION,
            OPPORTUNITY_INVALIDATE_STRENGTH,
            OPPORTUNITY_INVALIDATE_REGIME);
}

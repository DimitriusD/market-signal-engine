package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.List;

/**
 * Interpreted market opportunity (contract: {@code MarketOpportunityEvent}). It is an interpretation
 * of the market for a downstream strategy — <b>not</b> an order, not an execution instruction, not a
 * position/size/stop, not a forecast of profit. A CANDIDATE is an input for strategy evaluation.
 * Enforced table:
 * <pre>
 *   CANDIDATE       → side LONG | SHORT; type is a real pattern (not NONE / UNKNOWN); setupHorizon present;
 *                     evidenceStrength optional (valid when present); invalidationCodes allowed
 *   NO_OPPORTUNITY  → side NONE; type NONE; setupHorizon absent; evidenceStrength absent; invalidationCodes empty
 *   BLOCKED         → side NONE; type NONE; setupHorizon absent; evidenceStrength absent; invalidationCodes empty
 *   UNKNOWN         → side NONE; type NONE | UNKNOWN; setupHorizon absent; evidenceStrength absent; invalidationCodes empty
 * </pre>
 * {@code invalidationCodes} are future conditions that would cancel a CANDIDATE while still valid;
 * the causes of a current block / negative result belong to {@code reasonCodes}. Consistency with
 * {@link InterpretationQuality} (CANDIDATE / NO_OPPORTUNITY only when {@code eligibleForTrading}) is
 * enforced by {@link MarketInterpretationSnapshot}.
 */
public record MarketOpportunity(
        OpportunityStatus status,
        OpportunityType type,
        OpportunitySide side,
        MarketHorizon setupHorizon,
        EvidenceStrength evidenceStrength,
        List<ReasonCode> reasonCodes,
        List<ReasonCode> invalidationCodes) {

    public MarketOpportunity {
        requireNonNull(status, "opportunity.status");
        requireNonNull(type, "opportunity.type");
        requireNonNull(side, "opportunity.side");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "opportunity.reasonCodes");
        invalidationCodes = Invariants.reasonCodes(invalidationCodes, "opportunity.invalidationCodes");

        switch (status) {
            case CANDIDATE -> {
                require(side.isDirectional(), "opportunity CANDIDATE requires side LONG or SHORT, got " + side);
                require(type.isPattern(), "opportunity CANDIDATE requires a real opportunity type, got " + type);
                require(setupHorizon != null, "opportunity CANDIDATE requires a setupHorizon");
            }
            case NO_OPPORTUNITY, BLOCKED -> {
                requireNone(status, side, type, setupHorizon, evidenceStrength, invalidationCodes);
                require(type == OpportunityType.NONE, "opportunity " + status + " requires type NONE, got " + type);
            }
            case UNKNOWN -> {
                requireNone(status, side, type, setupHorizon, evidenceStrength, invalidationCodes);
                require(type == OpportunityType.NONE || type == OpportunityType.UNKNOWN,
                        "opportunity UNKNOWN requires type NONE or UNKNOWN, got " + type);
            }
        }
    }

    public boolean isCandidate() {
        return status == OpportunityStatus.CANDIDATE;
    }

    // ------------------------------------------------------------------ factories

    /** An identified opportunity pattern — a candidate for strategy evaluation, not a command. */
    public static MarketOpportunity candidate(OpportunityType type,
                                              OpportunitySide side,
                                              MarketHorizon setupHorizon,
                                              EvidenceStrength evidenceStrength,
                                              List<ReasonCode> reasonCodes,
                                              List<ReasonCode> invalidationCodes) {
        return new MarketOpportunity(OpportunityStatus.CANDIDATE, type, side, setupHorizon, evidenceStrength,
                reasonCodes, invalidationCodes);
    }

    /** The engine was allowed to search and found nothing — an honest negative result. */
    public static MarketOpportunity noOpportunity(List<ReasonCode> reasonCodes) {
        return new MarketOpportunity(OpportunityStatus.NO_OPPORTUNITY, OpportunityType.NONE, OpportunitySide.NONE,
                null, null, reasonCodes, List.of());
    }

    /** The engine was not allowed to use the snapshot for a trading decision. */
    public static MarketOpportunity blocked(List<ReasonCode> reasonCodes) {
        return new MarketOpportunity(OpportunityStatus.BLOCKED, OpportunityType.NONE, OpportunitySide.NONE,
                null, null, reasonCodes, List.of());
    }

    /** Fallback. */
    public static MarketOpportunity unknown(List<ReasonCode> reasonCodes) {
        return new MarketOpportunity(OpportunityStatus.UNKNOWN, OpportunityType.UNKNOWN, OpportunitySide.NONE,
                null, null, reasonCodes, List.of());
    }

    // ------------------------------------------------------------------ helpers

    private static void requireNone(OpportunityStatus status, OpportunitySide side, OpportunityType type,
                                    MarketHorizon setupHorizon, EvidenceStrength evidenceStrength,
                                    List<ReasonCode> invalidationCodes) {
        require(side == OpportunitySide.NONE, "opportunity " + status + " requires side NONE, got " + side);
        require(setupHorizon == null, "opportunity " + status + " must not carry a setupHorizon");
        require(evidenceStrength == null, "opportunity " + status + " must not carry an evidence strength");
        require(invalidationCodes.isEmpty(),
                "opportunity " + status + " must not carry invalidationCodes (use reasonCodes), got " + invalidationCodes);
    }
}

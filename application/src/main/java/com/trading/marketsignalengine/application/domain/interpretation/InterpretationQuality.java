package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import java.util.List;

/**
 * Overall quality verdict of one interpretation snapshot (contract: {@code InterpretationQualityEvent}).
 * Enforced status ↔ eligibility table:
 * <pre>
 *   OK        → eligibleForTrading = true
 *   DEGRADED  → eligibleForTrading = true | false   (engine policy, decided by a later quality policy)
 *   BLOCKED   → eligibleForTrading = false
 *   NO_DATA   → eligibleForTrading = false
 *   UNKNOWN   → eligibleForTrading = false
 * </pre>
 * The model never promotes DEGRADED to BLOCKED on its own; the consistency with
 * {@link MarketOpportunity} is enforced by {@link MarketInterpretationSnapshot}.
 */
public record InterpretationQuality(
        InterpretationQualityStatus status,
        boolean eligibleForTrading,
        List<ReasonCode> reasonCodes) {

    public InterpretationQuality {
        requireNonNull(status, "status");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "quality.reasonCodes");
        switch (status) {
            case OK -> require(eligibleForTrading, "quality OK requires eligibleForTrading = true");
            case DEGRADED -> { /* policy-dependent: both values are legal */ }
            case BLOCKED, NO_DATA, UNKNOWN -> require(!eligibleForTrading,
                    "quality " + status + " requires eligibleForTrading = false");
        }
    }

    public static InterpretationQuality ok(List<ReasonCode> reasonCodes) {
        return new InterpretationQuality(InterpretationQualityStatus.OK, true, reasonCodes);
    }

    public static InterpretationQuality degraded(boolean eligibleForTrading, List<ReasonCode> reasonCodes) {
        return new InterpretationQuality(InterpretationQualityStatus.DEGRADED, eligibleForTrading, reasonCodes);
    }

    public static InterpretationQuality blocked(List<ReasonCode> reasonCodes) {
        return new InterpretationQuality(InterpretationQualityStatus.BLOCKED, false, reasonCodes);
    }

    public static InterpretationQuality noData(List<ReasonCode> reasonCodes) {
        return new InterpretationQuality(InterpretationQualityStatus.NO_DATA, false, reasonCodes);
    }

    public static InterpretationQuality unknown(List<ReasonCode> reasonCodes) {
        return new InterpretationQuality(InterpretationQualityStatus.UNKNOWN, false, reasonCodes);
    }
}

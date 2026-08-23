package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import java.util.List;

/**
 * Eligibility verdict for one interpretation horizon (contract: {@code HorizonEligibilityEvent}).
 * {@code ELIGIBLE} means the horizon may be used; WARMING_UP / UNAVAILABLE / UNTRUSTED / FAILED /
 * UNKNOWN mean the horizon takes no part in interpretation — it is reported with that status and is
 * never replaced by a neutral reading. Reason codes explain the verdict (usually empty for ELIGIBLE).
 */
public record HorizonEligibility(
        HorizonEligibilityStatus status,
        List<ReasonCode> reasonCodes) {

    public HorizonEligibility {
        requireNonNull(status, "eligibility.status");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "eligibility.reasonCodes");
    }

    public boolean isEligible() {
        return status.isEligible();
    }

    public static HorizonEligibility eligible(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.ELIGIBLE, reasonCodes);
    }

    public static HorizonEligibility eligible() {
        return eligible(List.of());
    }

    public static HorizonEligibility warmingUp(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.WARMING_UP, reasonCodes);
    }

    public static HorizonEligibility unavailable(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.UNAVAILABLE, reasonCodes);
    }

    public static HorizonEligibility untrusted(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.UNTRUSTED, reasonCodes);
    }

    public static HorizonEligibility failed(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.FAILED, reasonCodes);
    }

    public static HorizonEligibility unknown(List<ReasonCode> reasonCodes) {
        return new HorizonEligibility(HorizonEligibilityStatus.UNKNOWN, reasonCodes);
    }
}

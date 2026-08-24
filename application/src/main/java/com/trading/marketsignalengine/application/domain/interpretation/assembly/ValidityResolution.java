package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import java.time.Instant;

/**
 * Package-private result of one {@link InterpretationValidityResolver#resolve}: the exclusive
 * {@code validUntil} deadline of the final snapshot, the opportunity that may actually be published
 * ({@code effectiveOpportunity} — the original one, or its NO_OPPORTUNITY downgrade when the
 * candidate had already expired at assessment time), the remaining validity at the assessment
 * instant ({@code validUntil − assessedAt}, may be ≤ 0 for non-candidate verdicts) and whether an
 * expiration downgrade happened.
 */
record ValidityResolution(
        Instant validUntil,
        MarketOpportunity effectiveOpportunity,
        long remainingValidityMs,
        boolean candidateExpired) {

    ValidityResolution {
        requireNonNull(validUntil, "validUntil");
        requireNonNull(effectiveOpportunity, "effectiveOpportunity");
        if (candidateExpired) {
            require(effectiveOpportunity.status() == OpportunityStatus.NO_OPPORTUNITY,
                    "an expired candidate must be downgraded to NO_OPPORTUNITY, got " + effectiveOpportunity.status());
        } else if (effectiveOpportunity.status() == OpportunityStatus.CANDIDATE) {
            require(remainingValidityMs > 0L,
                    "an active candidate requires remaining validity > 0 ms, got " + remainingValidityMs);
        }
    }
}

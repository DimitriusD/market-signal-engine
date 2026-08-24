package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.interpretation.InterpretationLineage;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInterpretationPolicy;

/**
 * Versioned, immutable aggregate policy of one full snapshot assembly: the interpretation lineage
 * identity plus the Stage 8 {@link OpportunityInterpretationPolicy} (which itself carries the
 * cross-horizon / horizon / evidence policies) and the Stage 9 {@link InterpretationValidityPolicy}.
 *
 * <p>{@code interpretationConfigHash} must be a <b>real</b> hash provided by the caller — the
 * assembler never invents one (no {@code hashCode()}, no serialization tricks; canonical config
 * hashing is a later stage). The caller is responsible for the hash covering every policy that
 * shaped the snapshot: opportunity, cross-horizon, horizon/evidence and validity. Two snapshots with
 * equal lineage must have been produced by identical configuration — a hash that silently omits a
 * policy breaks that guarantee. No production defaults and no Spring configuration at this stage.
 */
public record MarketInterpretationAssemblyPolicy(
        String interpretationVersion,
        String interpretationConfigHash,
        OpportunityInterpretationPolicy opportunityPolicy,
        InterpretationValidityPolicy validityPolicy) {

    public MarketInterpretationAssemblyPolicy {
        requireNotPlaceholder(interpretationVersion, "interpretationVersion");
        requireNotPlaceholder(interpretationConfigHash, "interpretationConfigHash");
        requireNonNull(opportunityPolicy, "opportunityPolicy");
        requireNonNull(validityPolicy, "validityPolicy");
    }

    /** The interpretation lineage of every snapshot assembled under this policy. */
    public InterpretationLineage interpretationLineage() {
        return new InterpretationLineage(interpretationVersion, interpretationConfigHash);
    }
}

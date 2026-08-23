package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.Invariants;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * Internal, immutable result of {@link HorizonDirectionResolver} for one eligible horizon: the
 * horizon-level direction, its optional strength and the horizon-level reason codes (directional
 * resolution first, then book context). Deliberately knows nothing about regime, eligibility or
 * volatility — those are separate concerns.
 *
 * <p>Invariants: direction non-null; reasons immutable and duplicate-free; {@code UNKNOWN} and
 * {@code MIXED} carry no strength; {@code NEUTRAL} carries a real computed
 * {@link EvidenceStrength#MIN}; a directional reading may carry a strength or {@code null} (a
 * strength is never invented — and a book contradiction drops it).
 */
record HorizonDirectionResolution(
        InterpretationDirection direction,
        EvidenceStrength evidenceStrength,
        List<ReasonCode> reasonCodes) {

    HorizonDirectionResolution {
        requireNonNull(direction, "direction resolution direction");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "direction resolution reasonCodes");
        require(!reasonCodes.isEmpty(), "a direction resolution must explain itself with at least one reason");
        switch (direction) {
            case UNKNOWN, MIXED -> require(evidenceStrength == null,
                    direction + " direction must not carry an evidence strength");
            case NEUTRAL -> require(EvidenceStrength.MIN.equals(evidenceStrength),
                    "NEUTRAL direction must carry a real 0 strength, got " + evidenceStrength);
            case BULLISH, BEARISH -> {
                // a directional reading keeps the primary evidence strength — possibly null, never invented
            }
        }
    }
}

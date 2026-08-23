package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic horizon-level direction reducer for one <b>eligible</b> horizon. Primary
 * directional evidence is FLOW + MOMENTUM (FLOW alone on 1S, where MFS v2 publishes no momentum);
 * BOOK is confirmation/context and can never create or reverse a direction; VOLATILITY takes no part
 * in direction at all. Typed evidence only — no reason-code parsing.
 *
 * <p>An evidence reading is a <em>directional vote</em> iff {@code AVAILABLE} with direction
 * BULLISH/BEARISH, and a <em>valid neutral</em> iff {@code AVAILABLE + NEUTRAL}. Anything else
 * (non-AVAILABLE, UNKNOWN, MIXED) is not a vote and never a neutral confirmation.
 *
 * <h2>Primary resolution, 5S/15S/60S</h2>
 * <pre>
 *   both directional, same side  → that side, strength = min(flow, momentum)   [CONFIRMED]
 *   both directional, opposite   → MIXED, no strength                          [DIVERGENCE]
 *   only Flow directional        → Flow side, Flow strength                    [FROM_FLOW]
 *   only Momentum directional    → Momentum side, Momentum strength            [FROM_MOMENTUM]
 *   both valid NEUTRAL           → NEUTRAL, real 0                             [NEUTRAL]
 *   anything else                → UNKNOWN, no strength                        [INSUFFICIENT]
 * </pre>
 * A divergence is reported, never converted into the opposite signal, a dominant side or a reversal.
 * A confirmed direction is never stronger than its weaker primary evidence; if a directional vote
 * carries no strength, the result stays directional with a {@code null} strength (nothing is
 * invented). No discount factors for unconfirmed directions.
 *
 * <h2>Primary resolution, 1S</h2>
 * Flow only: directional Flow → that side and strength [FROM_FLOW]; valid neutral Flow → NEUTRAL
 * [NEUTRAL]; otherwise UNKNOWN [INSUFFICIENT]. The 1S momentum evidence is UNAVAILABLE by design
 * (not scoped) and must not pull a valid Flow reading down to UNKNOWN.
 *
 * <h2>Book context (after primary resolution, directional result only)</h2>
 * AVAILABLE Book, same side → {@code HORIZON_BOOK_SUPPORTS_DIRECTION} (no strength bonus); AVAILABLE
 * Book, opposite side or MIXED → direction kept, strength dropped to {@code null},
 * {@code HORIZON_BOOK_CONTRADICTS_DIRECTION} (the conclusion becomes unconfirmed, never reversed);
 * AVAILABLE + NEUTRAL Book → {@code HORIZON_BOOK_NEUTRAL}. A non-AVAILABLE Book adds no
 * horizon-level code — its exact cause already lives in the nested book evidence. When the primary
 * resolution is not directional, Book adds nothing and never creates a direction.
 */
final class HorizonDirectionResolver {

    private HorizonDirectionResolver() {
    }

    /** Direction resolution of one eligible horizon from its typed FLOW / MOMENTUM / BOOK evidence. */
    static HorizonDirectionResolution resolve(MarketHorizon horizon,
                                              EvidenceAssessment flow,
                                              EvidenceAssessment momentum,
                                              EvidenceAssessment book) {
        requireNonNull(horizon, "horizon");
        requireDimension(flow, EvidenceDimension.FLOW);
        requireDimension(momentum, EvidenceDimension.MOMENTUM);
        requireDimension(book, EvidenceDimension.BOOK);

        HorizonDirectionResolution primary = horizon == MarketHorizon.H1S
                ? resolveFromFlowOnly(flow)
                : resolveFromFlowAndMomentum(flow, momentum);
        return applyBookContext(primary, book);
    }

    // ------------------------------------------------------------------ primary resolution

    private static HorizonDirectionResolution resolveFromFlowAndMomentum(EvidenceAssessment flow,
                                                                         EvidenceAssessment momentum) {
        boolean flowDirectional = isDirectionalVote(flow);
        boolean momentumDirectional = isDirectionalVote(momentum);
        if (flowDirectional && momentumDirectional) {
            if (flow.direction() == momentum.direction()) {
                return new HorizonDirectionResolution(flow.direction(),
                        weaker(flow.evidenceStrength(), momentum.evidenceStrength()),
                        List.of(HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_CONFIRMED));
            }
            return new HorizonDirectionResolution(InterpretationDirection.MIXED, null,
                    List.of(HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_DIVERGENCE));
        }
        if (flowDirectional) {
            return new HorizonDirectionResolution(flow.direction(), flow.evidenceStrength(),
                    List.of(HorizonReasonCodes.HORIZON_DIRECTION_FROM_FLOW));
        }
        if (momentumDirectional) {
            return new HorizonDirectionResolution(momentum.direction(), momentum.evidenceStrength(),
                    List.of(HorizonReasonCodes.HORIZON_DIRECTION_FROM_MOMENTUM));
        }
        if (isValidNeutral(flow) && isValidNeutral(momentum)) {
            return new HorizonDirectionResolution(InterpretationDirection.NEUTRAL, EvidenceStrength.MIN,
                    List.of(HorizonReasonCodes.HORIZON_DIRECTION_NEUTRAL));
        }
        return new HorizonDirectionResolution(InterpretationDirection.UNKNOWN, null,
                List.of(HorizonReasonCodes.HORIZON_DIRECTION_INSUFFICIENT));
    }

    private static HorizonDirectionResolution resolveFromFlowOnly(EvidenceAssessment flow) {
        if (isDirectionalVote(flow)) {
            return new HorizonDirectionResolution(flow.direction(), flow.evidenceStrength(),
                    List.of(HorizonReasonCodes.HORIZON_DIRECTION_FROM_FLOW));
        }
        if (isValidNeutral(flow)) {
            return new HorizonDirectionResolution(InterpretationDirection.NEUTRAL, EvidenceStrength.MIN,
                    List.of(HorizonReasonCodes.HORIZON_DIRECTION_NEUTRAL));
        }
        return new HorizonDirectionResolution(InterpretationDirection.UNKNOWN, null,
                List.of(HorizonReasonCodes.HORIZON_DIRECTION_INSUFFICIENT));
    }

    // ------------------------------------------------------------------ book context

    private static HorizonDirectionResolution applyBookContext(HorizonDirectionResolution primary,
                                                               EvidenceAssessment book) {
        if (!primary.direction().isDirectional() || !book.isAvailable()) {
            // Book never creates a direction; a non-AVAILABLE book explains itself in the nested evidence.
            return primary;
        }
        List<ReasonCode> reasons = new ArrayList<>(primary.reasonCodes());
        InterpretationDirection bookDirection = book.direction();
        if (bookDirection == primary.direction()) {
            reasons.add(HorizonReasonCodes.HORIZON_BOOK_SUPPORTS_DIRECTION);
            return new HorizonDirectionResolution(primary.direction(), primary.evidenceStrength(), reasons);
        }
        if (bookDirection.isDirectional() || bookDirection == InterpretationDirection.MIXED) {
            reasons.add(HorizonReasonCodes.HORIZON_BOOK_CONTRADICTS_DIRECTION);
            // the primary direction stands, but the conclusion is no longer confirmed — strength dropped
            return new HorizonDirectionResolution(primary.direction(), null, reasons);
        }
        if (bookDirection == InterpretationDirection.NEUTRAL) {
            reasons.add(HorizonReasonCodes.HORIZON_BOOK_NEUTRAL);
            return new HorizonDirectionResolution(primary.direction(), primary.evidenceStrength(), reasons);
        }
        // AVAILABLE + UNKNOWN book reading: no vote, no context code
        return primary;
    }

    // ------------------------------------------------------------------ helpers

    /** {@code AVAILABLE} with BULLISH/BEARISH — the only readings that count as a directional vote. */
    static boolean isDirectionalVote(EvidenceAssessment evidence) {
        return evidence.isAvailable() && evidence.direction().isDirectional();
    }

    /** {@code AVAILABLE + NEUTRAL} — an interpreted "no direction"; nothing else is a neutral confirmation. */
    static boolean isValidNeutral(EvidenceAssessment evidence) {
        return evidence.isAvailable() && evidence.direction() == InterpretationDirection.NEUTRAL;
    }

    /** The weaker of two directional strengths; {@code null} when either is absent (never invented). */
    static EvidenceStrength weaker(EvidenceStrength a, EvidenceStrength b) {
        if (a == null || b == null) {
            return null;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static void requireDimension(EvidenceAssessment evidence, EvidenceDimension expected) {
        requireNonNull(evidence, expected + " evidence");
        require(evidence.dimension() == expected,
                "direction resolver expected " + expected + " evidence, got " + evidence.dimension());
    }
}

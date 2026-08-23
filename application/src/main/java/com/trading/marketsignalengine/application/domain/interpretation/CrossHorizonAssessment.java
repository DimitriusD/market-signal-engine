package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.canonicalHorizons;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.List;

/**
 * Aggregated view across the horizon assessments of one snapshot (contract:
 * {@code CrossHorizonAssessmentEvent}). Only ELIGIBLE horizons participate — that is enforced by
 * {@link MarketInterpretationSnapshot}, which sees the assessments; this record enforces the local
 * consistency table:
 * <pre>
 *   ALIGNED_BULLISH    → direction BULLISH;  participating non-empty; conflicting empty
 *   ALIGNED_BEARISH    → direction BEARISH;  participating non-empty; conflicting empty
 *   PARTIALLY_ALIGNED  → direction BULLISH | BEARISH (a dominant direction exists); participating non-empty; conflicting empty
 *   CONFLICTING        → direction MIXED; participating ≥ 2; conflicting non-empty, a proper subset of participating
 *   NEUTRAL            → direction NEUTRAL; participating non-empty; conflicting empty; dominantHorizon absent
 *   INSUFFICIENT_DATA  → direction UNKNOWN; strength absent; dominantHorizon absent; conflicting empty
 *   UNKNOWN            → direction UNKNOWN; strength absent; dominantHorizon absent; conflicting empty
 * </pre>
 * plus: horizon lists are unique and canonical-ordered ({@code 1S, 5S, 15S, 60S}); conflicting ⊆
 * participating; a present {@code dominantHorizon} is participating and not conflicting.
 * No interpreter lives here — only the result model and its invariants.
 */
public record CrossHorizonAssessment(
        CrossHorizonAlignment alignment,
        InterpretationDirection direction,
        EvidenceStrength evidenceStrength,
        MarketHorizon dominantHorizon,
        List<MarketHorizon> participatingHorizons,
        List<MarketHorizon> conflictingHorizons,
        MarketRegime regime,
        List<ReasonCode> reasonCodes) {

    public CrossHorizonAssessment {
        requireNonNull(alignment, "crossHorizon.alignment");
        requireNonNull(direction, "crossHorizon.direction");
        participatingHorizons = canonicalHorizons(participatingHorizons, "crossHorizon.participatingHorizons");
        conflictingHorizons = canonicalHorizons(conflictingHorizons, "crossHorizon.conflictingHorizons");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "crossHorizon.reasonCodes");

        for (MarketHorizon horizon : conflictingHorizons) {
            require(participatingHorizons.contains(horizon),
                    "conflicting horizon " + horizon + " is not a participating horizon");
        }
        if (alignment != CrossHorizonAlignment.CONFLICTING) {
            require(conflictingHorizons.isEmpty(),
                    "conflictingHorizons must be empty for alignment " + alignment + ", got " + conflictingHorizons);
        }
        if (dominantHorizon != null) {
            require(participatingHorizons.contains(dominantHorizon),
                    "dominant horizon " + dominantHorizon + " is not a participating horizon");
            require(!conflictingHorizons.contains(dominantHorizon),
                    "dominant horizon " + dominantHorizon + " cannot be a conflicting horizon");
        }

        switch (alignment) {
            case ALIGNED_BULLISH -> {
                requireDirection(alignment, direction, InterpretationDirection.BULLISH);
                requireParticipants(alignment, participatingHorizons, 1);
            }
            case ALIGNED_BEARISH -> {
                requireDirection(alignment, direction, InterpretationDirection.BEARISH);
                requireParticipants(alignment, participatingHorizons, 1);
            }
            case PARTIALLY_ALIGNED -> {
                require(direction.isDirectional(),
                        "alignment PARTIALLY_ALIGNED requires a dominant direction BULLISH or BEARISH, got " + direction);
                requireParticipants(alignment, participatingHorizons, 1);
            }
            case CONFLICTING -> {
                requireDirection(alignment, direction, InterpretationDirection.MIXED);
                requireParticipants(alignment, participatingHorizons, 2);
                require(!conflictingHorizons.isEmpty(), "alignment CONFLICTING requires non-empty conflictingHorizons");
                require(conflictingHorizons.size() < participatingHorizons.size(),
                        "conflictingHorizons must be a proper subset of participatingHorizons (not every horizon can be opposite)");
            }
            case NEUTRAL -> {
                requireDirection(alignment, direction, InterpretationDirection.NEUTRAL);
                requireParticipants(alignment, participatingHorizons, 1);
                require(dominantHorizon == null, "alignment NEUTRAL has no dominant horizon");
            }
            case INSUFFICIENT_DATA, UNKNOWN -> {
                requireDirection(alignment, direction, InterpretationDirection.UNKNOWN);
                require(evidenceStrength == null, "alignment " + alignment + " must not carry an evidence strength");
                require(dominantHorizon == null, "alignment " + alignment + " has no dominant horizon");
            }
        }
    }

    public boolean isInconclusive() {
        return alignment.isInconclusive();
    }

    // ------------------------------------------------------------------ factories

    public static CrossHorizonAssessment insufficientData(List<MarketHorizon> participatingHorizons,
                                                          List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.INSUFFICIENT_DATA, InterpretationDirection.UNKNOWN,
                null, null, participatingHorizons, List.of(), null, reasonCodes);
    }

    public static CrossHorizonAssessment unknown(List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.UNKNOWN, InterpretationDirection.UNKNOWN,
                null, null, List.of(), List.of(), null, reasonCodes);
    }

    public static CrossHorizonAssessment alignedBullish(EvidenceStrength evidenceStrength,
                                                        MarketHorizon dominantHorizon,
                                                        List<MarketHorizon> participatingHorizons,
                                                        MarketRegime regime,
                                                        List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.ALIGNED_BULLISH, InterpretationDirection.BULLISH,
                evidenceStrength, dominantHorizon, participatingHorizons, List.of(), regime, reasonCodes);
    }

    public static CrossHorizonAssessment alignedBearish(EvidenceStrength evidenceStrength,
                                                        MarketHorizon dominantHorizon,
                                                        List<MarketHorizon> participatingHorizons,
                                                        MarketRegime regime,
                                                        List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.ALIGNED_BEARISH, InterpretationDirection.BEARISH,
                evidenceStrength, dominantHorizon, participatingHorizons, List.of(), regime, reasonCodes);
    }

    public static CrossHorizonAssessment partiallyAligned(InterpretationDirection dominantDirection,
                                                          EvidenceStrength evidenceStrength,
                                                          MarketHorizon dominantHorizon,
                                                          List<MarketHorizon> participatingHorizons,
                                                          MarketRegime regime,
                                                          List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.PARTIALLY_ALIGNED, dominantDirection,
                evidenceStrength, dominantHorizon, participatingHorizons, List.of(), regime, reasonCodes);
    }

    public static CrossHorizonAssessment conflicting(EvidenceStrength evidenceStrength,
                                                     MarketHorizon dominantHorizon,
                                                     List<MarketHorizon> participatingHorizons,
                                                     List<MarketHorizon> conflictingHorizons,
                                                     MarketRegime regime,
                                                     List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.CONFLICTING, InterpretationDirection.MIXED,
                evidenceStrength, dominantHorizon, participatingHorizons, conflictingHorizons, regime, reasonCodes);
    }

    public static CrossHorizonAssessment neutral(EvidenceStrength evidenceStrength,
                                                 List<MarketHorizon> participatingHorizons,
                                                 MarketRegime regime,
                                                 List<ReasonCode> reasonCodes) {
        return new CrossHorizonAssessment(CrossHorizonAlignment.NEUTRAL, InterpretationDirection.NEUTRAL,
                evidenceStrength, null, participatingHorizons, List.of(), regime, reasonCodes);
    }

    // ------------------------------------------------------------------ helpers

    private static void requireDirection(CrossHorizonAlignment alignment, InterpretationDirection actual,
                                         InterpretationDirection expected) {
        require(actual == expected, "alignment " + alignment + " requires direction " + expected + ", got " + actual);
    }

    private static void requireParticipants(CrossHorizonAlignment alignment, List<MarketHorizon> participating, int min) {
        require(participating.size() >= min,
                "alignment " + alignment + " requires at least " + min + " participating horizon(s), got " + participating);
    }
}

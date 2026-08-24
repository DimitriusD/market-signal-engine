package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H15_STRUCTURE_DOMINANT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H1_ADVERSE_CONTEXT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H1_SUPPORTS_CONTEXT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H5_TRIGGER_CONFIRMS;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H5_TRIGGER_CONTRADICTS;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_H60_CONTEXT_DOMINANT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_ALIGNED_BEARISH;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_ALIGNED_BULLISH;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_CONFLICTING;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_INSUFFICIENT_DATA;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_PARTIALLY_ALIGNED;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_FALLBACK;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_FROM_DOMINANT;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_UNKNOWN;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic hierarchical reduction of one snapshot's {@link HorizonAssessments} into one
 * {@link CrossHorizonAssessment}. Deliberately package-private: production code reaches it only
 * through {@link CrossHorizonAssessmentEvaluator}, which derives the assessments from one snapshot,
 * so manually assembled assessments from different snapshots can never be interpreted together.
 * Reads only typed fields (eligibility, direction, evidenceStrength, regime, horizon) — never the
 * per-horizon reason-code strings. No clock, no I/O, no mutation of the input.
 *
 * <h2>Fixed role hierarchy (algorithm semantics, not configuration)</h2>
 * <ul>
 *   <li><b>Structural horizons</b> — H60S (senior context), H15S (market structure), H5S (trigger).
 *       Only these can anchor, confirm or conflict.</li>
 *   <li><b>H1S</b> — micro/execution context: never an anchor, never a structural confirmation,
 *       never a conflicting horizon; it can only add a supportive reason or, when opposite/MIXED,
 *       downgrade a full alignment to PARTIALLY_ALIGNED and drop the aggregate strength.</li>
 * </ul>
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li><b>Participants</b> — every horizon that is ELIGIBLE with direction != UNKNOWN, canonical
 *       order. Non-eligible / UNKNOWN horizons act only as absent confirmation.</li>
 *   <li><b>Anchor</b> — the eligible directional H60S, else the eligible directional H15S, else
 *       none. Strength plays no part; H5S/H1S can never anchor. The anchor is the dominant horizon
 *       of every directional or conflicting verdict.</li>
 *   <li><b>No anchor</b> — NEUTRAL only when every participating structural horizon is NEUTRAL and
 *       at least one of H60S/H15S participates; otherwise INSUFFICIENT_DATA (neutral H5S/H1S alone
 *       cannot conclude a cross-horizon neutral).</li>
 *   <li><b>Conflict</b> — any other structural participant opposite to the anchor or MIXED ⇒
 *       CONFLICTING (direction MIXED, strength null, conflicts listed, anchor never among them).
 *       Conflict has priority over any partial/aligned conclusion.</li>
 *   <li><b>Confirmation</b> — a directional conclusion needs at least two structural horizons on the
 *       anchor direction (the anchor counts); one alone ⇒ INSUFFICIENT_DATA. H1S never counts.</li>
 *   <li><b>Alignment</b> — all three structural horizons directional on the anchor direction and no
 *       adverse H1S ⇒ ALIGNED_*; otherwise PARTIALLY_ALIGNED with the anchor direction.</li>
 *   <li><b>Strength</b> — the minimum strength over the confirming structural horizons; null if any
 *       of them has none, if H1S is adverse, or for CONFLICTING / INSUFFICIENT_DATA; NEUTRAL is a
 *       real {@link EvidenceStrength#MIN}. Never averaged, weighted or boosted by H1S.</li>
 *   <li><b>Regime</b> — the dominant horizon's usable (non-null, non-UNKNOWN) regime, else the first
 *       usable regime among eligible participants in role order H60S→H15S→H5S→H1S, else
 *       {@link MarketRegime#UNKNOWN}; {@code null} only for INSUFFICIENT_DATA. Never re-derived from
 *       raw volatility/momentum and never voted.</li>
 * </ol>
 * Reason codes are emitted in the deterministic order documented on {@link CrossHorizonReasonCodes}.
 * {@code CrossHorizonAlignment.UNKNOWN} is never produced for valid typed input.
 */
final class CrossHorizonInterpreter {

    /** Structural seniority for regime fallback and reporting: H60S → H15S → H5S → H1S. */
    private static final List<MarketHorizon> REGIME_ROLE_ORDER =
            List.of(MarketHorizon.H60S, MarketHorizon.H15S, MarketHorizon.H5S, MarketHorizon.H1S);

    CrossHorizonAssessment interpret(HorizonAssessments assessments) {
        requireNonNull(assessments, "horizon assessments");
        List<MarketHorizon> participants = participants(assessments);
        MarketHorizon anchor = anchor(assessments);
        if (anchor == null) {
            return withoutAnchor(assessments, participants);
        }
        InterpretationDirection anchorDirection = assessments.of(anchor).direction();
        List<MarketHorizon> conflicting = structuralConflicts(assessments, anchor, anchorDirection);
        if (!conflicting.isEmpty()) {
            return conflicting(assessments, participants, anchor, anchorDirection, conflicting);
        }
        List<MarketHorizon> confirming = structuralConfirmations(assessments, anchorDirection);
        if (confirming.size() < 2) {
            return CrossHorizonAssessment.insufficientData(participants, List.of(
                    CROSS_HORIZON_INSUFFICIENT_DATA, CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION));
        }
        return alignedOrPartiallyAligned(assessments, participants, anchor, anchorDirection, confirming);
    }

    // ------------------------------------------------------------------ participation and anchor

    private static List<MarketHorizon> participants(HorizonAssessments assessments) {
        List<MarketHorizon> participants = new ArrayList<>(4);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonAssessment assessment = assessments.of(horizon);
            if (assessment.isEligible() && assessment.direction() != InterpretationDirection.UNKNOWN) {
                participants.add(horizon);
            }
        }
        return participants;
    }

    /** H60S if eligible and directional, else H15S; H5S/H1S never anchor; strength plays no part. */
    private static MarketHorizon anchor(HorizonAssessments assessments) {
        if (isDirectionalParticipant(assessments, MarketHorizon.H60S)) {
            return MarketHorizon.H60S;
        }
        if (isDirectionalParticipant(assessments, MarketHorizon.H15S)) {
            return MarketHorizon.H15S;
        }
        return null;
    }

    private static boolean isDirectionalParticipant(HorizonAssessments assessments, MarketHorizon horizon) {
        HorizonAssessment assessment = assessments.of(horizon);
        return assessment.isEligible() && assessment.direction().isDirectional();
    }

    private static boolean isStructural(MarketHorizon horizon) {
        return horizon != MarketHorizon.H1S;
    }

    // ------------------------------------------------------------------ no directional anchor

    private static CrossHorizonAssessment withoutAnchor(HorizonAssessments assessments,
                                                        List<MarketHorizon> participants) {
        boolean structuralAllNeutral = true;
        boolean seniorNeutral = false;
        for (MarketHorizon horizon : participants) {
            if (!isStructural(horizon)) {
                continue;
            }
            if (assessments.of(horizon).direction() != InterpretationDirection.NEUTRAL) {
                structuralAllNeutral = false;
            } else if (horizon == MarketHorizon.H60S || horizon == MarketHorizon.H15S) {
                seniorNeutral = true;
            }
        }
        if (structuralAllNeutral && seniorNeutral) {
            RegimeChoice regime = fallbackRegime(assessments, participants);
            return CrossHorizonAssessment.neutral(EvidenceStrength.MIN, participants, regime.regime(),
                    List.of(CROSS_HORIZON_NEUTRAL, CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR, regime.reasonCode()));
        }
        return CrossHorizonAssessment.insufficientData(participants,
                List.of(CROSS_HORIZON_INSUFFICIENT_DATA, CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR));
    }

    // ------------------------------------------------------------------ structural conflict

    /** Structural participants (never the anchor, never H1S) that are opposite to the anchor or MIXED. */
    private static List<MarketHorizon> structuralConflicts(HorizonAssessments assessments,
                                                           MarketHorizon anchor,
                                                           InterpretationDirection anchorDirection) {
        List<MarketHorizon> conflicts = new ArrayList<>(2);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            if (!isStructural(horizon) || horizon == anchor) {
                continue;
            }
            InterpretationDirection direction = assessments.of(horizon).direction();
            if (direction == opposite(anchorDirection) || direction == InterpretationDirection.MIXED) {
                conflicts.add(horizon);
            }
        }
        return conflicts;
    }

    private static CrossHorizonAssessment conflicting(HorizonAssessments assessments,
                                                      List<MarketHorizon> participants,
                                                      MarketHorizon anchor,
                                                      InterpretationDirection anchorDirection,
                                                      List<MarketHorizon> conflicting) {
        RegimeChoice regime = dominantFirstRegime(assessments, anchor, participants);
        List<ReasonCode> reasons = new ArrayList<>(4);
        reasons.add(CROSS_HORIZON_CONFLICTING);
        reasons.add(dominantCode(anchor));
        if (conflicting.contains(MarketHorizon.H5S)) {
            reasons.add(CROSS_H5_TRIGGER_CONTRADICTS);
        } else if (assessments.of(MarketHorizon.H5S).direction() == anchorDirection) {
            reasons.add(CROSS_H5_TRIGGER_CONFIRMS);
        }
        reasons.add(regime.reasonCode());
        return CrossHorizonAssessment.conflicting(null, anchor, participants, conflicting, regime.regime(), reasons);
    }

    // ------------------------------------------------------------------ aligned / partially aligned

    /** Structural horizons directional on the anchor direction (the anchor included); H1S never counts. */
    private static List<MarketHorizon> structuralConfirmations(HorizonAssessments assessments,
                                                               InterpretationDirection anchorDirection) {
        List<MarketHorizon> confirming = new ArrayList<>(3);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            if (isStructural(horizon) && assessments.of(horizon).direction() == anchorDirection) {
                confirming.add(horizon);
            }
        }
        return confirming;
    }

    private static CrossHorizonAssessment alignedOrPartiallyAligned(HorizonAssessments assessments,
                                                                    List<MarketHorizon> participants,
                                                                    MarketHorizon anchor,
                                                                    InterpretationDirection anchorDirection,
                                                                    List<MarketHorizon> confirming) {
        InterpretationDirection h1Direction = assessments.of(MarketHorizon.H1S).direction();
        boolean h1Adverse = h1Direction == opposite(anchorDirection) || h1Direction == InterpretationDirection.MIXED;
        boolean h1Supports = h1Direction == anchorDirection;
        boolean fullAlignment = confirming.size() == 3 && !h1Adverse;
        EvidenceStrength strength = h1Adverse ? null : minimumStrength(assessments, confirming);
        RegimeChoice regime = dominantFirstRegime(assessments, anchor, participants);

        List<ReasonCode> reasons = new ArrayList<>(5);
        reasons.add(fullAlignment
                ? (anchorDirection == InterpretationDirection.BULLISH
                        ? CROSS_HORIZON_ALIGNED_BULLISH : CROSS_HORIZON_ALIGNED_BEARISH)
                : CROSS_HORIZON_PARTIALLY_ALIGNED);
        reasons.add(dominantCode(anchor));
        if (confirming.contains(MarketHorizon.H5S)) {
            reasons.add(CROSS_H5_TRIGGER_CONFIRMS);
        }
        if (h1Supports) {
            reasons.add(CROSS_H1_SUPPORTS_CONTEXT);
        } else if (h1Adverse) {
            reasons.add(CROSS_H1_ADVERSE_CONTEXT);
        }
        reasons.add(regime.reasonCode());

        if (fullAlignment) {
            return anchorDirection == InterpretationDirection.BULLISH
                    ? CrossHorizonAssessment.alignedBullish(strength, anchor, participants, regime.regime(), reasons)
                    : CrossHorizonAssessment.alignedBearish(strength, anchor, participants, regime.regime(), reasons);
        }
        return CrossHorizonAssessment.partiallyAligned(anchorDirection, strength, anchor, participants,
                regime.regime(), reasons);
    }

    // ------------------------------------------------------------------ strength aggregation

    /** Minimum over the confirming structural strengths; null as soon as any confirmation has none. */
    private static EvidenceStrength minimumStrength(HorizonAssessments assessments,
                                                    List<MarketHorizon> confirming) {
        EvidenceStrength minimum = null;
        for (MarketHorizon horizon : confirming) {
            EvidenceStrength strength = assessments.of(horizon).evidenceStrength();
            if (strength == null) {
                return null;
            }
            if (minimum == null || strength.compareTo(minimum) < 0) {
                minimum = strength;
            }
        }
        return minimum;
    }

    // ------------------------------------------------------------------ regime selection

    private record RegimeChoice(MarketRegime regime, ReasonCode reasonCode) {
    }

    private static RegimeChoice dominantFirstRegime(HorizonAssessments assessments,
                                                    MarketHorizon dominant,
                                                    List<MarketHorizon> participants) {
        MarketRegime dominantRegime = assessments.of(dominant).regime();
        if (usable(dominantRegime)) {
            return new RegimeChoice(dominantRegime, CROSS_HORIZON_REGIME_FROM_DOMINANT);
        }
        return fallbackRegime(assessments, participants);
    }

    /** First usable regime among the eligible participants in role order; never a non-participant. */
    private static RegimeChoice fallbackRegime(HorizonAssessments assessments,
                                               List<MarketHorizon> participants) {
        for (MarketHorizon horizon : REGIME_ROLE_ORDER) {
            if (!participants.contains(horizon)) {
                continue;
            }
            MarketRegime regime = assessments.of(horizon).regime();
            if (usable(regime)) {
                return new RegimeChoice(regime, CROSS_HORIZON_REGIME_FALLBACK);
            }
        }
        return new RegimeChoice(MarketRegime.UNKNOWN, CROSS_HORIZON_REGIME_UNKNOWN);
    }

    private static boolean usable(MarketRegime regime) {
        return regime != null && regime != MarketRegime.UNKNOWN;
    }

    // ------------------------------------------------------------------ helpers

    private static ReasonCode dominantCode(MarketHorizon anchor) {
        return anchor == MarketHorizon.H60S ? CROSS_H60_CONTEXT_DOMINANT : CROSS_H15_STRUCTURE_DOMINANT;
    }

    private static InterpretationDirection opposite(InterpretationDirection direction) {
        return direction == InterpretationDirection.BULLISH
                ? InterpretationDirection.BEARISH : InterpretationDirection.BULLISH;
    }
}

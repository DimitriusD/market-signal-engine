package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.assessments;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.bearish;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.bullish;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.eligible;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.mixed;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.neutral;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.unavailable;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.unknownDirection;
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
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAlignment;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Stage 7 hierarchy on hand-built typed assessments: anchor selection (H60S over H15S, never
 * H5S/H1S, strength-blind), minimum two-structural confirmation, full/partial alignment, structural
 * conflicts, the H1S micro-context role, neutral vs insufficient, minimum-strength aggregation,
 * senior-first regime provenance and determinism. Reason-code lists are asserted exactly — their
 * order is part of the contract.
 */
class CrossHorizonInterpreterTest {

    private final CrossHorizonInterpreter interpreter = new CrossHorizonInterpreter();

    private CrossHorizonAssessment interpret(HorizonAssessments assessments) {
        CrossHorizonAssessment cross = interpreter.interpret(assessments);
        assertNotEquals(CrossHorizonAlignment.UNKNOWN, cross.alignment(),
                "UNKNOWN is never a normal verdict for valid typed input");
        return cross;
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> interpreter.interpret(null));
    }

    // ------------------------------------------------------------------ 15.1 anchor hierarchy

    @Test
    void directionalH60sIsAlwaysTheDominantHorizon() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.9"), bullish(H60S, "0.1")));
        assertEquals(H60S, cross.dominantHorizon(), "H60S anchors even when H15S is far stronger");
        assertTrue(cross.reasonCodes().contains(CROSS_H60_CONTEXT_DOMINANT));
    }

    @Test
    void h15sIsDominantOnlyWithoutADirectionalH60s() {
        CrossHorizonAssessment noneligibleH60 = interpret(assessments(
                neutral(H1S), bearish(H5S, "0.7"), bearish(H15S, "0.4"), unavailable(H60S)));
        assertEquals(H15S, noneligibleH60.dominantHorizon());
        assertTrue(noneligibleH60.reasonCodes().contains(CROSS_H15_STRUCTURE_DOMINANT));

        CrossHorizonAssessment neutralH60 = interpret(assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), neutral(H60S)));
        assertEquals(H15S, neutralH60.dominantHorizon(),
                "a neutral H60S is not an anchor but does not block the H15S anchor either");
        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, neutralH60.alignment());
        assertEquals(InterpretationDirection.BULLISH, neutralH60.direction());
    }

    @Test
    void h5sAndH1sNeverBecomeDominant() {
        // strongly directional H5S + H1S, no senior anchor: no dominant horizon at all
        CrossHorizonAssessment cross = interpret(assessments(
                bullish(H1S, "1"), bullish(H5S, "1"), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, cross.alignment());
        assertNull(cross.dominantHorizon());
    }

    // ------------------------------------------------------------------ 15.2 full alignment

    @Test
    void fullBullishAlignment() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH, cross.alignment());
        assertEquals(InterpretationDirection.BULLISH, cross.direction());
        assertEquals(H60S, cross.dominantHorizon());
        assertEquals(List.of(H1S, H5S, H15S, H60S), cross.participatingHorizons(), "canonical order");
        assertEquals(List.of(), cross.conflictingHorizons());
        assertEquals(EvidenceStrength.of("0.4"), cross.evidenceStrength(), "minimum structural strength");
        assertEquals(MarketRegime.TRENDING, cross.regime());
        assertEquals(List.of(CROSS_HORIZON_ALIGNED_BULLISH, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    @Test
    void fullBearishAlignmentWithoutH1s() {
        CrossHorizonAssessment cross = interpret(assessments(
                unavailable(H1S), bearish(H5S, "0.5"), bearish(H15S, "0.9"), bearish(H60S, "0.7")));

        assertEquals(CrossHorizonAlignment.ALIGNED_BEARISH, cross.alignment());
        assertEquals(InterpretationDirection.BEARISH, cross.direction());
        assertEquals(H60S, cross.dominantHorizon());
        assertEquals(List.of(H5S, H15S, H60S), cross.participatingHorizons(),
                "an unavailable H1S is not a participant and does not break structural alignment");
        assertEquals(EvidenceStrength.of("0.5"), cross.evidenceStrength());
        assertEquals(List.of(CROSS_HORIZON_ALIGNED_BEARISH, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    @Test
    void sameDirectionH1sAddsASupportiveReasonWithoutChangingStrength() {
        CrossHorizonAssessment cross = interpret(assessments(
                bullish(H1S, "0.05"), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH, cross.alignment());
        assertEquals(EvidenceStrength.of("0.4"), cross.evidenceStrength(),
                "H1S is excluded from the minimum — 0.05 must not drag the aggregate down");
        assertEquals(List.of(CROSS_HORIZON_ALIGNED_BULLISH, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_H1_SUPPORTS_CONTEXT, CROSS_HORIZON_REGIME_FROM_DOMINANT),
                cross.reasonCodes());
    }

    // ------------------------------------------------------------------ 15.3 partial alignment

    @Test
    void seniorPairWithNeutralTriggerIsPartiallyAligned() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), bullish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment());
        assertEquals(InterpretationDirection.BULLISH, cross.direction());
        assertEquals(H60S, cross.dominantHorizon());
        assertEquals(List.of(), cross.conflictingHorizons());
        assertEquals(EvidenceStrength.of("0.6"), cross.evidenceStrength());
        assertEquals(List.of(CROSS_HORIZON_PARTIALLY_ALIGNED, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes(), "no trigger code for a neutral H5S");
    }

    @Test
    void anchorPlusTriggerWithUnusableH15sIsPartiallyAligned() {
        for (HorizonAssessments assessments : List.of(
                assessments(neutral(H1S), bullish(H5S, "0.3"), unavailable(H15S), bullish(H60S, "0.8")),
                assessments(neutral(H1S), bullish(H5S, "0.3"), unknownDirection(H15S), bullish(H60S, "0.8")))) {
            CrossHorizonAssessment cross = interpret(assessments);
            assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment());
            assertEquals(InterpretationDirection.BULLISH, cross.direction());
            assertEquals(H60S, cross.dominantHorizon());
            assertEquals(List.of(H1S, H5S, H60S), cross.participatingHorizons(),
                    "an unusable H15S never becomes a participant");
            assertEquals(EvidenceStrength.of("0.3"), cross.evidenceStrength());
        }
    }

    @Test
    void structurePlusTriggerWithoutH60sIsPartiallyAligned() {
        CrossHorizonAssessment cross = interpret(assessments(
                unavailable(H1S), bearish(H5S, "0.7"), bearish(H15S, "0.4"), unavailable(H60S)));

        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment());
        assertEquals(InterpretationDirection.BEARISH, cross.direction());
        assertEquals(H15S, cross.dominantHorizon());
        assertEquals(EvidenceStrength.of("0.4"), cross.evidenceStrength());
        assertEquals(List.of(CROSS_HORIZON_PARTIALLY_ALIGNED, CROSS_H15_STRUCTURE_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    @Test
    void adverseH1sDowngradesFullAlignmentToPartialWithoutConflictOrDirectionChange() {
        for (HorizonAssessments assessments : List.of(
                assessments(bearish(H1S, "0.9"), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")),
                assessments(mixed(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")))) {
            CrossHorizonAssessment cross = interpret(assessments);
            assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment(),
                    "adverse H1S downgrades, never conflicts");
            assertEquals(InterpretationDirection.BULLISH, cross.direction(),
                    "H1S can never flip or mix the senior direction");
            assertEquals(List.of(), cross.conflictingHorizons(), "H1S never enters conflictingHorizons");
            assertNull(cross.evidenceStrength(), "adverse micro-context drops the aggregate strength");
            assertEquals(List.of(CROSS_HORIZON_PARTIALLY_ALIGNED, CROSS_H60_CONTEXT_DOMINANT,
                    CROSS_H5_TRIGGER_CONFIRMS, CROSS_H1_ADVERSE_CONTEXT, CROSS_HORIZON_REGIME_FROM_DOMINANT),
                    cross.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ 15.4 structural conflicts

    @Test
    void seniorDisagreementIsConflicting() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), bearish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.CONFLICTING, cross.alignment());
        assertEquals(InterpretationDirection.MIXED, cross.direction());
        assertEquals(H60S, cross.dominantHorizon(), "the anchor stays dominant in a conflict");
        assertEquals(List.of(H15S), cross.conflictingHorizons());
        assertNull(cross.evidenceStrength());
        assertEquals(List.of(CROSS_HORIZON_CONFLICTING, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    @Test
    void opposingTriggerIsConflicting() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), bearish(H5S, "0.2"), bullish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.CONFLICTING, cross.alignment());
        assertEquals(List.of(H5S), cross.conflictingHorizons());
        assertEquals(List.of(CROSS_HORIZON_CONFLICTING, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONTRADICTS, CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    @Test
    void structuralMixedIsAConflictEvenAgainstTheH15sAnchor() {
        CrossHorizonAssessment mixedH15 = interpret(assessments(
                neutral(H1S), neutral(H5S), mixed(H15S), bullish(H60S, "0.8")));
        assertEquals(CrossHorizonAlignment.CONFLICTING, mixedH15.alignment());
        assertEquals(List.of(H15S), mixedH15.conflictingHorizons());

        CrossHorizonAssessment mixedH60 = interpret(assessments(
                neutral(H1S), neutral(H5S), bullish(H15S, "0.5"), mixed(H60S)));
        assertEquals(CrossHorizonAlignment.CONFLICTING, mixedH60.alignment());
        assertEquals(H15S, mixedH60.dominantHorizon(), "a MIXED H60S is not an anchor — H15S anchors");
        assertEquals(List.of(H60S), mixedH60.conflictingHorizons());
    }

    @Test
    void multipleConflictsAreListedInCanonicalOrderWithoutTheAnchorOrH1s() {
        CrossHorizonAssessment cross = interpret(assessments(
                bearish(H1S, "0.9"), mixed(H5S), bearish(H15S, "0.3"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.CONFLICTING, cross.alignment());
        assertEquals(InterpretationDirection.MIXED, cross.direction());
        assertEquals(H60S, cross.dominantHorizon());
        assertEquals(List.of(H5S, H15S), cross.conflictingHorizons(),
                "canonical order; the anchor and the adverse H1S are never conflicting horizons");
        assertEquals(List.of(H1S, H5S, H15S, H60S), cross.participatingHorizons());
        assertNull(cross.evidenceStrength());
    }

    @Test
    void confirmingTriggerNextToASeniorConflictIsStillReported() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), bullish(H5S, "0.5"), bearish(H15S, "0.6"), bullish(H60S, "0.8")));

        assertEquals(CrossHorizonAlignment.CONFLICTING, cross.alignment());
        assertEquals(List.of(H15S), cross.conflictingHorizons());
        assertEquals(List.of(CROSS_HORIZON_CONFLICTING, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_H5_TRIGGER_CONFIRMS, CROSS_HORIZON_REGIME_FROM_DOMINANT), cross.reasonCodes());
    }

    // ------------------------------------------------------------------ 15.5 H1S role

    @Test
    void h1sIsNeverAStructuralConfirmation() {
        // H60S + H1S same direction: one structural confirmation only → insufficient
        CrossHorizonAssessment seniorPlusMicro = interpret(assessments(
                bullish(H1S, "0.9"), unavailable(H5S), unavailable(H15S), bullish(H60S, "0.8")));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, seniorPlusMicro.alignment());
        assertEquals(InterpretationDirection.UNKNOWN, seniorPlusMicro.direction());
        assertNull(seniorPlusMicro.dominantHorizon());
        assertNull(seniorPlusMicro.evidenceStrength());
        assertNull(seniorPlusMicro.regime());
        assertEquals(List.of(CROSS_HORIZON_INSUFFICIENT_DATA,
                CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION), seniorPlusMicro.reasonCodes());

        // H5S + H1S same direction without a senior anchor → insufficient
        CrossHorizonAssessment triggerPlusMicro = interpret(assessments(
                bullish(H1S, "0.9"), bullish(H5S, "0.9"), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, triggerPlusMicro.alignment());
        assertEquals(List.of(CROSS_HORIZON_INSUFFICIENT_DATA,
                CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR), triggerPlusMicro.reasonCodes());
    }

    @Test
    void oppositeOrMixedH1sNeverCreatesConflicting() {
        for (HorizonAssessments assessments : List.of(
                assessments(bearish(H1S, "1"), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")),
                assessments(mixed(H1S), neutral(H5S), bullish(H15S, "0.6"), bullish(H60S, "0.8")))) {
            CrossHorizonAssessment cross = interpret(assessments);
            assertNotEquals(CrossHorizonAlignment.CONFLICTING, cross.alignment());
            assertEquals(List.of(), cross.conflictingHorizons());
        }
    }

    // ------------------------------------------------------------------ 15.6 neutral and insufficient

    @Test
    void allNeutralSeniorHorizonsConcludeNeutral() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), neutral(H15S), neutral(H60S)));

        assertEquals(CrossHorizonAlignment.NEUTRAL, cross.alignment());
        assertEquals(InterpretationDirection.NEUTRAL, cross.direction());
        assertEquals(EvidenceStrength.MIN, cross.evidenceStrength(), "neutral is a real interpreted 0");
        assertNull(cross.dominantHorizon());
        assertEquals(List.of(H1S, H5S, H15S, H60S), cross.participatingHorizons());
        assertEquals(List.of(), cross.conflictingHorizons());
        assertEquals(MarketRegime.RANGING, cross.regime(), "senior-first fallback: H60S regime");
        assertEquals(List.of(CROSS_HORIZON_NEUTRAL, CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR,
                CROSS_HORIZON_REGIME_FALLBACK), cross.reasonCodes());
    }

    @Test
    void aSingleNeutralSeniorHorizonIsEnoughForNeutral() {
        CrossHorizonAssessment cross = interpret(assessments(
                unavailable(H1S), unavailable(H5S), unavailable(H15S), neutral(H60S)));
        assertEquals(CrossHorizonAlignment.NEUTRAL, cross.alignment());
        assertEquals(List.of(H60S), cross.participatingHorizons());
    }

    @Test
    void neutralOnlyOnJuniorHorizonsIsInsufficient() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, cross.alignment());
        assertEquals(List.of(CROSS_HORIZON_INSUFFICIENT_DATA, CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR),
                cross.reasonCodes());
    }

    @Test
    void aLoneDirectionalAnchorIsInsufficient() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), neutral(H15S), bullish(H60S, "0.8")));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, cross.alignment());
        assertEquals(InterpretationDirection.UNKNOWN, cross.direction(),
                "a single senior direction is never published as a cross-horizon direction");
        assertEquals(List.of(CROSS_HORIZON_INSUFFICIENT_DATA,
                CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION), cross.reasonCodes());
    }

    @Test
    void loneJuniorDirectionsAreInsufficient() {
        CrossHorizonAssessment onlyTrigger = interpret(assessments(
                neutral(H1S), bullish(H5S, "0.9"), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, onlyTrigger.alignment());

        CrossHorizonAssessment onlyMicro = interpret(assessments(
                bullish(H1S, "0.9"), unavailable(H5S), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, onlyMicro.alignment());
    }

    @Test
    void noParticipantsAndUnknownSeniorsAreInsufficient() {
        CrossHorizonAssessment noParticipants = interpret(assessments(
                unavailable(H1S), unavailable(H5S), unavailable(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, noParticipants.alignment());
        assertEquals(List.of(), noParticipants.participatingHorizons());

        CrossHorizonAssessment unknownSeniors = interpret(assessments(
                unknownDirection(H1S), unknownDirection(H5S), unknownDirection(H15S), unknownDirection(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, unknownSeniors.alignment());
        assertEquals(List.of(), unknownSeniors.participatingHorizons(),
                "eligible horizons with direction UNKNOWN never participate");
    }

    @Test
    void mixedStateWithoutAnchorIsInsufficientNotNeutral() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), mixed(H15S), unavailable(H60S)));
        assertEquals(CrossHorizonAlignment.INSUFFICIENT_DATA, cross.alignment());
        assertEquals(List.of(), cross.conflictingHorizons(), "a conflict needs an anchor");
    }

    // ------------------------------------------------------------------ 15.7 strength aggregation

    @Test
    void aggregateStrengthIsNullWhenAnyStructuralConfirmationHasNone() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), eligible(H5S, InterpretationDirection.BULLISH, null, MarketRegime.TRENDING),
                bullish(H15S, "0.6"), bullish(H60S, "0.8")));
        assertEquals(CrossHorizonAlignment.ALIGNED_BULLISH, cross.alignment(),
                "a null-strength confirmation still confirms the direction");
        assertNull(cross.evidenceStrength(), "one null structural confirmation nulls the aggregate");
    }

    // minimum over confirming structural horizons, the H1S exclusion, the adverse-H1S null, the
    // CONFLICTING null, the NEUTRAL MIN and the INSUFFICIENT null are asserted in the scenarios above

    // ------------------------------------------------------------------ 15.8 regime provenance

    @Test
    void unusableDominantRegimeFallsBackInRoleOrder() {
        // dominant H60S regime UNKNOWN → next usable in role order is H15S
        CrossHorizonAssessment viaH15 = interpret(assessments(
                neutral(H1S), neutral(H5S), bullish(H15S, "0.6"),
                eligible(H60S, InterpretationDirection.BULLISH, "0.8", MarketRegime.UNKNOWN)));
        assertEquals(MarketRegime.TRENDING, viaH15.regime());
        assertEquals(List.of(CROSS_HORIZON_PARTIALLY_ALIGNED, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_HORIZON_REGIME_FALLBACK), viaH15.reasonCodes());

        // H60S and H15S regimes UNKNOWN → H5S
        CrossHorizonAssessment viaH5 = interpret(assessments(
                neutral(H1S), eligible(H5S, InterpretationDirection.BULLISH, "0.4", MarketRegime.QUIET),
                eligible(H15S, InterpretationDirection.BULLISH, "0.6", MarketRegime.UNKNOWN),
                eligible(H60S, InterpretationDirection.BULLISH, "0.8", MarketRegime.UNKNOWN)));
        assertEquals(MarketRegime.QUIET, viaH5.regime());
        assertTrue(viaH5.reasonCodes().contains(CROSS_HORIZON_REGIME_FALLBACK));
    }

    @Test
    void regimeFallbackSkipsNonParticipants() {
        // H15S is eligible with a usable regime but direction UNKNOWN — it is not a participant and
        // must not be a regime source; the fallback goes to H5S instead
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), eligible(H5S, InterpretationDirection.BULLISH, "0.4", MarketRegime.QUIET),
                eligible(H15S, InterpretationDirection.UNKNOWN, null, MarketRegime.TRENDING),
                eligible(H60S, InterpretationDirection.BULLISH, "0.8", MarketRegime.UNKNOWN)));
        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment());
        assertEquals(MarketRegime.QUIET, cross.regime(), "H15S regime must be skipped: not a participant");
    }

    @Test
    void allUnusableRegimesYieldUnknownRegimeOnAFormedAssessment() {
        CrossHorizonAssessment cross = interpret(assessments(
                unavailable(H1S), unavailable(H5S),
                eligible(H15S, InterpretationDirection.BULLISH, "0.6", MarketRegime.UNKNOWN),
                eligible(H60S, InterpretationDirection.BULLISH, "0.8", MarketRegime.UNKNOWN)));
        assertEquals(CrossHorizonAlignment.PARTIALLY_ALIGNED, cross.alignment());
        assertEquals(MarketRegime.UNKNOWN, cross.regime());
        assertEquals(List.of(CROSS_HORIZON_PARTIALLY_ALIGNED, CROSS_H60_CONTEXT_DOMINANT,
                CROSS_HORIZON_REGIME_UNKNOWN), cross.reasonCodes());
    }

    @Test
    void neutralUsesTheSameSeniorFirstRegimeFallback() {
        CrossHorizonAssessment cross = interpret(assessments(
                neutral(H1S), neutral(H5S), neutral(H15S),
                eligible(H60S, InterpretationDirection.NEUTRAL, "0", MarketRegime.QUIET)));
        assertEquals(CrossHorizonAlignment.NEUTRAL, cross.alignment());
        assertEquals(MarketRegime.QUIET, cross.regime(), "H60S first, even for NEUTRAL");
        assertTrue(cross.reasonCodes().contains(CROSS_HORIZON_REGIME_FALLBACK));
    }

    // ------------------------------------------------------------------ 15.10 determinism

    @Test
    void identicalInputYieldsValueEqualOutputAndInputIsNotMutated() {
        HorizonAssessments input = assessments(
                bearish(H1S, "0.9"), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8"));
        HorizonAssessments copy = assessments(
                bearish(H1S, "0.9"), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8"));

        CrossHorizonAssessment first = interpreter.interpret(input);
        CrossHorizonAssessment second = new CrossHorizonInterpreter().interpret(copy);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.reasonCodes(), second.reasonCodes(), "stable reason order");
        assertEquals(copy, input, "the interpreter never mutates its input");

        assertThrows(UnsupportedOperationException.class, () -> first.participatingHorizons().add(H1S));
        assertThrows(UnsupportedOperationException.class, () -> first.reasonCodes().remove(0));
    }
}

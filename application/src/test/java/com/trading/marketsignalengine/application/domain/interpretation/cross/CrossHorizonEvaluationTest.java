package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.assessments;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.bullish;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.neutral;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.unavailable;
import static com.trading.marketsignalengine.application.domain.interpretation.cross.CrossFixtures.unknownDirection;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reference consistency of the paired result: a {@link CrossHorizonEvaluation} can only exist when
 * every horizon the cross assessment refers to actually resolves — eligible, with a known direction —
 * against the {@link HorizonAssessments} it is paired with. Value semantics.
 */
class CrossHorizonEvaluationTest {

    private static final HorizonAssessments ALIGNED = assessments(
            neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8"));

    private static CrossHorizonAssessment alignedCross() {
        return CrossHorizonAssessment.alignedBullish(EvidenceStrength.of("0.4"), H60S,
                List.of(H1S, H5S, H15S, H60S), null, List.of());
    }

    @Test
    void exposesExactlyThePairedResults() {
        CrossHorizonAssessment cross = alignedCross();
        CrossHorizonEvaluation evaluation = new CrossHorizonEvaluation(ALIGNED, cross);

        assertSame(ALIGNED, evaluation.horizonAssessments());
        assertSame(cross, evaluation.crossHorizonAssessment());
    }

    @Test
    void rejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonEvaluation(null, alignedCross()));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonEvaluation(ALIGNED, null));
    }

    @Test
    void rejectsParticipantsThatDoNotResolveAgainstThePairedAssessments() {
        // H60S participant, but the paired assessments say H60S is UNAVAILABLE
        HorizonAssessments withoutH60 = assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), unavailable(H60S));
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonEvaluation(withoutH60, alignedCross()));

        // H60S participant, but the paired assessments say its direction is UNKNOWN
        HorizonAssessments unknownH60 = assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), unknownDirection(H60S));
        assertThrows(IllegalArgumentException.class,
                () -> new CrossHorizonEvaluation(unknownH60, alignedCross()));
    }

    @Test
    void rejectsADominantHorizonThatIsNotEligibleInThePairedAssessments() {
        // a cross assessment claiming dominant H15S paired with assessments where H15S is unavailable
        CrossHorizonAssessment cross = CrossHorizonAssessment.partiallyAligned(InterpretationDirection.BULLISH,
                EvidenceStrength.of("0.4"), H15S, List.of(H5S, H15S), null, List.of());
        HorizonAssessments withoutH15 = assessments(
                neutral(H1S), bullish(H5S, "0.4"), unavailable(H15S), unavailable(H60S));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonEvaluation(withoutH15, cross));
    }

    @Test
    void valueSemantics() {
        CrossHorizonEvaluation first = new CrossHorizonEvaluation(ALIGNED, alignedCross());
        CrossHorizonEvaluation second = new CrossHorizonEvaluation(assessments(
                neutral(H1S), bullish(H5S, "0.4"), bullish(H15S, "0.6"), bullish(H60S, "0.8")), alignedCross());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
        assertTrue(first.toString().contains("CrossHorizonEvaluation"));

        CrossHorizonEvaluation different = new CrossHorizonEvaluation(ALIGNED,
                CrossHorizonAssessment.insufficientData(List.of(H1S, H5S, H15S, H60S), List.of()));
        assertNotEquals(first, different);
    }
}

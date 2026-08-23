package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.strength;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossHorizonAssessmentTest {

    private static final List<MarketHorizon> ALL = MarketHorizon.canonicalOrder();

    @Test
    void conflictingHorizonsMustBeAProperSubsetOfParticipating() {
        CrossHorizonAssessment ok = CrossHorizonAssessment.conflicting(strength("0.4"), H60S,
                List.of(H5S, H15S, H60S), List.of(H5S), null, List.of());
        assertEquals(List.of(H5S), ok.conflictingHorizons());

        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.conflicting(null, null,
                List.of(H15S, H60S), List.of(H5S), null, List.of()), "conflicting not participating");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.conflicting(null, null,
                List.of(H15S, H60S), List.of(H15S, H60S), null, List.of()), "every horizon conflicting");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.conflicting(null, null,
                List.of(H15S, H60S), List.of(), null, List.of()), "CONFLICTING without conflicting horizons");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.conflicting(null, null,
                List.of(H60S), List.of(), null, List.of()), "CONFLICTING needs two participants");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.conflicting(null, H5S,
                List.of(H5S, H60S), List.of(H5S), null, List.of()), "dominant cannot be conflicting");
    }

    @Test
    void onlyConflictingAlignmentMayCarryConflictingHorizons() {
        for (CrossHorizonAlignment alignment : CrossHorizonAlignment.values()) {
            if (alignment == CrossHorizonAlignment.CONFLICTING) {
                continue;
            }
            InterpretationDirection direction = switch (alignment) {
                case ALIGNED_BULLISH, PARTIALLY_ALIGNED -> InterpretationDirection.BULLISH;
                case ALIGNED_BEARISH -> InterpretationDirection.BEARISH;
                case NEUTRAL -> InterpretationDirection.NEUTRAL;
                default -> InterpretationDirection.UNKNOWN;
            };
            assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(alignment, direction, null,
                    null, List.of(H5S, H60S), List.of(H5S), null, List.of()), alignment + " with conflictingHorizons");
        }
    }

    @Test
    void conflictingHasMixedDirection() {
        assertEquals(InterpretationDirection.MIXED, CrossHorizonAssessment.conflicting(null, null,
                List.of(H5S, H60S), List.of(H5S), null, List.of()).direction());
        for (InterpretationDirection direction : InterpretationDirection.values()) {
            if (direction == InterpretationDirection.MIXED) {
                continue;
            }
            assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.CONFLICTING,
                    direction, null, null, List.of(H5S, H60S), List.of(H5S), null, List.of()), "CONFLICTING + " + direction);
        }
    }

    @Test
    void inconclusiveAlignmentsAreUnknownWithoutStrengthOrDominantHorizon() {
        CrossHorizonAssessment insufficient = CrossHorizonAssessment.insufficientData(List.of(H1S), List.of());
        assertEquals(InterpretationDirection.UNKNOWN, insufficient.direction());
        assertNull(insufficient.evidenceStrength());
        assertNull(insufficient.dominantHorizon());
        assertTrue(insufficient.isInconclusive());
        CrossHorizonAssessment unknown = CrossHorizonAssessment.unknown(List.of());
        assertEquals(InterpretationDirection.UNKNOWN, unknown.direction());
        assertEquals(List.of(), unknown.participatingHorizons());

        for (CrossHorizonAlignment alignment : List.of(CrossHorizonAlignment.INSUFFICIENT_DATA, CrossHorizonAlignment.UNKNOWN)) {
            assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(alignment,
                    InterpretationDirection.NEUTRAL, null, null, List.of(), List.of(), null, List.of()), alignment + " + NEUTRAL");
            assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(alignment,
                    InterpretationDirection.UNKNOWN, strength("0.1"), null, List.of(), List.of(), null, List.of()), alignment + " + strength");
            assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(alignment,
                    InterpretationDirection.UNKNOWN, null, H1S, List.of(H1S), List.of(), null, List.of()), alignment + " + dominant");
        }
    }

    @Test
    void alignedAndNeutralAlignmentsFixTheDirection() {
        assertEquals(InterpretationDirection.BULLISH,
                CrossHorizonAssessment.alignedBullish(null, null, ALL, null, List.of()).direction());
        assertEquals(InterpretationDirection.BEARISH,
                CrossHorizonAssessment.alignedBearish(null, H60S, ALL, null, List.of()).direction());
        assertEquals(InterpretationDirection.NEUTRAL,
                CrossHorizonAssessment.neutral(strength("0.05"), ALL, MarketRegime.QUIET, List.of()).direction());
        assertEquals(InterpretationDirection.BEARISH, CrossHorizonAssessment.partiallyAligned(InterpretationDirection.BEARISH,
                null, H15S, List.of(H5S, H15S), null, List.of()).direction());

        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.ALIGNED_BULLISH,
                InterpretationDirection.BEARISH, null, null, ALL, List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.ALIGNED_BEARISH,
                InterpretationDirection.MIXED, null, null, ALL, List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.NEUTRAL,
                InterpretationDirection.UNKNOWN, null, null, ALL, List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.NEUTRAL,
                InterpretationDirection.NEUTRAL, null, H5S, ALL, List.of(), null, List.of()), "NEUTRAL has no dominant horizon");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.partiallyAligned(InterpretationDirection.NEUTRAL,
                null, null, ALL, null, List.of()), "PARTIALLY_ALIGNED needs a dominant direction");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.partiallyAligned(InterpretationDirection.MIXED,
                null, null, ALL, null, List.of()));
        // conclusive alignments need participants
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.alignedBullish(null, null, List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.neutral(null, List.of(), null, List.of()));
        // dominant must be participating
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.alignedBullish(null, H60S, List.of(H1S, H5S), null, List.of()));
    }

    @Test
    void horizonListsAreUniqueCanonicallyOrderedAndImmutable() {
        CrossHorizonAssessment cross = CrossHorizonAssessment.conflicting(null, H60S,
                List.of(H60S, H1S, H15S, H5S), List.of(H5S, H1S), null, List.of());
        assertEquals(ALL, cross.participatingHorizons());
        assertEquals(List.of(H1S, H5S), cross.conflictingHorizons());
        assertThrows(UnsupportedOperationException.class, () -> cross.participatingHorizons().add(H1S));
        assertThrows(UnsupportedOperationException.class, () -> cross.conflictingHorizons().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> cross.reasonCodes().add(ReasonCode.of("X")));

        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.alignedBullish(null, null,
                List.of(H5S, H5S), null, List.of()), "duplicate participating");
        assertThrows(IllegalArgumentException.class, () -> CrossHorizonAssessment.alignedBullish(null, null,
                java.util.Arrays.asList(H5S, null), null, List.of()), "null participating");
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(null, InterpretationDirection.UNKNOWN,
                null, null, List.of(), List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CrossHorizonAssessment(CrossHorizonAlignment.UNKNOWN, null,
                null, null, List.of(), List.of(), null, List.of()));
    }
}

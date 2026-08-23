package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.availableUnknown;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.neutral;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.notAvailable;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.unavailable;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_BOOK_CONTRADICTS_DIRECTION;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_BOOK_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_BOOK_SUPPORTS_DIRECTION;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_FROM_FLOW;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_FROM_MOMENTUM;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_INSUFFICIENT;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_DIRECTION_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_CONFIRMED;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_DIVERGENCE;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Horizon direction resolution: the Flow+Momentum matrix on 5S/15S/60S, the Flow-only 1S semantics,
 * book context (support / contradiction / neutral, never a direction of its own), null-strength
 * handling and the typed-evidence-only contract (no reason-code parsing, nested reasons never
 * copied).
 */
class HorizonDirectionResolverTest {

    private static EvidenceAssessment flow(InterpretationDirection direction, String strength) {
        return EvidenceFixtures.available(EvidenceDimension.FLOW, direction, strength);
    }

    private static EvidenceAssessment momentum(InterpretationDirection direction, String strength) {
        return EvidenceFixtures.available(EvidenceDimension.MOMENTUM, direction, strength);
    }

    private static EvidenceAssessment book(InterpretationDirection direction, String strength) {
        return EvidenceFixtures.available(EvidenceDimension.BOOK, direction, strength);
    }

    private static final EvidenceAssessment NO_BOOK = unavailable(EvidenceDimension.BOOK);
    private static final EvidenceAssessment NO_MOMENTUM = unavailable(EvidenceDimension.MOMENTUM);

    private static HorizonDirectionResolution resolve(MarketHorizon horizon, EvidenceAssessment flow,
                                                      EvidenceAssessment momentum, EvidenceAssessment book) {
        return HorizonDirectionResolver.resolve(horizon, flow, momentum, book);
    }

    // ------------------------------------------------------------------ 5S/15S/60S primary matrix

    @ParameterizedTest
    @EnumSource(value = MarketHorizon.class, names = {"H5S", "H15S", "H60S"})
    void agreementIsConfirmedWithTheWeakerStrength(MarketHorizon horizon) {
        HorizonDirectionResolution bullish = resolve(horizon,
                flow(InterpretationDirection.BULLISH, "0.7"), momentum(InterpretationDirection.BULLISH, "0.4"), NO_BOOK);
        assertEquals(InterpretationDirection.BULLISH, bullish.direction());
        assertEquals(EvidenceStrength.of("0.4"), bullish.evidenceStrength(), "min(0.7, 0.4)");
        assertEquals(List.of(HORIZON_FLOW_MOMENTUM_CONFIRMED), bullish.reasonCodes());

        HorizonDirectionResolution bearish = resolve(horizon,
                flow(InterpretationDirection.BEARISH, "0.3"), momentum(InterpretationDirection.BEARISH, "0.9"), NO_BOOK);
        assertEquals(InterpretationDirection.BEARISH, bearish.direction());
        assertEquals(EvidenceStrength.of("0.3"), bearish.evidenceStrength());
        assertEquals(List.of(HORIZON_FLOW_MOMENTUM_CONFIRMED), bearish.reasonCodes());
    }

    @ParameterizedTest
    @EnumSource(value = MarketHorizon.class, names = {"H5S", "H15S", "H60S"})
    void divergenceIsMixedWithNoStrengthAndNoDominantSide(MarketHorizon horizon) {
        for (HorizonDirectionResolution mixed : List.of(
                resolve(horizon, flow(InterpretationDirection.BULLISH, "0.9"),
                        momentum(InterpretationDirection.BEARISH, "0.1"), NO_BOOK),
                resolve(horizon, flow(InterpretationDirection.BEARISH, "0.1"),
                        momentum(InterpretationDirection.BULLISH, "0.9"), NO_BOOK))) {
            assertEquals(InterpretationDirection.MIXED, mixed.direction(), "divergence is reported, not resolved");
            assertNull(mixed.evidenceStrength());
            assertEquals(List.of(HORIZON_FLOW_MOMENTUM_DIVERGENCE), mixed.reasonCodes());
        }
    }

    @Test
    void singleDirectionalSourceKeepsItsOwnStrength() {
        // directional Flow + neutral Momentum
        HorizonDirectionResolution fromFlow = resolve(H5S,
                flow(InterpretationDirection.BULLISH, "0.6"), neutral(EvidenceDimension.MOMENTUM), NO_BOOK);
        assertEquals(InterpretationDirection.BULLISH, fromFlow.direction());
        assertEquals(EvidenceStrength.of("0.6"), fromFlow.evidenceStrength(), "no discount factor");
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW), fromFlow.reasonCodes());

        // neutral Flow + directional Momentum
        HorizonDirectionResolution fromMomentum = resolve(H5S,
                neutral(EvidenceDimension.FLOW), momentum(InterpretationDirection.BEARISH, "0.5"), NO_BOOK);
        assertEquals(InterpretationDirection.BEARISH, fromMomentum.direction());
        assertEquals(EvidenceStrength.of("0.5"), fromMomentum.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_MOMENTUM), fromMomentum.reasonCodes());

        // directional + non-AVAILABLE and directional + AVAILABLE-UNKNOWN still resolve from the vote
        for (EvidenceAssessment mom : List.of(NO_MOMENTUM,
                notAvailable(EvidenceDimension.MOMENTUM, EvidenceAvailabilityStatus.UNTRUSTED),
                notAvailable(EvidenceDimension.MOMENTUM, EvidenceAvailabilityStatus.FAILED),
                availableUnknown(EvidenceDimension.MOMENTUM))) {
            HorizonDirectionResolution r = resolve(H5S, flow(InterpretationDirection.BULLISH, "0.6"), mom, NO_BOOK);
            assertEquals(InterpretationDirection.BULLISH, r.direction());
            assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW), r.reasonCodes());
        }
        HorizonDirectionResolution r = resolve(H5S, unavailable(EvidenceDimension.FLOW),
                momentum(InterpretationDirection.BULLISH, "0.6"), NO_BOOK);
        assertEquals(InterpretationDirection.BULLISH, r.direction());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_MOMENTUM), r.reasonCodes());
    }

    @Test
    void bothNeutralIsNeutralWithRealZeroStrength() {
        HorizonDirectionResolution result = resolve(H5S,
                neutral(EvidenceDimension.FLOW), neutral(EvidenceDimension.MOMENTUM), NO_BOOK);

        assertEquals(InterpretationDirection.NEUTRAL, result.direction());
        assertEquals(EvidenceStrength.MIN, result.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_NEUTRAL), result.reasonCodes());
    }

    @Test
    void missingEvidenceIsNeverANeutralConfirmation() {
        List<HorizonDirectionResolution> unknowns = List.of(
                // neutral + unavailable
                resolve(H5S, neutral(EvidenceDimension.FLOW), NO_MOMENTUM, NO_BOOK),
                // unavailable + neutral
                resolve(H5S, unavailable(EvidenceDimension.FLOW), neutral(EvidenceDimension.MOMENTUM), NO_BOOK),
                // neutral + AVAILABLE-UNKNOWN
                resolve(H5S, neutral(EvidenceDimension.FLOW), availableUnknown(EvidenceDimension.MOMENTUM), NO_BOOK),
                // AVAILABLE-UNKNOWN + AVAILABLE-UNKNOWN
                resolve(H5S, availableUnknown(EvidenceDimension.FLOW), availableUnknown(EvidenceDimension.MOMENTUM), NO_BOOK),
                // both non-AVAILABLE
                resolve(H5S, notAvailable(EvidenceDimension.FLOW, EvidenceAvailabilityStatus.FAILED),
                        notAvailable(EvidenceDimension.MOMENTUM, EvidenceAvailabilityStatus.UNTRUSTED), NO_BOOK));

        for (HorizonDirectionResolution unknown : unknowns) {
            assertEquals(InterpretationDirection.UNKNOWN, unknown.direction(), "missing evidence is not neutral");
            assertNull(unknown.evidenceStrength());
            assertEquals(List.of(HORIZON_DIRECTION_INSUFFICIENT), unknown.reasonCodes());
        }
    }

    @Test
    void nullStrengthOnADirectionalVoteStaysDirectionalWithoutInventingAStrength() {
        HorizonDirectionResolution confirmed = resolve(H5S,
                flow(InterpretationDirection.BULLISH, null), momentum(InterpretationDirection.BULLISH, "0.4"), NO_BOOK);
        assertEquals(InterpretationDirection.BULLISH, confirmed.direction());
        assertNull(confirmed.evidenceStrength(), "min with an absent strength is absent, never invented");
        assertEquals(List.of(HORIZON_FLOW_MOMENTUM_CONFIRMED), confirmed.reasonCodes());

        HorizonDirectionResolution fromFlow = resolve(H5S,
                flow(InterpretationDirection.BEARISH, null), neutral(EvidenceDimension.MOMENTUM), NO_BOOK);
        assertEquals(InterpretationDirection.BEARISH, fromFlow.direction());
        assertNull(fromFlow.evidenceStrength());
    }

    // ------------------------------------------------------------------ 1S: flow only

    @Test
    void h1sResolvesFromFlowAloneAndNotScopedMomentumDoesNotBlockIt() {
        EvidenceAssessment notScopedMomentum = unavailable(EvidenceDimension.MOMENTUM);

        HorizonDirectionResolution bullish = resolve(H1S,
                flow(InterpretationDirection.BULLISH, "0.8"), notScopedMomentum, NO_BOOK);
        assertEquals(InterpretationDirection.BULLISH, bullish.direction());
        assertEquals(EvidenceStrength.of("0.8"), bullish.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW), bullish.reasonCodes());

        HorizonDirectionResolution neutral = resolve(H1S,
                neutral(EvidenceDimension.FLOW), notScopedMomentum, NO_BOOK);
        assertEquals(InterpretationDirection.NEUTRAL, neutral.direction(),
                "a valid neutral Flow is NEUTRAL on 1S despite the unavailable momentum");
        assertEquals(EvidenceStrength.MIN, neutral.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_NEUTRAL), neutral.reasonCodes());

        for (EvidenceAssessment flow : List.of(
                unavailable(EvidenceDimension.FLOW),
                notAvailable(EvidenceDimension.FLOW, EvidenceAvailabilityStatus.UNTRUSTED),
                availableUnknown(EvidenceDimension.FLOW))) {
            HorizonDirectionResolution unknown = resolve(H1S, flow, notScopedMomentum, NO_BOOK);
            assertEquals(InterpretationDirection.UNKNOWN, unknown.direction());
            assertNull(unknown.evidenceStrength());
            assertEquals(List.of(HORIZON_DIRECTION_INSUFFICIENT), unknown.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ book context

    @Test
    void bookSupportAddsAReasonButNeverStrength() {
        HorizonDirectionResolution supported = resolve(H1S,
                flow(InterpretationDirection.BULLISH, "0.6"), NO_MOMENTUM, book(InterpretationDirection.BULLISH, "0.9"));

        assertEquals(InterpretationDirection.BULLISH, supported.direction());
        assertEquals(EvidenceStrength.of("0.6"), supported.evidenceStrength(), "no bonus from a stronger book");
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_SUPPORTS_DIRECTION), supported.reasonCodes());
    }

    @Test
    void bookContradictionKeepsTheDirectionAndDropsTheStrength() {
        HorizonDirectionResolution opposed = resolve(H1S,
                flow(InterpretationDirection.BULLISH, "0.6"), NO_MOMENTUM, book(InterpretationDirection.BEARISH, "0.2"));
        assertEquals(InterpretationDirection.BULLISH, opposed.direction(), "book can never reverse the direction");
        assertNull(opposed.evidenceStrength(), "the conclusion is no longer confirmed");
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_CONTRADICTS_DIRECTION), opposed.reasonCodes());

        HorizonDirectionResolution mixedBook = resolve(H1S,
                flow(InterpretationDirection.BEARISH, "0.5"), NO_MOMENTUM,
                EvidenceAssessment.available(EvidenceDimension.BOOK, InterpretationDirection.MIXED, null,
                        List.of(EvidenceFixtures.NESTED_REASON)));
        assertEquals(InterpretationDirection.BEARISH, mixedBook.direction());
        assertNull(mixedBook.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_CONTRADICTS_DIRECTION), mixedBook.reasonCodes());
    }

    @Test
    void neutralBookIsContextOnly() {
        HorizonDirectionResolution result = resolve(H1S,
                flow(InterpretationDirection.BULLISH, "0.6"), NO_MOMENTUM, neutral(EvidenceDimension.BOOK));

        assertEquals(InterpretationDirection.BULLISH, result.direction());
        assertEquals(EvidenceStrength.of("0.6"), result.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW, HORIZON_BOOK_NEUTRAL), result.reasonCodes());
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceAvailabilityStatus.class, names = {"UNAVAILABLE", "UNTRUSTED", "FAILED", "UNKNOWN"})
    void nonAvailableBookAddsNoHorizonLevelReason(EvidenceAvailabilityStatus status) {
        HorizonDirectionResolution result = resolve(H1S,
                flow(InterpretationDirection.BULLISH, "0.6"), NO_MOMENTUM,
                notAvailable(EvidenceDimension.BOOK, status));

        assertEquals(InterpretationDirection.BULLISH, result.direction());
        assertEquals(EvidenceStrength.of("0.6"), result.evidenceStrength());
        assertEquals(List.of(HORIZON_DIRECTION_FROM_FLOW), result.reasonCodes(),
                "the exact cause already lives in the nested book evidence");
    }

    @Test
    void bookAloneNeverCreatesADirection() {
        // strongly bullish book next to a neutral / unknown primary conclusion
        HorizonDirectionResolution neutralFlow = resolve(H1S,
                neutral(EvidenceDimension.FLOW), NO_MOMENTUM, book(InterpretationDirection.BULLISH, "0.9"));
        assertEquals(InterpretationDirection.NEUTRAL, neutralFlow.direction());
        assertEquals(List.of(HORIZON_DIRECTION_NEUTRAL), neutralFlow.reasonCodes(), "no book codes without a direction");

        HorizonDirectionResolution unknownFlow = resolve(H1S,
                unavailable(EvidenceDimension.FLOW), NO_MOMENTUM, book(InterpretationDirection.BULLISH, "0.9"));
        assertEquals(InterpretationDirection.UNKNOWN, unknownFlow.direction());
        assertEquals(List.of(HORIZON_DIRECTION_INSUFFICIENT), unknownFlow.reasonCodes());

        // book adds no code next to a divergence either
        HorizonDirectionResolution divergence = resolve(H5S,
                flow(InterpretationDirection.BULLISH, "0.6"), momentum(InterpretationDirection.BEARISH, "0.6"),
                book(InterpretationDirection.BULLISH, "0.9"));
        assertEquals(InterpretationDirection.MIXED, divergence.direction());
        assertEquals(List.of(HORIZON_FLOW_MOMENTUM_DIVERGENCE), divergence.reasonCodes());
    }

    // ------------------------------------------------------------------ contract

    @Test
    void nestedEvidenceReasonsAreNeverCopiedIntoTheResolution() {
        HorizonDirectionResolution result = resolve(H5S,
                flow(InterpretationDirection.BULLISH, "0.6"), momentum(InterpretationDirection.BULLISH, "0.4"),
                book(InterpretationDirection.BULLISH, "0.9"));

        assertFalse(result.reasonCodes().contains(EvidenceFixtures.NESTED_REASON),
                "horizon reasons describe the resolution, nested evidence explains itself");
        assertTrue(result.reasonCodes().stream().allMatch(code -> code.value().startsWith("HORIZON_")));
    }

    @Test
    void wrongDimensionOrNullInputsAreRejected() {
        EvidenceAssessment flow = flow(InterpretationDirection.BULLISH, "0.6");
        EvidenceAssessment momentum = momentum(InterpretationDirection.BULLISH, "0.4");
        EvidenceAssessment book = book(InterpretationDirection.BULLISH, "0.9");

        assertThrows(IllegalArgumentException.class, () -> resolve(null, flow, momentum, book));
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, null, momentum, book));
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, flow, null, book));
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, flow, momentum, null));
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, momentum, momentum, book), "flow slot");
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, flow, flow, book), "momentum slot");
        assertThrows(IllegalArgumentException.class, () -> resolve(H5S, flow, momentum, momentum), "book slot");
    }

    @Test
    void resolutionInvariantsAreEnforced() {
        // UNKNOWN and MIXED never carry a strength; NEUTRAL always carries a real 0
        assertThrows(IllegalArgumentException.class, () -> new HorizonDirectionResolution(
                InterpretationDirection.UNKNOWN, EvidenceStrength.MIN, List.of(HORIZON_DIRECTION_INSUFFICIENT)));
        assertThrows(IllegalArgumentException.class, () -> new HorizonDirectionResolution(
                InterpretationDirection.MIXED, EvidenceStrength.MIN, List.of(HORIZON_FLOW_MOMENTUM_DIVERGENCE)));
        assertThrows(IllegalArgumentException.class, () -> new HorizonDirectionResolution(
                InterpretationDirection.NEUTRAL, null, List.of(HORIZON_DIRECTION_NEUTRAL)));
        assertThrows(IllegalArgumentException.class, () -> new HorizonDirectionResolution(
                null, null, List.of(HORIZON_DIRECTION_INSUFFICIENT)));
        assertThrows(IllegalArgumentException.class, () -> new HorizonDirectionResolution(
                InterpretationDirection.UNKNOWN, null, List.of()));

        HorizonDirectionResolution directional = new HorizonDirectionResolution(
                InterpretationDirection.BULLISH, null, List.of(HORIZON_DIRECTION_FROM_FLOW));
        assertNull(directional.evidenceStrength(), "directional may carry a null strength");
        assertThrows(UnsupportedOperationException.class,
                () -> directional.reasonCodes().add(HORIZON_DIRECTION_NEUTRAL));
    }

    @Test
    void weakerStrengthHelperIsExact() {
        assertEquals(EvidenceStrength.of("0.4"),
                HorizonDirectionResolver.weaker(EvidenceStrength.of("0.4"), EvidenceStrength.of("0.7")));
        assertEquals(EvidenceStrength.of("0.4"),
                HorizonDirectionResolver.weaker(EvidenceStrength.of("0.7"), EvidenceStrength.of("0.4")));
        assertEquals(EvidenceStrength.of("0.5"),
                HorizonDirectionResolver.weaker(EvidenceStrength.of("0.5"), EvidenceStrength.of("0.5")));
        assertNull(HorizonDirectionResolver.weaker(null, EvidenceStrength.of("0.4")));
        assertNull(HorizonDirectionResolver.weaker(EvidenceStrength.of("0.4"), null));
    }
}

package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.availableUnknown;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.neutral;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.notAvailable;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.unavailable;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.volatility;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.EvidenceFixtures.volatilityNotAvailable;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_QUIET;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_RANGING;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_TRENDING;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_UNKNOWN;
import static com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonReasonCodes.HORIZON_REGIME_VOLATILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityLevel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regime resolution: typed VolatilityLevel + Momentum only — HIGH/EXTREME dominate, LOW splits into
 * TRENDING/QUIET, NORMAL into TRENDING/RANGING/UNKNOWN; no reason-code parsing, no Flow/Book input.
 */
class MarketRegimeResolverTest {

    private static EvidenceAssessment momentum(InterpretationDirection direction, String strength) {
        return EvidenceFixtures.available(EvidenceDimension.MOMENTUM, direction, strength);
    }

    private static final EvidenceAssessment NEUTRAL_MOMENTUM = neutral(EvidenceDimension.MOMENTUM);
    private static final EvidenceAssessment NOT_SCOPED_MOMENTUM = unavailable(EvidenceDimension.MOMENTUM);

    private static void assertRegime(MarketRegimeResolution resolution, MarketRegime regime,
                                     com.trading.marketsignalengine.application.domain.interpretation.ReasonCode reason) {
        assertEquals(regime, resolution.regime());
        assertEquals(List.of(reason), resolution.reasonCodes());
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceAvailabilityStatus.class, names = {"UNAVAILABLE", "UNTRUSTED", "FAILED", "UNKNOWN"})
    void unusableVolatilityIsUnknownRegime(EvidenceAvailabilityStatus status) {
        assertRegime(MarketRegimeResolver.resolve(volatilityNotAvailable(status),
                        momentum(InterpretationDirection.BULLISH, "0.9")),
                MarketRegime.UNKNOWN, HORIZON_REGIME_UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = VolatilityLevel.class, names = {"HIGH", "EXTREME"})
    void highAndExtremeAreVolatileRegardlessOfMomentum(VolatilityLevel level) {
        for (EvidenceAssessment momentum : List.of(
                momentum(InterpretationDirection.BULLISH, "1"),
                momentum(InterpretationDirection.BEARISH, "0.5"),
                NEUTRAL_MOMENTUM,
                NOT_SCOPED_MOMENTUM)) {
            assertRegime(MarketRegimeResolver.resolve(volatility(level), momentum),
                    MarketRegime.VOLATILE, HORIZON_REGIME_VOLATILE);
        }
    }

    @Test
    void lowVolatilitySplitsIntoTrendingAndQuiet() {
        assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.LOW),
                        momentum(InterpretationDirection.BULLISH, "0.6")),
                MarketRegime.TRENDING, HORIZON_REGIME_TRENDING);
        assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.LOW),
                        momentum(InterpretationDirection.BEARISH, "0.6")),
                MarketRegime.TRENDING, HORIZON_REGIME_TRENDING);

        // any non-directional momentum — neutral, unavailable (incl. 1S not scoped), unknown — is QUIET
        for (EvidenceAssessment momentum : List.of(
                NEUTRAL_MOMENTUM,
                NOT_SCOPED_MOMENTUM,
                notAvailable(EvidenceDimension.MOMENTUM, EvidenceAvailabilityStatus.FAILED),
                availableUnknown(EvidenceDimension.MOMENTUM))) {
            assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.LOW), momentum),
                    MarketRegime.QUIET, HORIZON_REGIME_QUIET);
        }
    }

    @Test
    void normalVolatilitySplitsIntoTrendingRangingAndUnknown() {
        assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.NORMAL),
                        momentum(InterpretationDirection.BULLISH, "0.6")),
                MarketRegime.TRENDING, HORIZON_REGIME_TRENDING);
        assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.NORMAL), NEUTRAL_MOMENTUM),
                MarketRegime.RANGING, HORIZON_REGIME_RANGING);

        // an unread market is not "ranging": unavailable / unknown momentum → UNKNOWN
        for (EvidenceAssessment momentum : List.of(
                NOT_SCOPED_MOMENTUM,
                notAvailable(EvidenceDimension.MOMENTUM, EvidenceAvailabilityStatus.UNTRUSTED),
                availableUnknown(EvidenceDimension.MOMENTUM))) {
            assertRegime(MarketRegimeResolver.resolve(volatility(VolatilityLevel.NORMAL), momentum),
                    MarketRegime.UNKNOWN, HORIZON_REGIME_UNKNOWN);
        }
    }

    @Test
    void nestedReasonsAreNeverCopiedAndOnlyTypedInputsAreRead() {
        MarketRegimeResolution resolution = MarketRegimeResolver.resolve(
                volatility(VolatilityLevel.NORMAL), momentum(InterpretationDirection.BULLISH, "0.6"));

        assertFalse(resolution.reasonCodes().contains(EvidenceFixtures.NESTED_REASON),
                "the regime is decided from the typed level, never from reason codes");
    }

    @Test
    void nullAndWrongDimensionInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MarketRegimeResolver.resolve(null, NEUTRAL_MOMENTUM));
        assertThrows(IllegalArgumentException.class,
                () -> MarketRegimeResolver.resolve(volatility(VolatilityLevel.LOW), null));
        assertThrows(IllegalArgumentException.class,
                () -> MarketRegimeResolver.resolve(volatility(VolatilityLevel.LOW), neutral(EvidenceDimension.FLOW)));
    }

    @Test
    void resolutionInvariantsAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketRegimeResolution(null, List.of(HORIZON_REGIME_UNKNOWN)), "null regime forbidden");
        assertThrows(IllegalArgumentException.class,
                () -> new MarketRegimeResolution(MarketRegime.QUIET, List.of()), "exactly one reason");
        assertThrows(IllegalArgumentException.class,
                () -> new MarketRegimeResolution(MarketRegime.QUIET,
                        List.of(HORIZON_REGIME_QUIET, HORIZON_REGIME_UNKNOWN)), "exactly one reason");

        MarketRegimeResolution quiet = new MarketRegimeResolution(MarketRegime.QUIET, List.of(HORIZON_REGIME_QUIET));
        assertThrows(UnsupportedOperationException.class, () -> quiet.reasonCodes().add(HORIZON_REGIME_UNKNOWN));
    }
}

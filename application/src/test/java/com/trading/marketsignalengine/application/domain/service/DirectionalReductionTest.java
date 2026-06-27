package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectionalReductionTest {

    @Test
    void riskOffYieldsRiskOffBiasAndZeroScore() {
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                riskOff(SignalType.NO_TRADE_CONDITION),
                bullish(SignalType.BUY_PRESSURE)));

        assertEquals(MarketBias.RISK_OFF, reduction.bias());
        assertEquals(0, reduction.score().signum());
        assertTrue(reduction.riskOff());
        assertInvariants(reduction);
    }

    @Test
    void singleBullishBaseStaysNeutral() {
        // Class A fix: one bullish leg (+0.25) is below the 0.35 directional threshold, so bias is
        // NEUTRAL and the small positive score no longer contradicts it.
        DirectionalReduction reduction = DirectionalReduction.from(List.of(bullish(SignalType.BUY_PRESSURE)));

        assertEquals(new BigDecimal("0.25"), reduction.score());
        assertEquals(MarketBias.NEUTRAL, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void alignedBullishBaseIsBullish() {
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                bullish(SignalType.BUY_PRESSURE),
                bullish(SignalType.ORDER_BOOK_BULLISH)));

        assertEquals(new BigDecimal("0.50"), reduction.score());
        assertEquals(MarketBias.BULLISH, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void alignedBearishBaseIsBearish() {
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                bearish(SignalType.SELL_PRESSURE),
                bearish(SignalType.ORDER_BOOK_BEARISH)));

        assertEquals(new BigDecimal("-0.50"), reduction.score());
        assertEquals(MarketBias.BEARISH, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void balancedConflictIsMixed() {
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                bullish(SignalType.BUY_PRESSURE),
                bearish(SignalType.ORDER_BOOK_BEARISH)));

        assertEquals(0, reduction.score().signum());
        assertTrue(reduction.conflict());
        assertEquals(MarketBias.MIXED, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void compositeSetupDoesNotInflateScore() {
        // Class C fix: LONG_SETUP_FORMING is a consequence of the base legs, so it must not re-add to
        // the score. Score stays +0.50 (the two base legs), not +0.75.
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                bullish(SignalType.BUY_PRESSURE),
                bullish(SignalType.ORDER_BOOK_BULLISH),
                bullish(SignalType.LONG_SETUP_FORMING)));

        assertEquals(new BigDecimal("0.50"), reduction.score());
        assertEquals(MarketBias.BULLISH, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void regimeIsExcludedFromScore() {
        // REGIME is microstructure noise, not a trend, so it does not move the directional score.
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                bullish(SignalType.REGIME_TRENDING_UP),
                neutral(SignalType.ORDER_BOOK_NEUTRAL)));

        assertEquals(0, reduction.score().signum());
        assertFalse(reduction.hasBullishBase());
        assertEquals(MarketBias.NEUTRAL, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void reproducesReportedScenarioConsistently() {
        // The originally reported divergence: data tradable, spread acceptable, buy pressure, neutral
        // book, trending-up regime. Old code: bias=NEUTRAL but score=+0.5. Now they agree.
        DirectionalReduction reduction = DirectionalReduction.from(List.of(
                neutral(SignalType.DATA_TRADABLE),
                neutral(SignalType.SPREAD_ACCEPTABLE),
                bullish(SignalType.BUY_PRESSURE),
                neutral(SignalType.ORDER_BOOK_NEUTRAL),
                bullish(SignalType.REGIME_TRENDING_UP)));

        assertEquals(new BigDecimal("0.25"), reduction.score());
        assertEquals(MarketBias.NEUTRAL, reduction.bias());
        assertInvariants(reduction);
    }

    @Test
    void invariantsHoldAcrossCombinations() {
        List<List<MarketSignal>> combinations = List.of(
                List.of(),
                List.of(neutral(SignalType.DATA_TRADABLE)),
                List.of(bullish(SignalType.BUY_PRESSURE)),
                List.of(bearish(SignalType.SELL_PRESSURE)),
                List.of(bullish(SignalType.BUY_PRESSURE), bullish(SignalType.ORDER_BOOK_BULLISH)),
                List.of(bearish(SignalType.SELL_PRESSURE), bearish(SignalType.ORDER_BOOK_BEARISH)),
                List.of(bullish(SignalType.BUY_PRESSURE), bearish(SignalType.ORDER_BOOK_BEARISH)),
                List.of(bearish(SignalType.SELL_PRESSURE), bullish(SignalType.ORDER_BOOK_BULLISH)),
                List.of(riskOff(SignalType.SPREAD_TOO_WIDE), bullish(SignalType.BUY_PRESSURE)),
                List.of(riskOff(SignalType.NO_TRADE_CONDITION)));

        for (List<MarketSignal> combination : combinations) {
            assertInvariants(DirectionalReduction.from(combination));
        }
    }

    private static void assertInvariants(DirectionalReduction reduction) {
        BigDecimal threshold = DirectionalReduction.DIRECTIONAL_THRESHOLD;
        BigDecimal score = reduction.score();
        switch (reduction.bias()) {
            case BULLISH -> assertTrue(score.signum() > 0, "BULLISH must have score > 0 but was " + score);
            case BEARISH -> assertTrue(score.signum() < 0, "BEARISH must have score < 0 but was " + score);
            case NEUTRAL -> assertTrue(score.abs().compareTo(threshold) < 0,
                    "NEUTRAL must have |score| < threshold but was " + score);
            case RISK_OFF -> assertEquals(0, score.signum(), "RISK_OFF must have score == 0 but was " + score);
            case MIXED -> {
                assertTrue(score.abs().compareTo(threshold) < 0,
                        "MIXED must have |score| < threshold but was " + score);
                assertTrue(reduction.conflict(), "MIXED must have a bull/bear conflict");
            }
        }
    }

    private static MarketSignal bullish(SignalType type) {
        return MarketSignal.bullish(type, SignalStrength.STRONG, new BigDecimal("0.70"), "test", null);
    }

    private static MarketSignal bearish(SignalType type) {
        return MarketSignal.bearish(type, SignalStrength.STRONG, new BigDecimal("0.70"), "test", null);
    }

    private static MarketSignal neutral(SignalType type) {
        return MarketSignal.neutral(type, SignalStrength.NONE, new BigDecimal("0.50"), "test", null);
    }

    private static MarketSignal riskOff(SignalType type) {
        return MarketSignal.riskOff(type, SignalStrength.STRONG, BigDecimal.ONE, "test", null);
    }
}

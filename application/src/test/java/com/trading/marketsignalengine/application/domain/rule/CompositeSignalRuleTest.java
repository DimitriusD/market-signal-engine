package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeSignalRuleTest {

    private final DefaultCompositeSignalRule rule = new DefaultCompositeSignalRule();

    @Test
    void alignedBullishSignalsEmitLongSetupForming() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bullish(SignalType.ORDER_BOOK_BULLISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null),
                MarketSignal.neutral(SignalType.VOLATILITY_NORMAL, SignalStrength.NONE, BigDecimal.ONE, "vol", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void alignedBearishSignalsEmitShortSetupForming() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.bearish(SignalType.ORDER_BOOK_BEARISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null),
                MarketSignal.neutral(SignalType.VOLATILITY_NORMAL, SignalStrength.NONE, BigDecimal.ONE, "vol", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void longSetupRequiresVolatilityNormal() {
        // Same aligned bullish components, but without VOLATILITY_NORMAL no LONG setup forms.
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bullish(SignalType.ORDER_BOOK_BULLISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertFalse(signals.stream().anyMatch(s -> s.type() == SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void shortSetupRequiresVolatilityNormal() {
        // Same aligned bearish components, but without VOLATILITY_NORMAL no SHORT setup forms.
        List<MarketSignal> existing = List.of(
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.bearish(SignalType.ORDER_BOOK_BEARISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertFalse(signals.stream().anyMatch(s -> s.type() == SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void withoutVolatilityNormalNoSetupIsFormed() {
        // Both directions aligned on pressure/book/spread, but high volatility (no VOLATILITY_NORMAL)
        // suppresses any setup.
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bullish(SignalType.ORDER_BOOK_BULLISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.bearish(SignalType.ORDER_BOOK_BEARISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.isEmpty());
    }

    @Test
    void longSetupCarriesSetupAttributes() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bullish(SignalType.ORDER_BOOK_BULLISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null),
                MarketSignal.neutral(SignalType.VOLATILITY_NORMAL, SignalStrength.NONE, BigDecimal.ONE, "vol", null));

        MarketSignal longSetup = rule.evaluate(dummyContext(), existing).stream()
                .filter(s -> s.type() == SignalType.LONG_SETUP_FORMING)
                .findFirst()
                .orElseThrow();

        assertTrue(longSetup.attributes().get("setupSide").equals("LONG"));
        assertTrue(longSetup.attributes().get("setupType").equals("MICROSTRUCTURE_MOMENTUM"));
    }

    @Test
    void longSetupContainsComponentsAndRequiredGates() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bullish(SignalType.ORDER_BOOK_BULLISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null),
                MarketSignal.neutral(SignalType.VOLATILITY_NORMAL, SignalStrength.NONE, BigDecimal.ONE, "vol", null));

        MarketSignal longSetup = rule.evaluate(dummyContext(), existing).stream()
                .filter(s -> s.type() == SignalType.LONG_SETUP_FORMING)
                .findFirst()
                .orElseThrow();

        assertEquals("LONG", longSetup.attributes().get("setupSide"));
        assertEquals("MICROSTRUCTURE", longSetup.attributes().get("setupFamily"));
        assertEquals("v1", longSetup.attributes().get("setupVersion"));
        assertEquals("BUY_PRESSURE,ORDER_BOOK_BULLISH,SPREAD_ACCEPTABLE,VOLATILITY_NORMAL",
                longSetup.attributes().get("components"));
        assertEquals("BUY_PRESSURE,ORDER_BOOK_BULLISH", longSetup.attributes().get("directionalComponents"));
        assertEquals("SPREAD_ACCEPTABLE,VOLATILITY_NORMAL", longSetup.attributes().get("requiredGates"));
    }

    @Test
    void shortSetupContainsComponentsAndRequiredGates() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.bearish(SignalType.ORDER_BOOK_BEARISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null),
                MarketSignal.neutral(SignalType.VOLATILITY_NORMAL, SignalStrength.NONE, BigDecimal.ONE, "vol", null));

        MarketSignal shortSetup = rule.evaluate(dummyContext(), existing).stream()
                .filter(s -> s.type() == SignalType.SHORT_SETUP_FORMING)
                .findFirst()
                .orElseThrow();

        assertEquals("SHORT", shortSetup.attributes().get("setupSide"));
        assertEquals("MICROSTRUCTURE", shortSetup.attributes().get("setupFamily"));
        assertEquals("v1", shortSetup.attributes().get("setupVersion"));
        assertEquals("SELL_PRESSURE,ORDER_BOOK_BEARISH,SPREAD_ACCEPTABLE,VOLATILITY_NORMAL",
                shortSetup.attributes().get("components"));
        assertEquals("SELL_PRESSURE,ORDER_BOOK_BEARISH", shortSetup.attributes().get("directionalComponents"));
        assertEquals("SPREAD_ACCEPTABLE,VOLATILITY_NORMAL", shortSetup.attributes().get("requiredGates"));
    }

    @Test
    void conflictingDirectionalSignalsDoNotEmitMarketMixed() {
        // MARKET_MIXED is now derived by SignalAggregator from the directional reduction, not here.
        List<MarketSignal> existing = List.of(
                MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "buy", null),
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.stream().noneMatch(s -> s.type() == SignalType.MARKET_MIXED));
    }

    private SignalEvaluationContext dummyContext() {
        return SignalRuleTestSupport.context(SignalRuleTestSupport.defaultFeatures());
    }
}

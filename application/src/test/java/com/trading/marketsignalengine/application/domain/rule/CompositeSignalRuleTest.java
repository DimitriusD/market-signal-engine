package com.trading.marketsignalengine.application.domain.rule;

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
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void alignedBearishSignalsEmitShortSetupForming() {
        List<MarketSignal> existing = List.of(
                MarketSignal.bearish(SignalType.SELL_PRESSURE, SignalStrength.STRONG, BigDecimal.ONE, "sell", null),
                MarketSignal.bearish(SignalType.ORDER_BOOK_BEARISH, SignalStrength.STRONG, BigDecimal.ONE, "book", null),
                MarketSignal.neutral(SignalType.SPREAD_ACCEPTABLE, SignalStrength.NONE, BigDecimal.ONE, "spread", null));

        List<MarketSignal> signals = rule.evaluate(dummyContext(), existing);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.SHORT_SETUP_FORMING));
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

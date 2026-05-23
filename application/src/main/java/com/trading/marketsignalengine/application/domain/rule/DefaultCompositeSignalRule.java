package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DefaultCompositeSignalRule implements CompositeSignalRule {

    private static final Set<SignalType> NO_TRADE_TYPES = EnumSet.of(
            SignalType.NO_TRADE_OUT_OF_SYNC,
            SignalType.NO_TRADE_RECOVERING_BOOK,
            SignalType.NO_TRADE_STALE_BBO,
            SignalType.NO_TRADE_STALE_BOOK,
            SignalType.NO_TRADE_STALE_TRADES,
            SignalType.NO_TRADE_INCOMPLETE_BOOK,
            SignalType.SPREAD_TOO_WIDE,
            SignalType.VOLATILITY_HIGH);

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context, List<MarketSignal> existingSignals) {
        List<MarketSignal> compositeSignals = new ArrayList<>();

        boolean hasNoTradeCondition = existingSignals.stream()
                .anyMatch(signal -> NO_TRADE_TYPES.contains(signal.type())
                        || signal.type() == SignalType.NO_TRADE_CONDITION);

        if (hasNoTradeCondition) {
            compositeSignals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_CONDITION,
                    SignalStrength.EXTREME,
                    BigDecimal.ONE,
                    "One or more no-trade conditions are active",
                    null));
            return compositeSignals;
        }

        boolean hasBuyPressure = hasType(existingSignals, SignalType.BUY_PRESSURE);
        boolean hasSellPressure = hasType(existingSignals, SignalType.SELL_PRESSURE);
        boolean hasBullishBook = hasType(existingSignals, SignalType.ORDER_BOOK_BULLISH);
        boolean hasBearishBook = hasType(existingSignals, SignalType.ORDER_BOOK_BEARISH);
        boolean hasAcceptableSpread = hasType(existingSignals, SignalType.SPREAD_ACCEPTABLE);

        if (hasBuyPressure && hasBullishBook && hasAcceptableSpread) {
            compositeSignals.add(MarketSignal.bullish(
                    SignalType.LONG_SETUP_FORMING,
                    SignalStrength.STRONG,
                    new BigDecimal("0.75"),
                    "Buy pressure, bullish order book and acceptable spread are aligned",
                    null));
        }

        if (hasSellPressure && hasBearishBook && hasAcceptableSpread) {
            compositeSignals.add(MarketSignal.bearish(
                    SignalType.SHORT_SETUP_FORMING,
                    SignalStrength.STRONG,
                    new BigDecimal("0.75"),
                    "Sell pressure, bearish order book and acceptable spread are aligned",
                    null));
        }

        boolean hasBullishDirection = existingSignals.stream()
                .anyMatch(signal -> signal.direction() == SignalDirection.BULLISH);
        boolean hasBearishDirection = existingSignals.stream()
                .anyMatch(signal -> signal.direction() == SignalDirection.BEARISH);

        if (hasBullishDirection && hasBearishDirection) {
            compositeSignals.add(MarketSignal.neutral(
                    SignalType.MARKET_MIXED,
                    SignalStrength.MODERATE,
                    new BigDecimal("0.60"),
                    "Bullish and bearish signals are both present",
                    null));
        }

        return compositeSignals;
    }

    private static boolean hasType(List<MarketSignal> signals, SignalType type) {
        return signals.stream().anyMatch(signal -> signal.type() == type);
    }
}

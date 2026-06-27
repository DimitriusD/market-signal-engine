package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Setup-formation only. The engine gates no-trade conditions before this rule ever runs, so a
 * composite signal is only produced on tradable data. No-trade detection and the
 * {@link SignalType#NO_TRADE_CONDITION} signal are owned exclusively by the engine.
 */
public class DefaultCompositeSignalRule implements CompositeSignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context, List<MarketSignal> existingSignals) {
        List<MarketSignal> compositeSignals = new ArrayList<>();

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

        // MARKET_MIXED is derived by SignalAggregator from the directional reduction, not here.
        return compositeSignals;
    }

    private static boolean hasType(List<MarketSignal> signals, SignalType type) {
        return signals.stream().anyMatch(signal -> signal.type() == type);
    }
}

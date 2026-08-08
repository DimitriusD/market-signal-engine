package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        boolean hasNormalVolatility = hasType(existingSignals, SignalType.VOLATILITY_NORMAL);

        if (hasBuyPressure && hasBullishBook && hasAcceptableSpread && hasNormalVolatility) {
            compositeSignals.add(MarketSignal.bullish(
                    SignalType.LONG_SETUP_FORMING,
                    SignalStrength.STRONG,
                    new BigDecimal("0.75"),
                    "Buy pressure, bullish order book, acceptable spread and normal volatility are aligned",
                    longSetupAttributes()));
        }

        if (hasSellPressure && hasBearishBook && hasAcceptableSpread && hasNormalVolatility) {
            compositeSignals.add(MarketSignal.bearish(
                    SignalType.SHORT_SETUP_FORMING,
                    SignalStrength.STRONG,
                    new BigDecimal("0.75"),
                    "Sell pressure, bearish order book, acceptable spread and normal volatility are aligned",
                    shortSetupAttributes()));
        }

        // MARKET_MIXED is derived by SignalAggregator from the directional reduction, not here.
        return compositeSignals;
    }

    private static Map<String, String> longSetupAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("setupSide", "LONG");
        attributes.put("setupType", "MICROSTRUCTURE_MOMENTUM");
        attributes.put("setupFamily", "MICROSTRUCTURE");
        attributes.put("setupVersion", "v1");
        attributes.put("components", "BUY_PRESSURE,ORDER_BOOK_BULLISH,SPREAD_ACCEPTABLE,VOLATILITY_NORMAL");
        attributes.put("directionalComponents", "BUY_PRESSURE,ORDER_BOOK_BULLISH");
        attributes.put("requiredGates", "SPREAD_ACCEPTABLE,VOLATILITY_NORMAL");
        return attributes;
    }

    private static Map<String, String> shortSetupAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("setupSide", "SHORT");
        attributes.put("setupType", "MICROSTRUCTURE_MOMENTUM");
        attributes.put("setupFamily", "MICROSTRUCTURE");
        attributes.put("setupVersion", "v1");
        attributes.put("components", "SELL_PRESSURE,ORDER_BOOK_BEARISH,SPREAD_ACCEPTABLE,VOLATILITY_NORMAL");
        attributes.put("directionalComponents", "SELL_PRESSURE,ORDER_BOOK_BEARISH");
        attributes.put("requiredGates", "SPREAD_ACCEPTABLE,VOLATILITY_NORMAL");
        return attributes;
    }

    private static boolean hasType(List<MarketSignal> signals, SignalType type) {
        return signals.stream().anyMatch(signal -> signal.type() == type);
    }
}

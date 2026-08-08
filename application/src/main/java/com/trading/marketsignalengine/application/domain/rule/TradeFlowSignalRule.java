package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Directional trade-flow rule keyed on the normalized {@code signedFlowImbalance5s} ∈ [-1, 1]
 * rather than raw {@code signedTradeFlow5s} (base-asset volume), so the buy/sell thresholds are
 * portable across instruments. A snapshot must clear a minimum 5s trade count and a symmetric dead
 * zone around zero before any pressure is emitted; otherwise the rule is neutral. Strength and
 * confidence scale with the magnitude of the imbalance instead of being a flat STRONG/0.70.
 */
public class TradeFlowSignalRule implements SignalRule {

    private static final BigDecimal NEUTRAL_CONFIDENCE = new BigDecimal("0.50");

    private static final BigDecimal STRONG_ABS = new BigDecimal("0.50");
    private static final BigDecimal MODERATE_ABS = new BigDecimal("0.25");

    private static final BigDecimal CONFIDENCE_BASE = new BigDecimal("0.50");
    private static final BigDecimal CONFIDENCE_SLOPE = new BigDecimal("0.50");
    private static final BigDecimal CONFIDENCE_CAP = new BigDecimal("0.95");
    private static final int CONFIDENCE_SCALE = 4;

    private static final BigDecimal MIN_IMBALANCE = new BigDecimal("-1");
    private static final BigDecimal MAX_IMBALANCE = BigDecimal.ONE;

    private static final String NEUTRAL_REASON = "neutralReason";
    private static final String REASON_TRADE_FLOW_MISSING = "TRADE_FLOW_MISSING";
    private static final String REASON_IMBALANCE_MISSING = "SIGNED_FLOW_IMBALANCE_5S_MISSING";
    private static final String REASON_LOW_TRADE_COUNT = "LOW_TRADE_COUNT_5S";
    private static final String REASON_DEAD_ZONE = "INSIDE_DEAD_ZONE";
    private static final String REASON_NEGATIVE_TRADE_COUNT_5S = "NEGATIVE_TRADE_COUNT_5S";
    private static final String REASON_IMBALANCE_OUT_OF_RANGE = "SIGNED_FLOW_IMBALANCE_5S_OUT_OF_RANGE";
    private static final String REASON_IMBALANCE_1S_OUT_OF_RANGE = "SIGNED_FLOW_IMBALANCE_1S_OUT_OF_RANGE";

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        TradeFlowFeature tradeFlow = features.tradeFlow();

        if (tradeFlow == null) {
            return neutral("trade flow features are missing", REASON_TRADE_FLOW_MISSING, null, config);
        }

        if (tradeFlow.tradeCount5s() < 0 || tradeFlow.tradeCount1s() < 0) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INVALID_TRADE_FLOW,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "trade count is negative",
                    attributes(tradeFlow, config, REASON_NEGATIVE_TRADE_COUNT_5S)));
        }

        BigDecimal imbalance = tradeFlow.signedFlowImbalance5s();
        if (imbalance == null) {
            return neutral("signedFlowImbalance5s is missing", REASON_IMBALANCE_MISSING, tradeFlow, config);
        }

        if (outsideMinusOneToOne(imbalance)) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INVALID_TRADE_FLOW,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "signedFlowImbalance5s is outside [-1, 1]",
                    attributes(tradeFlow, config, REASON_IMBALANCE_OUT_OF_RANGE)));
        }

        BigDecimal imbalance1s = tradeFlow.signedFlowImbalance1s();
        if (imbalance1s != null && outsideMinusOneToOne(imbalance1s)) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INVALID_TRADE_FLOW,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "signedFlowImbalance1s is outside [-1, 1]",
                    attributes(tradeFlow, config, REASON_IMBALANCE_1S_OUT_OF_RANGE)));
        }

        if (tradeFlow.tradeCount5s() < config.minTradeCount5sForTradeFlowSignal()) {
            return neutral("tradeCount5s is below the minimum for a trade-flow signal",
                    REASON_LOW_TRADE_COUNT, tradeFlow, config);
        }

        if (imbalance.compareTo(config.buyFlowImbalance5sThreshold()) >= 0) {
            BigDecimal abs = imbalance.abs();
            return List.of(MarketSignal.bullish(
                    SignalType.BUY_PRESSURE,
                    strengthFor(abs),
                    confidenceFor(abs),
                    "signedFlowImbalance5s is at or above the buy threshold",
                    attributes(tradeFlow, config, null)));
        }

        if (imbalance.compareTo(config.sellFlowImbalance5sThreshold()) <= 0) {
            BigDecimal abs = imbalance.abs();
            return List.of(MarketSignal.bearish(
                    SignalType.SELL_PRESSURE,
                    strengthFor(abs),
                    confidenceFor(abs),
                    "signedFlowImbalance5s is at or below the sell threshold",
                    attributes(tradeFlow, config, null)));
        }

        return neutral("signedFlowImbalance5s is inside the neutral dead zone",
                REASON_DEAD_ZONE, tradeFlow, config);
    }

    private static List<MarketSignal> neutral(String reason,
                                              String neutralReason,
                                              TradeFlowFeature tradeFlow,
                                              SignalConfiguration config) {
        return List.of(MarketSignal.neutral(
                SignalType.TRADE_FLOW_NEUTRAL,
                SignalStrength.NONE,
                NEUTRAL_CONFIDENCE,
                reason,
                attributes(tradeFlow, config, neutralReason)));
    }

    private static SignalStrength strengthFor(BigDecimal absImbalance) {
        if (absImbalance.compareTo(STRONG_ABS) >= 0) {
            return SignalStrength.STRONG;
        }
        if (absImbalance.compareTo(MODERATE_ABS) >= 0) {
            return SignalStrength.MODERATE;
        }
        return SignalStrength.WEAK;
    }

    private static BigDecimal confidenceFor(BigDecimal absImbalance) {
        BigDecimal raw = CONFIDENCE_BASE.add(absImbalance.multiply(CONFIDENCE_SLOPE));
        return raw.min(CONFIDENCE_CAP).setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP);
    }

    private static Map<String, String> attributes(TradeFlowFeature tradeFlow,
                                                  SignalConfiguration config,
                                                  String neutralReason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("buyFlowImbalance5sThreshold", config.buyFlowImbalance5sThreshold().toPlainString());
        attributes.put("sellFlowImbalance5sThreshold", config.sellFlowImbalance5sThreshold().toPlainString());
        attributes.put("minTradeCount5sForTradeFlowSignal",
                Integer.toString(config.minTradeCount5sForTradeFlowSignal()));

        if (tradeFlow != null) {
            attributes.put("tradeCount5s", Integer.toString(tradeFlow.tradeCount5s()));
            putIfPresent(attributes, "signedFlowImbalance5s", tradeFlow.signedFlowImbalance5s());
            putIfPresent(attributes, "buyAggressiveVolume5s", tradeFlow.buyAggressiveVolume5s());
            putIfPresent(attributes, "sellAggressiveVolume5s", tradeFlow.sellAggressiveVolume5s());
            putIfPresent(attributes, "totalAggressiveVolume5s", tradeFlow.totalAggressiveVolume5s());
            putIfPresent(attributes, "signedTradeFlow5s", tradeFlow.signedTradeFlow5s());
            putIfPresent(attributes, "tradeIntensity5s", tradeFlow.tradeIntensity5s());
            putIfPresent(attributes, "avgTradeSize5s", tradeFlow.avgTradeSize5s());
            putIfPresent(attributes, "vwap5s", tradeFlow.vwap5s());

            attributes.put("tradeCount1s", Integer.toString(tradeFlow.tradeCount1s()));
            putIfPresent(attributes, "signedFlowImbalance1s", tradeFlow.signedFlowImbalance1s());
            putIfPresent(attributes, "signedTradeFlow1s", tradeFlow.signedTradeFlow1s());
            putIfPresent(attributes, "buyAggressiveVolume1s", tradeFlow.buyAggressiveVolume1s());
            putIfPresent(attributes, "sellAggressiveVolume1s", tradeFlow.sellAggressiveVolume1s());
            putIfPresent(attributes, "totalAggressiveVolume1s", tradeFlow.totalAggressiveVolume1s());
            putIfPresent(attributes, "tradeIntensity1s", tradeFlow.tradeIntensity1s());
            putIfPresent(attributes, "avgTradeSize1s", tradeFlow.avgTradeSize1s());
            putIfPresent(attributes, "vwap1s", tradeFlow.vwap1s());
            putIfPresent(attributes, "lastTradePrice", tradeFlow.lastTradePrice());
        }

        if (neutralReason != null) {
            attributes.put(NEUTRAL_REASON, neutralReason);
        }
        return attributes;
    }

    private static boolean outsideMinusOneToOne(BigDecimal value) {
        return value.compareTo(MIN_IMBALANCE) < 0 || value.compareTo(MAX_IMBALANCE) > 0;
    }

    private static void putIfPresent(Map<String, String> attributes, String key, BigDecimal value) {
        if (value != null) {
            attributes.put(key, value.toPlainString());
        }
    }
}

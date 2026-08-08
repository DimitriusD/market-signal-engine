package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tradability-risk gate on 1s realized volatility, expressed in log-return basis points
 * ({@code realizedVolatilityBps1s}, MFS v2 units). Missing risk data is never read optimistically
 * as normal: a missing regime or a missing volatility value both emit a RISK_OFF no-trade signal so
 * the engine short-circuits before any directional rule runs.
 *
 * <p>The threshold is an intentionally generous, uncalibrated placeholder that blocks only extreme
 * regimes; every emitted signal carries {@code volatilityThresholdCalibration=UNCALIBRATED} until
 * the value is fitted on replayed data (path-to-paper-trading.md, decision 8.2).
 */
public class VolatilitySignalRule implements SignalRule {

    private static final BigDecimal NORMAL_CONFIDENCE = new BigDecimal("0.5");

    private static final String RISK_REASON = "riskReason";
    private static final String MAX_REALIZED_VOLATILITY_BPS_1S = "maxRealizedVolatilityBps1s";
    private static final String REALIZED_VOLATILITY_BPS_1S = "realizedVolatilityBps1s";
    private static final String THRESHOLD_CALIBRATION = "volatilityThresholdCalibration";
    private static final String THRESHOLD_CALIBRATION_STATE = "UNCALIBRATED";

    private static final String REASON_REGIME_MISSING = "VOLATILITY_REGIME_MISSING";
    private static final String REASON_VOLATILITY_MISSING = "REALIZED_VOLATILITY_BPS_1S_MISSING";
    private static final String REASON_ABOVE_THRESHOLD = "REALIZED_VOLATILITY_BPS_1S_ABOVE_THRESHOLD";
    private static final String REASON_NEGATIVE_VOLATILITY = "REALIZED_VOLATILITY_BPS_1S_NEGATIVE";

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        RegimeFeature regime = features.regime();

        if (regime == null) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_VOLATILITY_MISSING,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Volatility regime features are missing",
                    missingAttributes(config, REASON_REGIME_MISSING)));
        }

        if (regime.realizedVolatilityBps1s() == null) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_VOLATILITY_MISSING,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "realizedVolatilityBps1s is missing",
                    missingAttributes(config, REASON_VOLATILITY_MISSING)));
        }

        if (regime.realizedVolatilityBps1s().signum() < 0) {
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INVALID_VOLATILITY,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "realizedVolatilityBps1s is negative",
                    volatilityAttributes(regime, config, REASON_NEGATIVE_VOLATILITY)));
        }

        if (regime.realizedVolatilityBps1s().compareTo(config.maxRealizedVolatilityBps1s()) <= 0) {
            return List.of(MarketSignal.neutral(
                    SignalType.VOLATILITY_NORMAL,
                    SignalStrength.NONE,
                    NORMAL_CONFIDENCE,
                    "realizedVolatilityBps1s is within configured threshold",
                    volatilityAttributes(regime, config, null)));
        }

        return List.of(MarketSignal.riskOff(
                SignalType.VOLATILITY_HIGH,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                "realizedVolatilityBps1s is above configured threshold",
                volatilityAttributes(regime, config, REASON_ABOVE_THRESHOLD)));
    }

    private static Map<String, String> missingAttributes(SignalConfiguration config, String riskReason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(RISK_REASON, riskReason);
        attributes.put(MAX_REALIZED_VOLATILITY_BPS_1S, config.maxRealizedVolatilityBps1s().toPlainString());
        attributes.put(THRESHOLD_CALIBRATION, THRESHOLD_CALIBRATION_STATE);
        return attributes;
    }

    private static Map<String, String> volatilityAttributes(RegimeFeature regime,
                                                            SignalConfiguration config,
                                                            String riskReason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(REALIZED_VOLATILITY_BPS_1S, regime.realizedVolatilityBps1s().toPlainString());
        attributes.put(MAX_REALIZED_VOLATILITY_BPS_1S, config.maxRealizedVolatilityBps1s().toPlainString());
        attributes.put(THRESHOLD_CALIBRATION, THRESHOLD_CALIBRATION_STATE);
        SignalAttributes.putIfPresent(attributes, "lastTradeDistanceToMidBps", regime.lastTradeDistanceToMidBps());
        if (riskReason != null) {
            attributes.put(RISK_REASON, riskReason);
        }
        return attributes;
    }
}

package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;

public class VolatilitySignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        RegimeFeature regime = features.regime();

        if (regime == null || regime.shortTermVolatility1s() == null) {
            return List.of(MarketSignal.neutral(
                    SignalType.VOLATILITY_NORMAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "shortTermVolatility1s is missing; defaulting to normal for skeleton",
                    null));
        }

        if (regime.shortTermVolatility1s().compareTo(config.maxShortTermVolatility1s()) <= 0) {
            return List.of(MarketSignal.neutral(
                    SignalType.VOLATILITY_NORMAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "shortTermVolatility1s is within configured threshold",
                    null));
        }

        return List.of(MarketSignal.riskOff(
                SignalType.VOLATILITY_HIGH,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                "shortTermVolatility1s is above configured threshold",
                null));
    }
}

package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.RegimeFeatureView;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;

public class RegimeSignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        RegimeFeatureView regime = features.regime();

        if (regime == null || regime.lastTradeDistanceToMidBps() == null
                || regime.lastTradeDistanceToMidBps().compareTo(BigDecimal.ZERO) == 0) {
            return List.of(MarketSignal.neutral(
                    SignalType.REGIME_RANGING,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "Market regime is ranging",
                    null));
        }

        if (regime.lastTradeDistanceToMidBps().compareTo(BigDecimal.ZERO) > 0) {
            return List.of(MarketSignal.bullish(
                    SignalType.REGIME_TRENDING_UP,
                    SignalStrength.MODERATE,
                    new BigDecimal("0.60"),
                    "Market regime is trending up",
                    null));
        }

        return List.of(MarketSignal.bearish(
                SignalType.REGIME_TRENDING_DOWN,
                SignalStrength.MODERATE,
                new BigDecimal("0.60"),
                "Market regime is trending down",
                null));
    }
}

package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.BboFeatureView;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;

public class SpreadSignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        BboFeatureView bbo = features.bbo();

        if (bbo == null || bbo.spreadBps() == null) {
            return List.of(MarketSignal.riskOff(
                    SignalType.SPREAD_TOO_WIDE,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Spread is missing",
                    null));
        }

        if (bbo.spreadBps().compareTo(config.maxSpreadBps()) <= 0) {
            return List.of(MarketSignal.neutral(
                    SignalType.SPREAD_ACCEPTABLE,
                    SignalStrength.NONE,
                    BigDecimal.ONE,
                    "Spread is within configured threshold",
                    null));
        }

        return List.of(MarketSignal.riskOff(
                SignalType.SPREAD_TOO_WIDE,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                "Spread is above configured threshold",
                null));
    }
}

package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.TradeFlowFeatureView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TradeFlowSignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        TradeFlowFeatureView tradeFlow = features.tradeFlow();

        if (tradeFlow == null || tradeFlow.signedTradeFlow5s() == null) {
            return List.of(MarketSignal.neutral(
                    SignalType.TRADE_FLOW_NEUTRAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "signedTradeFlow5s is missing",
                    null));
        }

        BigDecimal signedTradeFlow5s = tradeFlow.signedTradeFlow5s();

        if (signedTradeFlow5s.compareTo(config.buySignedTradeFlow5sThreshold()) > 0) {
            return List.of(MarketSignal.bullish(
                    SignalType.BUY_PRESSURE,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "signedTradeFlow5s is above buy threshold",
                    Map.of(
                            "signedTradeFlow5s", signedTradeFlow5s.toPlainString(),
                            "threshold", config.buySignedTradeFlow5sThreshold().toPlainString())));
        }

        if (signedTradeFlow5s.compareTo(config.sellSignedTradeFlow5sThreshold()) < 0) {
            return List.of(MarketSignal.bearish(
                    SignalType.SELL_PRESSURE,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "signedTradeFlow5s is below sell threshold",
                    Map.of(
                            "signedTradeFlow5s", signedTradeFlow5s.toPlainString(),
                            "threshold", config.sellSignedTradeFlow5sThreshold().toPlainString())));
        }

        return List.of(MarketSignal.neutral(
                SignalType.TRADE_FLOW_NEUTRAL,
                SignalStrength.NONE,
                new BigDecimal("0.5"),
                "signedTradeFlow5s is within neutral range",
                null));
    }
}

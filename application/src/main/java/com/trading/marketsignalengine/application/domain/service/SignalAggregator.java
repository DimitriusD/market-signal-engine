package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class SignalAggregator {

    public MarketSignalSnapshot aggregate(SignalEvaluationContext context, List<MarketSignal> signals) {
        MarketFeaturesSnapshot features = context.features();
        List<MarketSignal> allSignals = List.copyOf(signals);

        MarketBias marketBias = resolveMarketBias(allSignals);
        RiskLevel riskLevel = resolveRiskLevel(allSignals);
        BigDecimal marketBiasScore = computeMarketBiasScore(allSignals);

        return new MarketSignalSnapshot(
                UUID.randomUUID().toString(),
                features.snapshotId(),
                features.exchange(),
                features.marketType(),
                features.symbol(),
                features.instrumentId(),
                features.eventTime(),
                context.evaluatedAt(),
                features.featureSetVersion(),
                context.configuration().signalSetVersion(),
                marketBias,
                marketBiasScore,
                riskLevel,
                allSignals);
    }

    private MarketBias resolveMarketBias(List<MarketSignal> signals) {
        if (hasType(signals, SignalType.NO_TRADE_CONDITION)) {
            return MarketBias.RISK_OFF;
        }
        if (hasType(signals, SignalType.MARKET_MIXED)) {
            return MarketBias.MIXED;
        }
        if (hasType(signals, SignalType.LONG_SETUP_FORMING)) {
            return MarketBias.BULLISH;
        }
        if (hasType(signals, SignalType.SHORT_SETUP_FORMING)) {
            return MarketBias.BEARISH;
        }
        return MarketBias.NEUTRAL;
    }

    private RiskLevel resolveRiskLevel(List<MarketSignal> signals) {
        if (hasType(signals, SignalType.NO_TRADE_CONDITION)) {
            return RiskLevel.NO_TRADE;
        }
        if (hasType(signals, SignalType.MARKET_MIXED)) {
            return RiskLevel.ELEVATED;
        }
        return RiskLevel.NORMAL;
    }

    private BigDecimal computeMarketBiasScore(List<MarketSignal> signals) {
        boolean hasRiskOff = signals.stream()
                .anyMatch(signal -> signal.direction() == SignalDirection.RISK_OFF);
        if (hasRiskOff) {
            return BigDecimal.ZERO;
        }

        BigDecimal score = BigDecimal.ZERO;
        for (MarketSignal signal : signals) {
            if (signal.direction() == SignalDirection.BULLISH) {
                score = score.add(new BigDecimal("0.25"));
            } else if (signal.direction() == SignalDirection.BEARISH) {
                score = score.subtract(new BigDecimal("0.25"));
            }
        }

        return clamp(score, new BigDecimal("-1"), BigDecimal.ONE);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private static boolean hasType(List<MarketSignal> signals, SignalType type) {
        return signals.stream().anyMatch(signal -> signal.type() == type);
    }
}

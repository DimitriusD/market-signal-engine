package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SignalAggregator {

    public MarketSignalSnapshot aggregate(SignalEvaluationContext context, List<MarketSignal> signals) {
        MarketFeaturesSnapshot features = context.features();
        List<MarketSignal> inputSignals = List.copyOf(signals);

        DirectionalReduction reduction = DirectionalReduction.from(inputSignals);
        RiskLevel riskLevel = resolveRiskLevel(reduction);
        List<MarketSignal> allSignals = withDiagnostics(inputSignals, reduction);

        return new MarketSignalSnapshot(
                UUID.randomUUID().toString(),
                features.snapshotId(),
                features.exchange(),
                features.marketType(),
                features.base(),
                features.quote(),
                features.symbol(),
                features.instrumentId(),
                features.eventTime(),
                context.evaluatedAt(),
                features.featureSetVersion(),
                context.configuration().signalSetVersion(),
                reduction.bias(),
                reduction.score(),
                riskLevel,
                allSignals);
    }

    private RiskLevel resolveRiskLevel(DirectionalReduction reduction) {
        if (reduction.riskOff()) {
            return RiskLevel.NO_TRADE;
        }
        if (reduction.conflict()) {
            return RiskLevel.ELEVATED;
        }
        return RiskLevel.NORMAL;
    }

    /**
     * Appends MARKET_MIXED as a diagnostic when, and only when, the reduction resolved to MIXED.
     * Emitting it here (rather than in the composite rule) keeps it derived from the same reduction
     * that produced the bias, so the signal and {@code marketBias} can never disagree.
     */
    private List<MarketSignal> withDiagnostics(List<MarketSignal> signals, DirectionalReduction reduction) {
        if (reduction.bias() != MarketBias.MIXED) {
            return signals;
        }
        List<MarketSignal> withDiagnostics = new ArrayList<>(signals);
        withDiagnostics.add(MarketSignal.neutral(
                SignalType.MARKET_MIXED,
                SignalStrength.MODERATE,
                new BigDecimal("0.60"),
                "Bullish and bearish base signals conflict without a dominant direction",
                null));
        return List.copyOf(withDiagnostics);
    }
}

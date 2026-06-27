package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.rule.CompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Three-phase evaluation with hard short-circuits:
 * <ol>
 *   <li>data-quality gate — if the snapshot is not tradable, nothing downstream is computed;</li>
 *   <li>tradability-risk gate (spread / volatility) — only meaningful once quality has passed, so a
 *       stale BBO can never produce a spread verdict;</li>
 *   <li>directional rules + composite setups — only ever run on tradable data.</li>
 * </ol>
 * A no-trade condition at any gate ends evaluation immediately: directional signals are never
 * produced, so they can leak neither into the published signal list nor into the directional score.
 * The RISK_OFF short-circuit in {@link DirectionalReduction} is then a backstop, not the sole line
 * of defence. The engine is the single emitter of {@link SignalType#NO_TRADE_CONDITION}.
 */
public class DefaultMarketSignalEngine implements MarketSignalEngine {

    private final List<SignalRule> qualityGateRules;
    private final List<SignalRule> tradabilityGateRules;
    private final List<SignalRule> directionalRules;
    private final CompositeSignalRule compositeSignalRule;
    private final SignalAggregator signalAggregator;
    private final SignalConfiguration signalConfiguration;

    public DefaultMarketSignalEngine(List<SignalRule> qualityGateRules,
                                     List<SignalRule> tradabilityGateRules,
                                     List<SignalRule> directionalRules,
                                     CompositeSignalRule compositeSignalRule,
                                     SignalAggregator signalAggregator,
                                     SignalConfiguration signalConfiguration) {
        this.qualityGateRules = List.copyOf(qualityGateRules);
        this.tradabilityGateRules = List.copyOf(tradabilityGateRules);
        this.directionalRules = List.copyOf(directionalRules);
        this.compositeSignalRule = compositeSignalRule;
        this.signalAggregator = signalAggregator;
        this.signalConfiguration = signalConfiguration;
    }

    @Override
    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {

        SignalEvaluationContext context = new SignalEvaluationContext(features, signalConfiguration, Instant.now());

        List<MarketSignal> signals = new ArrayList<>();

        // Phase 1: data-quality gate.
        evaluateInto(qualityGateRules, context, signals);
        if (hasNoTrade(signals)) {
            return aggregateNoTrade(context, signals);
        }

        // Phase 2: tradability-risk gate (spread / volatility).
        evaluateInto(tradabilityGateRules, context, signals);
        if (hasNoTrade(signals)) {
            return aggregateNoTrade(context, signals);
        }

        // Phase 3: directional rules + composite setups, on tradable data only.
        evaluateInto(directionalRules, context, signals);
        signals.addAll(compositeSignalRule.evaluate(context, signals));

        return signalAggregator.aggregate(context, signals);
    }

    private static void evaluateInto(List<SignalRule> rules,
                                     SignalEvaluationContext context,
                                     List<MarketSignal> sink) {
        for (SignalRule rule : rules) {
            sink.addAll(rule.evaluate(context));
        }
    }

    private MarketSignalSnapshot aggregateNoTrade(SignalEvaluationContext context,
                                                  List<MarketSignal> gateSignals) {
        List<MarketSignal> signals = new ArrayList<>(gateSignals);
        signals.add(noTradeCondition());
        return signalAggregator.aggregate(context, signals);
    }

    /**
     * A snapshot is no-trade exactly when a gate emitted a RISK_OFF signal. This is the one
     * definition of "no-trade" in the engine and matches {@link DirectionalReduction}.
     */
    private static boolean hasNoTrade(List<MarketSignal> signals) {
        return signals.stream().anyMatch(signal -> signal.direction() == SignalDirection.RISK_OFF);
    }

    private static MarketSignal noTradeCondition() {
        return MarketSignal.riskOff(
                SignalType.NO_TRADE_CONDITION,
                SignalStrength.EXTREME,
                BigDecimal.ONE,
                "One or more no-trade conditions are active",
                null);
    }
}

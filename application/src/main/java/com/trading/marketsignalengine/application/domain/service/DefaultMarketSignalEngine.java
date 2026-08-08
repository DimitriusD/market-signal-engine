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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Three-phase evaluation with hard short-circuits:
 * <ol>
 *   <li>data-quality gate — if the snapshot is not tradable, nothing downstream is computed;</li>
 *   <li>tradability-risk gate (spread / volatility) — only meaningful once quality has passed, so a
 *       stale BBO can never produce a spread verdict;</li>
 *   <li>directional rules + composite setups — only ever run on tradable data.</li>
 * </ol>
 * A no-trade condition at any gate ends evaluation immediately. Phases 1–2 cannot have produced
 * directional evidence yet; in phase 3 an earlier directional rule may already have emitted
 * bullish/bearish signals before a later rule detected an invalid feature value, so
 * {@link #aggregateNoTrade} additionally strips directional evidence from the published list. The
 * invariant therefore holds unconditionally: a no-trade snapshot never contains bullish/bearish
 * signals, neither in the published signal list nor in the directional score. The RISK_OFF
 * short-circuit in {@link DirectionalReduction} is then a backstop, not the sole line of defence.
 * The engine is the single emitter of {@link SignalType#NO_TRADE_CONDITION}.
 */
public class DefaultMarketSignalEngine implements MarketSignalEngine {

    private final List<SignalRule> qualityGateRules;
    private final List<SignalRule> tradabilityGateRules;
    private final List<SignalRule> directionalRules;
    private final CompositeSignalRule compositeSignalRule;
    private final SignalAggregator signalAggregator;
    private final SignalConfiguration signalConfiguration;
    private final Clock clock;

    public DefaultMarketSignalEngine(List<SignalRule> qualityGateRules,
                                     List<SignalRule> tradabilityGateRules,
                                     List<SignalRule> directionalRules,
                                     CompositeSignalRule compositeSignalRule,
                                     SignalAggregator signalAggregator,
                                     SignalConfiguration signalConfiguration,
                                     Clock clock) {
        this.qualityGateRules = List.copyOf(qualityGateRules);
        this.tradabilityGateRules = List.copyOf(tradabilityGateRules);
        this.directionalRules = List.copyOf(directionalRules);
        this.compositeSignalRule = compositeSignalRule;
        this.signalAggregator = signalAggregator;
        this.signalConfiguration = signalConfiguration;
        this.clock = clock;
    }

    @Override
    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {

        SignalEvaluationContext context = new SignalEvaluationContext(features, signalConfiguration, Instant.now(clock));

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

        // Phase 3: directional rules + composite setups, on tradable data only. A directional rule can
        // still emit RISK_OFF on a semantically-invalid feature value (out-of-range imbalance, negative
        // trade count, invalid order book); in that case no composite setup must be formed on garbage.
        evaluateInto(directionalRules, context, signals);
        if (hasNoTrade(signals)) {
            return aggregateNoTrade(context, signals);
        }
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

    /**
     * Assembles the published no-trade snapshot. Directional (bullish/bearish) evidence emitted by
     * an earlier phase-3 rule before a later rule went RISK_OFF is dropped: publishing actionable
     * directional signals alongside NO_TRADE_CONDITION would hand downstream contradictory evidence.
     */
    private MarketSignalSnapshot aggregateNoTrade(SignalEvaluationContext context,
                                                  List<MarketSignal> gateSignals) {
        List<MarketSignal> signals = gateSignals.stream()
                .filter(signal -> signal.direction() != SignalDirection.BULLISH
                        && signal.direction() != SignalDirection.BEARISH)
                .collect(Collectors.toCollection(ArrayList::new));
        signals.add(noTradeCondition(signals));
        return signalAggregator.aggregate(context, signals);
    }

    /**
     * A snapshot is no-trade exactly when a gate emitted a RISK_OFF signal. This is the one
     * definition of "no-trade" in the engine and matches {@link DirectionalReduction}.
     */
    private static boolean hasNoTrade(List<MarketSignal> signals) {
        return signals.stream().anyMatch(signal -> signal.direction() == SignalDirection.RISK_OFF);
    }

    private static MarketSignal noTradeCondition(List<MarketSignal> gateSignals) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("condition", "NO_TRADE");
        attributes.put("reason", "ONE_OR_MORE_RISK_OFF_SIGNALS_ACTIVE");
        attributes.put("emittedBy", "DefaultMarketSignalEngine");

        String riskOffSignals = gateSignals.stream()
                .filter(signal -> signal.direction() == SignalDirection.RISK_OFF)
                .map(signal -> signal.type().name())
                .distinct()
                .collect(Collectors.joining(","));

        attributes.put("riskOffSignals", riskOffSignals);

        return MarketSignal.riskOff(
                SignalType.NO_TRADE_CONDITION,
                SignalStrength.EXTREME,
                BigDecimal.ONE,
                "One or more no-trade conditions are active",
                attributes);
    }
}

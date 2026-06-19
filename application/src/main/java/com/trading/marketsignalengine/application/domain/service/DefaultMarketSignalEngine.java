package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.rule.CompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DefaultMarketSignalEngine implements MarketSignalEngine {

    private final List<SignalRule> baseRules;
    private final CompositeSignalRule compositeSignalRule;
    private final SignalAggregator signalAggregator;
    private final SignalConfiguration signalConfiguration;

    public DefaultMarketSignalEngine(List<SignalRule> baseRules,
                                     CompositeSignalRule compositeSignalRule,
                                     SignalAggregator signalAggregator,
                                     SignalConfiguration signalConfiguration) {
        this.baseRules = List.copyOf(baseRules);
        this.compositeSignalRule = compositeSignalRule;
        this.signalAggregator = signalAggregator;
        this.signalConfiguration = signalConfiguration;
    }

    @Override
    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {

        SignalEvaluationContext context = new SignalEvaluationContext(features, signalConfiguration, Instant.now());

        List<MarketSignal> baseSignals = new ArrayList<>();
        for (SignalRule rule : baseRules) {
            baseSignals.addAll(rule.evaluate(context));
        }

        List<MarketSignal> compositeSignals = compositeSignalRule.evaluate(context, baseSignals);

        List<MarketSignal> allSignals = new ArrayList<>(baseSignals);
        allSignals.addAll(compositeSignals);

        return signalAggregator.aggregate(context, allSignals);
    }
}

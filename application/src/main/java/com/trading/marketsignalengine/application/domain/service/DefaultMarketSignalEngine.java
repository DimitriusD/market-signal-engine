package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.rule.CompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultMarketSignalEngine implements MarketSignalEngine {

    private final List<SignalRule> baseRules;
    private final CompositeSignalRule compositeSignalRule;
    private final SignalAggregator signalAggregator;

    public DefaultMarketSignalEngine(
            List<SignalRule> baseRules,
            CompositeSignalRule compositeSignalRule,
            SignalAggregator signalAggregator) {
        this.baseRules = List.copyOf(baseRules);
        this.compositeSignalRule = compositeSignalRule;
        this.signalAggregator = signalAggregator;
    }

    @Override
    public MarketSignalSnapshot evaluate(SignalEvaluationContext context) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(context.features(), "features");

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

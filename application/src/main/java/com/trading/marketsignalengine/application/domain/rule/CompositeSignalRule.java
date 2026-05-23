package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import java.util.List;

public interface CompositeSignalRule {

    List<MarketSignal> evaluate(SignalEvaluationContext context, List<MarketSignal> existingSignals);
}
